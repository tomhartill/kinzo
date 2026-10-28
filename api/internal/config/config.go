// Package config reads the API's settings from the environment.
package config

import (
	"errors"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	// Addr is the address the HTTP server listens on, e.g. ":8080".
	Addr string
	// DatabaseURL is a Postgres connection string.
	DatabaseURL string
	// PublicURL is where phones reach the API, e.g. "https://api.example.com". Sign-in
	// providers send people back to it, so it must match what's registered with them.
	PublicURL string

	// Google sign-in: the "Web application" OAuth client. Leave empty to turn Google off.
	GoogleClientID     string
	GoogleClientSecret string
	// GoogleExtraAudiences are other client IDs (comma separated) whose ID tokens to accept.
	GoogleExtraAudiences []string

	// Facebook sign-in. Leave empty to turn Facebook off.
	FacebookAppID        string
	FacebookAppSecret    string
	FacebookGraphVersion string

	// The machine scan: a Claude API key, the vision model, and scans per user per day. Leave
	// the key empty to turn scanning off.
	AnthropicAPIKey string
	ScanModel       string
	ScanDailyLimit  int

	// Email and phone code sign-in. OTPProvider is "twilio", "log" (codes go to the API log;
	// for development) or empty for off. It defaults to twilio when TWILIO_ACCOUNT_SID is set.
	OTPProvider            string
	TwilioAccountSID       string
	TwilioAuthToken        string
	TwilioVerifyServiceSID string
	// OTPChannels are how codes can go: "sms", "whatsapp".
	OTPChannels []string
	// PhoneCountries are the ISO region codes codes can be sent to, such as "GB".
	PhoneCountries []string
	// OTPTestNumbers map E.164 numbers to a fixed code; they never get a real message.
	OTPTestNumbers map[string]string
	// BehindProxy means a reverse proxy sits in front, so the caller's address is read from
	// X-Forwarded-For (for the per-address code limit).
	BehindProxy bool
}

func Load() (Config, error) {
	cfg := Config{
		Addr:                 getenv("KINZO_ADDR", ":8080"),
		DatabaseURL:          os.Getenv("DATABASE_URL"),
		PublicURL:            strings.TrimSuffix(getenv("KINZO_PUBLIC_URL", "http://localhost:8080"), "/"),
		GoogleClientID:       os.Getenv("GOOGLE_CLIENT_ID"),
		GoogleClientSecret:   os.Getenv("GOOGLE_CLIENT_SECRET"),
		GoogleExtraAudiences: splitList(os.Getenv("GOOGLE_EXTRA_AUDIENCES")),
		FacebookAppID:        os.Getenv("FACEBOOK_APP_ID"),
		FacebookAppSecret:    os.Getenv("FACEBOOK_APP_SECRET"),
		FacebookGraphVersion: os.Getenv("FACEBOOK_GRAPH_VERSION"),
		AnthropicAPIKey:      os.Getenv("ANTHROPIC_API_KEY"),
		ScanModel:            os.Getenv("KINZO_SCAN_MODEL"),
	}
	limit, err := strconv.Atoi(getenv("KINZO_SCAN_DAILY_LIMIT", "30"))
	if err != nil || limit < 0 {
		return Config{}, errors.New("KINZO_SCAN_DAILY_LIMIT must be a whole number")
	}
	cfg.ScanDailyLimit = limit

	cfg.TwilioAccountSID = os.Getenv("TWILIO_ACCOUNT_SID")
	cfg.TwilioAuthToken = os.Getenv("TWILIO_AUTH_TOKEN")
	cfg.TwilioVerifyServiceSID = os.Getenv("TWILIO_VERIFY_SERVICE_SID")
	cfg.OTPProvider = os.Getenv("KINZO_OTP_PROVIDER")
	if cfg.OTPProvider == "" && cfg.TwilioAccountSID != "" {
		cfg.OTPProvider = "twilio"
	}
	switch cfg.OTPProvider {
	case "", "log":
	case "twilio":
		if cfg.TwilioAccountSID == "" || cfg.TwilioAuthToken == "" || cfg.TwilioVerifyServiceSID == "" {
			return Config{}, errors.New("Twilio needs TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_VERIFY_SERVICE_SID")
		}
	default:
		return Config{}, errors.New("KINZO_OTP_PROVIDER must be twilio or log")
	}
	cfg.OTPChannels = splitList(getenv("KINZO_OTP_CHANNELS", "sms"))
	for _, c := range cfg.OTPChannels {
		if c != "sms" && c != "whatsapp" {
			return Config{}, errors.New("KINZO_OTP_CHANNELS can only list sms and whatsapp")
		}
	}
	cfg.PhoneCountries = splitList(strings.ToUpper(getenv("KINZO_PHONE_COUNTRIES", "GB")))
	cfg.OTPTestNumbers = map[string]string{}
	for _, pair := range splitList(os.Getenv("KINZO_OTP_TEST_NUMBERS")) {
		number, code, ok := strings.Cut(pair, ":")
		if !ok || !strings.HasPrefix(number, "+") || len(code) != 6 {
			return Config{}, errors.New("KINZO_OTP_TEST_NUMBERS entries look like +447700900123:123456")
		}
		cfg.OTPTestNumbers[number] = code
	}
	cfg.BehindProxy = os.Getenv("KINZO_BEHIND_PROXY") == "true"
	if cfg.DatabaseURL == "" {
		return Config{}, errors.New("DATABASE_URL is not set")
	}
	return cfg, nil
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func splitList(s string) []string {
	var out []string
	for _, part := range strings.Split(s, ",") {
		if part = strings.TrimSpace(part); part != "" {
			out = append(out, part)
		}
	}
	return out
}
