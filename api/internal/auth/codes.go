package auth

import (
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"math/big"
	"net/http"
	"net/url"
	"strings"
	"sync"
)

// CodeSender texts or WhatsApps one-time sign-in codes and checks them. Twilio Verify is the
// real one; LogSender is for development.
type CodeSender interface {
	// Send sends a new code to phone (E.164) by channel ("sms" or "whatsapp") and returns the
	// sender's reference for that send.
	Send(ctx context.Context, phone, channel string) (ref string, err error)
	// Check reports whether code is right for that send. ErrCodeExpired means the send can't
	// be checked any more (too old, or out of tries).
	Check(ctx context.Context, ref, phone, code string) (bool, error)
}

// ErrCodeExpired means a code can no longer be used, and a new one is needed.
var ErrCodeExpired = errors.New("code expired")

// ErrSendRefused means the sender wouldn't send to that number by that channel, for example
// a number that can't get WhatsApp. The app offers the other channel.
var ErrSendRefused = errors.New("send refused")

// Twilio sends and checks codes with Twilio Verify, which makes the codes itself and has its
// own fraud protection.
type Twilio struct {
	AccountSID string
	AuthToken  string
	ServiceSID string
	HTTP       *http.Client

	// BaseURL replaces https://verify.twilio.com in tests.
	BaseURL string
}

func (t *Twilio) Send(ctx context.Context, phone, channel string) (string, error) {
	var v struct {
		SID string `json:"sid"`
	}
	err := t.post(ctx, "/Verifications", url.Values{"To": {phone}, "Channel": {channel}}, &v)
	if err != nil {
		return "", err
	}
	return v.SID, nil
}

func (t *Twilio) Check(ctx context.Context, ref, phone, code string) (bool, error) {
	var v struct {
		Status string `json:"status"`
	}
	err := t.post(ctx, "/VerificationCheck", url.Values{"VerificationSid": {ref}, "Code": {code}}, &v)
	if err != nil {
		return false, err
	}
	return v.Status == "approved", nil
}

func (t *Twilio) post(ctx context.Context, path string, form url.Values, out any) error {
	base := t.BaseURL
	if base == "" {
		base = "https://verify.twilio.com"
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost,
		base+"/v2/Services/"+t.ServiceSID+path, strings.NewReader(form.Encode()))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.SetBasicAuth(t.AccountSID, t.AuthToken)
	client := t.HTTP
	if client == nil {
		client = http.DefaultClient
	}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("twilio verify: %w", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	if resp.StatusCode >= 300 {
		var e struct {
			Code    int    `json:"code"`
			Message string `json:"message"`
		}
		_ = json.Unmarshal(body, &e)
		switch {
		// A check on a verification that's approved, expired or out of tries is a 404, and
		// 60202 is too many checks.
		case resp.StatusCode == http.StatusNotFound, e.Code == 60202:
			return ErrCodeExpired
		// 60200 is a number Twilio won't accept for that channel, 60203 too many sends to the
		// number, 60205 a landline for SMS, 60410 a number range blocked for fraud.
		case e.Code == 60200, e.Code == 60203, e.Code == 60205, e.Code == 60410:
			return fmt.Errorf("%w: twilio %d %s", ErrSendRefused, e.Code, e.Message)
		}
		return fmt.Errorf("twilio verify: %s: %d %s", resp.Status, e.Code, e.Message)
	}
	return json.Unmarshal(body, out)
}

// LogSender makes codes itself and writes them to the log instead of sending them, for
// development. Codes live in memory, so they are lost when the API restarts.
type LogSender struct {
	Logger *slog.Logger

	mu    sync.Mutex
	codes map[string]string // ref to code
}

func (l *LogSender) Send(_ context.Context, phone, channel string) (string, error) {
	code := RandomCode()
	ref := NewToken("log_", 12)
	l.mu.Lock()
	if l.codes == nil {
		l.codes = map[string]string{}
	}
	l.codes[ref] = code
	l.mu.Unlock()
	l.Logger.Info("sign-in code (not sent: KINZO_OTP_PROVIDER is log)", "phone", phone, "channel", channel, "code", code)
	return ref, nil
}

func (l *LogSender) Check(_ context.Context, ref, _, code string) (bool, error) {
	l.mu.Lock()
	want, ok := l.codes[ref]
	l.mu.Unlock()
	if !ok {
		return false, ErrCodeExpired
	}
	return subtle.ConstantTimeCompare([]byte(want), []byte(code)) == 1, nil
}

// RandomCode returns six random digits.
func RandomCode() string {
	n, err := rand.Int(rand.Reader, big.NewInt(1_000_000))
	if err != nil {
		panic(err) // crypto/rand never fails on supported platforms
	}
	return fmt.Sprintf("%06d", n.Int64())
}

// WithTestNumbers wraps a sender so that the given numbers (E.164 to code) never get a real
// message and always accept their fixed code. App store reviewers sign in with one.
func WithTestNumbers(next CodeSender, numbers map[string]string) CodeSender {
	if len(numbers) == 0 {
		return next
	}
	return &testNumbers{next: next, numbers: numbers}
}

type testNumbers struct {
	next    CodeSender
	numbers map[string]string
}

const testRefPrefix = "test:"

func (t *testNumbers) Send(ctx context.Context, phone, channel string) (string, error) {
	if _, ok := t.numbers[phone]; ok {
		return testRefPrefix + phone, nil
	}
	return t.next.Send(ctx, phone, channel)
}

func (t *testNumbers) Check(ctx context.Context, ref, phone, code string) (bool, error) {
	if number, ok := strings.CutPrefix(ref, testRefPrefix); ok {
		want, known := t.numbers[number]
		return known && number == phone && subtle.ConstantTimeCompare([]byte(want), []byte(code)) == 1, nil
	}
	return t.next.Check(ctx, ref, phone, code)
}
