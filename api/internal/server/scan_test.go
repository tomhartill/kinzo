package server

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/tomhartill/kinzo/api/internal/scan"
)

type fakeRecogniser struct {
	result    scan.Result
	err       error
	mediaType string
}

func (f *fakeRecogniser) Recognise(_ context.Context, _ []byte, mediaType string) (scan.Result, error) {
	f.mediaType = mediaType
	return f.result, f.err
}

func (a *testAPI) scanPhoto(token, contentType string, photo []byte) *httptest.ResponseRecorder {
	a.t.Helper()
	req := httptest.NewRequest("POST", "/v1/scan/machine", bytes.NewReader(photo))
	req.Header.Set("Content-Type", contentType)
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	a.handler.ServeHTTP(rec, req)
	return rec
}

func TestMachineScan(t *testing.T) {
	api := newTestAPI(t)
	tom := api.signIn("g-scan", "Tom", "tom@example.com")
	api.scans.result = scan.Result{
		Matches:     []scan.Match{{MachineID: "6a2f3c43-35f4-5b1c-9d67-3d0e58e4a5d5", Confidence: scan.High}},
		ExerciseID:  "0abe4af6-3057-5130-9982-199c2f5f1b36",
		Description: "A seated chest press.",
	}

	expectStatus(t, api.scanPhoto("", "image/jpeg", []byte{1}), http.StatusUnauthorized)
	expectStatus(t, api.scanPhoto(tom.Token, "text/plain", []byte{1}), http.StatusUnsupportedMediaType)

	rec := api.scanPhoto(tom.Token, "image/jpeg", []byte{0xff, 0xd8})
	expectStatus(t, rec, http.StatusOK)
	got := decode[scan.Result](t, rec)
	if len(got.Matches) != 1 || got.ExerciseID != api.scans.result.ExerciseID || got.Description != "A seated chest press." {
		t.Fatalf("result = %+v", got)
	}
	if api.scans.mediaType != "image/jpeg" {
		t.Errorf("media type = %q", api.scans.mediaType)
	}

	// Nothing matched: still counts towards the limit, and is logged for the catalogue.
	api.scans.result = scan.Result{Matches: []scan.Match{}, Description: "A Nautilus pullover."}
	expectStatus(t, api.scanPhoto(tom.Token, "image/png", []byte{1}), http.StatusOK)

	// The test server allows two a day.
	rec = api.scanPhoto(tom.Token, "image/jpeg", []byte{1})
	expectStatus(t, rec, http.StatusTooManyRequests)
	if code := decode[apiError](t, rec).Error.Code; code != "scan_limit" {
		t.Errorf("code = %q", code)
	}
}

func TestMachineScanUnreadablePhoto(t *testing.T) {
	api := newTestAPI(t)
	tom := api.signIn("g-scan-2", "Tom", "")
	api.scans.err = scan.ErrUnreadable
	expectStatus(t, api.scanPhoto(tom.Token, "image/jpeg", []byte{1}), http.StatusUnprocessableEntity)
}
