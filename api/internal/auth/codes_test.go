package auth

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestTwilioVerify(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if user, pass, _ := r.BasicAuth(); user != "AC1" || pass != "secret" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		_ = r.ParseForm()
		switch r.URL.Path {
		case "/v2/Services/VA1/Verifications":
			if r.Form.Get("To") == "+447700900999" {
				w.WriteHeader(http.StatusBadRequest)
				_, _ = w.Write([]byte(`{"code": 60200, "message": "Invalid parameter"}`))
				return
			}
			_, _ = w.Write([]byte(`{"sid": "VE1", "status": "pending"}`))
		case "/v2/Services/VA1/VerificationCheck":
			switch r.Form.Get("Code") {
			case "123456":
				_, _ = w.Write([]byte(`{"status": "approved"}`))
			case "999999":
				w.WriteHeader(http.StatusNotFound)
				_, _ = w.Write([]byte(`{"code": 20404, "message": "not found"}`))
			default:
				_, _ = w.Write([]byte(`{"status": "pending"}`))
			}
		}
	}))
	defer srv.Close()
	tw := &Twilio{AccountSID: "AC1", AuthToken: "secret", ServiceSID: "VA1", BaseURL: srv.URL}
	ctx := context.Background()

	ref, err := tw.Send(ctx, "+447911123456", "sms")
	if err != nil || ref != "VE1" {
		t.Fatalf("send = %q, %v", ref, err)
	}
	if _, err := tw.Send(ctx, "+447700900999", "whatsapp"); !errors.Is(err, ErrSendRefused) {
		t.Fatalf("refused send err = %v", err)
	}
	if ok, err := tw.Check(ctx, ref, "+447911123456", "123456"); !ok || err != nil {
		t.Fatalf("right code = %v, %v", ok, err)
	}
	if ok, err := tw.Check(ctx, ref, "+447911123456", "000000"); ok || err != nil {
		t.Fatalf("wrong code = %v, %v", ok, err)
	}
	if _, err := tw.Check(ctx, ref, "+447911123456", "999999"); !errors.Is(err, ErrCodeExpired) {
		t.Fatalf("expired err = %v", err)
	}
}

func TestPhoneRules(t *testing.T) {
	rules := PhoneRules{Countries: []string{"GB"}, TestNumbers: map[string]string{"+447700900123": "123456"}}
	cases := map[string]error{
		"+44 7911 123456": nil,
		"07911 123456":    nil,
		"+447700900123":   nil, // a test number, though not a real one
		"+447700900124":   ErrInvalidPhone,
		"+1 202 555 0143": ErrUnsupportedCountry,
		"hello":           ErrInvalidPhone,
		"":                ErrInvalidPhone,
	}
	for typed, want := range cases {
		if _, err := rules.Parse(typed); !errors.Is(err, want) {
			t.Errorf("Parse(%q) err = %v, want %v", typed, err, want)
		}
	}
	if got, _ := rules.Parse("07911 123456"); got != "+447911123456" {
		t.Errorf("E.164 = %q", got)
	}
	if hint := PhoneHint("+447911123456"); hint != "•••• 456" {
		t.Errorf("hint = %q", hint)
	}
}
