// Command kinzo-api runs the Kinzo HTTP API.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/tomhartill/kinzo/api/internal/auth"
	"github.com/tomhartill/kinzo/api/internal/config"
	"github.com/tomhartill/kinzo/api/internal/database"
	"github.com/tomhartill/kinzo/api/internal/scan"
	"github.com/tomhartill/kinzo/api/internal/server"
	"github.com/tomhartill/kinzo/api/internal/store"
)

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	if err := run(logger); err != nil {
		logger.Error("kinzo-api stopped", "err", err)
		os.Exit(1)
	}
}

func run(logger *slog.Logger) error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	pool, err := database.Open(ctx, cfg.DatabaseURL)
	if err != nil {
		return err
	}
	defer pool.Close()

	if err := database.Migrate(ctx, pool); err != nil {
		return err
	}

	var providers []auth.Provider
	var googleIDTokens server.IDTokenVerifier
	if cfg.GoogleClientID != "" {
		google := &auth.Google{
			ClientID:     cfg.GoogleClientID,
			ClientSecret: cfg.GoogleClientSecret,
			Audiences:    cfg.GoogleExtraAudiences,
		}
		providers = append(providers, google)
		googleIDTokens = google
	}
	if cfg.FacebookAppID != "" {
		providers = append(providers, &auth.Facebook{
			AppID:        cfg.FacebookAppID,
			AppSecret:    cfg.FacebookAppSecret,
			GraphVersion: cfg.FacebookGraphVersion,
		})
	}
	logger.Info("sign-in providers", "count", len(providers), "public_url", cfg.PublicURL)

	options := []server.Option{server.WithAccounts(store.New(pool), cfg.PublicURL, providers, googleIDTokens)}
	if cfg.AnthropicAPIKey != "" {
		machines, err := scan.LoadCatalogue()
		if err != nil {
			return err
		}
		recogniser := scan.NewRecogniser(cfg.AnthropicAPIKey, cfg.ScanModel, machines)
		options = append(options, server.WithMachineScan(recogniser, cfg.ScanDailyLimit))
		logger.Info("machine scan on", "machines", len(machines.Machines), "daily_limit", cfg.ScanDailyLimit)
	}

	if cfg.OTPProvider != "" {
		var sender auth.CodeSender = &auth.LogSender{Logger: logger}
		if cfg.OTPProvider == "twilio" {
			sender = &auth.Twilio{
				AccountSID: cfg.TwilioAccountSID,
				AuthToken:  cfg.TwilioAuthToken,
				ServiceSID: cfg.TwilioVerifyServiceSID,
				HTTP:       &http.Client{Timeout: 15 * time.Second},
			}
		}
		rules := auth.PhoneRules{Countries: cfg.PhoneCountries, TestNumbers: cfg.OTPTestNumbers}
		options = append(options, server.WithPhoneCodes(
			auth.WithTestNumbers(sender, cfg.OTPTestNumbers), rules, cfg.OTPChannels, cfg.BehindProxy))
		logger.Info("phone codes on", "provider", cfg.OTPProvider, "channels", cfg.OTPChannels,
			"countries", cfg.PhoneCountries, "test_numbers", len(cfg.OTPTestNumbers))
	}

	httpServer := &http.Server{
		Addr:              cfg.Addr,
		Handler:           server.New(pool, logger, options...).Handler(),
		ReadHeaderTimeout: 10 * time.Second,
	}

	errs := make(chan error, 1)
	go func() {
		logger.Info("listening", "addr", cfg.Addr)
		errs <- httpServer.ListenAndServe()
	}()

	select {
	case err := <-errs:
		if !errors.Is(err, http.ErrServerClosed) {
			return err
		}
	case <-ctx.Done():
		logger.Info("shutting down")
	}

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	return httpServer.Shutdown(shutdownCtx)
}
