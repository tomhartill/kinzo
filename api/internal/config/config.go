// Package config reads the API's settings from the environment.
package config

import (
	"errors"
	"os"
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
	}
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
