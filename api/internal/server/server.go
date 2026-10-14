// Package server holds the HTTP routes. The API contract lives in openapi/kinzo.yaml at the
// repository root; keep the two in step.
package server

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"time"
)

// Pinger is the part of the database pool the health check needs.
type Pinger interface {
	Ping(ctx context.Context) error
}

type Server struct {
	db     Pinger
	logger *slog.Logger

	// Accounts and friends; nil when the server runs without them, as in the health tests.
	accounts *accounts
}

// Option configures optional parts of the server.
type Option func(*Server)

func New(db Pinger, logger *slog.Logger, opts ...Option) *Server {
	s := &Server{db: db, logger: logger}
	for _, opt := range opts {
		opt(s)
	}
	return s
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", s.health)
	if s.accounts != nil {
		s.accountRoutes(mux)
	}
	return s.logRequests(mux)
}

type healthResponse struct {
	Status   string `json:"status"`
	Database string `json:"database"`
}

func (s *Server) health(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()

	if err := s.db.Ping(ctx); err != nil {
		s.logger.Warn("health check: database unreachable", "err", err)
		writeJSON(w, http.StatusServiceUnavailable, healthResponse{Status: "degraded", Database: "unreachable"})
		return
	}
	writeJSON(w, http.StatusOK, healthResponse{Status: "ok", Database: "ok"})
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

// apiError is the body of every error response: a stable code for the app to switch on and a
// sentence a person could read.
type apiError struct {
	Error struct {
		Code    string `json:"code"`
		Message string `json:"message"`
	} `json:"error"`
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	var body apiError
	body.Error.Code = code
	body.Error.Message = message
	writeJSON(w, status, body)
}

func (s *Server) logRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(rec, r)
		s.logger.Info("request", "method", r.Method, "path", r.URL.Path, "status", rec.status, "duration", time.Since(start))
	})
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) WriteHeader(status int) {
	r.status = status
	r.ResponseWriter.WriteHeader(status)
}
