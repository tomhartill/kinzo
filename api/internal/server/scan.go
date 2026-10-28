package server

import (
	"context"
	"errors"
	"io"
	"net/http"
	"time"

	"github.com/tomhartill/kinzo/api/internal/scan"
)

// MachineRecogniser says which machine a photo shows. *scan.Recogniser is the real one.
type MachineRecogniser interface {
	Recognise(ctx context.Context, image []byte, mediaType string) (scan.Result, error)
}

const (
	// maxScanPhoto is far more than the app sends (it shrinks photos to about 1280 pixels).
	maxScanPhoto = 5 << 20
	scanTimeout  = 45 * time.Second
)

type machineScan struct {
	recogniser MachineRecogniser
	dailyLimit int
}

// WithMachineScan turns on the machine scan. Each scan is a paid vision-model call, so a
// signed-in user gets dailyLimit a day. It needs WithAccounts too.
func WithMachineScan(recogniser MachineRecogniser, dailyLimit int) Option {
	return func(s *Server) {
		s.scan = &machineScan{recogniser: recogniser, dailyLimit: dailyLimit}
	}
}

// scanMachine takes a photo as the raw request body (image/jpeg or image/png) and answers with
// the machines it could show and the exercise variation it most likely is.
func (s *Server) scanMachine(w http.ResponseWriter, r *http.Request, sess session) {
	if s.scan == nil {
		writeError(w, http.StatusServiceUnavailable, "scan_unavailable", "Machine scanning isn't set up on this server yet.")
		return
	}
	mediaType := r.Header.Get("Content-Type")
	if mediaType != "image/jpeg" && mediaType != "image/png" {
		writeError(w, http.StatusUnsupportedMediaType, "bad_photo", "Send the photo as a JPEG or PNG.")
		return
	}
	image, err := io.ReadAll(http.MaxBytesReader(w, r.Body, maxScanPhoto))
	if err != nil || len(image) == 0 {
		writeError(w, http.StatusRequestEntityTooLarge, "bad_photo", "That photo is too large. Try again.")
		return
	}

	used, err := s.accounts.store.CountScansSince(r.Context(), sess.user.ID, time.Now().Add(-24*time.Hour))
	if err != nil {
		s.internalError(w, "count scans", err)
		return
	}
	if used >= s.scan.dailyLimit {
		writeError(w, http.StatusTooManyRequests, "scan_limit", "You've used today's scans. Search for the machine instead, or scan again tomorrow.")
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), scanTimeout)
	defer cancel()
	result, err := s.scan.recogniser.Recognise(ctx, image, mediaType)
	if errors.Is(err, scan.ErrUnreadable) {
		writeError(w, http.StatusUnprocessableEntity, "unreadable", "Couldn't make out a machine in that photo. Try again with the whole machine in view.")
		return
	}
	if err != nil {
		s.logger.Error("machine scan", "err", err)
		writeError(w, http.StatusBadGateway, "scan_failed", "The scan didn't work this time. Try again in a moment.")
		return
	}

	machineID := ""
	if len(result.Matches) > 0 {
		machineID = result.Matches[0].MachineID
	}
	// The scan has been paid for, so the person gets the answer even if logging it fails.
	if err := s.accounts.store.RecordScan(r.Context(), sess.user.ID, machineID, result.ExerciseID, result.Description); err != nil {
		s.logger.Error("record machine scan", "err", err)
	}
	writeJSON(w, http.StatusOK, result)
}
