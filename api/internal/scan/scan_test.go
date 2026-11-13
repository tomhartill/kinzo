package scan

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/anthropics/anthropic-sdk-go/option"
)

func TestTheBundledCatalogueIsValid(t *testing.T) {
	c, err := LoadCatalogue()
	if err != nil {
		t.Fatal(err)
	}
	if len(c.Machines) < 40 {
		t.Fatalf("got %d machines, want the curated list", len(c.Machines))
	}
	ids := map[string]bool{}
	for _, m := range c.Machines {
		if m.ID == "" || m.Name == "" || len(m.Exercises) == 0 {
			t.Errorf("machine %q is incomplete", m.Name)
		}
		if ids[m.ID] {
			t.Errorf("machine ID %s appears twice", m.ID)
		}
		ids[m.ID] = true
	}
}

// fakeClaude answers every Messages request with text, and keeps the last request body.
func fakeClaude(t *testing.T, stopReason, text string) (*httptest.Server, *map[string]any) {
	t.Helper()
	var last map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(body, &last)
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{
			"id":            "msg_test",
			"type":          "message",
			"role":          "assistant",
			"model":         DefaultModel,
			"stop_reason":   stopReason,
			"stop_sequence": nil,
			"content":       []map[string]any{{"type": "text", "text": text}},
			"usage":         map[string]any{"input_tokens": 10, "output_tokens": 10},
		})
	}))
	t.Cleanup(srv.Close)
	return srv, &last
}

func testCatalogue() Catalogue {
	return Catalogue{Version: 1, Machines: []Machine{
		{ID: "leg-press", Name: "Leg press", Exercises: []Exercise{{ID: "ex-leg-press", Name: "Leg Press"}}},
		{ID: "chest-press", Name: "Chest press machine", Aliases: []string{"seated chest press"}, Exercises: []Exercise{
			{ID: "ex-flat", Name: "Machine Bench Press"},
			{ID: "ex-incline", Name: "Machine Incline Chest Press"},
		}},
		{ID: "pec-deck", Name: "Pec deck", Exercises: []Exercise{{ID: "ex-fly", Name: "Butterfly"}}},
	}}
}

func TestRecogniseMapsCodesToIDs(t *testing.T) {
	srv, last := fakeClaude(t, "end_turn",
		`{"machines":[{"code":"M2","confidence":"high"},{"code":"M3","confidence":"low"}],"exercise":"M2.2","description":"An incline chest press."}`)
	r := NewRecogniser("test-key", "", testCatalogue(), option.WithBaseURL(srv.URL))

	got, err := r.Recognise(context.Background(), []byte{0xff, 0xd8}, "image/jpeg")
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Matches) != 2 || got.Matches[0].MachineID != "chest-press" || got.Matches[0].Confidence != High ||
		got.Matches[1].MachineID != "pec-deck" {
		t.Errorf("matches = %+v", got.Matches)
	}
	if got.ExerciseID != "ex-incline" {
		t.Errorf("exercise = %q, want the incline press", got.ExerciseID)
	}
	if got.Description != "An incline chest press." {
		t.Errorf("description = %q", got.Description)
	}

	// The machine list goes in the cached system prompt, and the photo in the message.
	if (*last)["model"] != DefaultModel {
		t.Errorf("model = %v", (*last)["model"])
	}
	system, _ := json.Marshal((*last)["system"])
	if !strings.Contains(string(system), "M2 | Chest press machine | seated chest press") ||
		!strings.Contains(string(system), "M2.2 Machine Incline Chest Press") ||
		!strings.Contains(string(system), "ephemeral") {
		t.Errorf("system prompt doesn't list the machines for caching: %s", system)
	}
	messages, _ := json.Marshal((*last)["messages"])
	if !strings.Contains(string(messages), `"media_type":"image/jpeg"`) {
		t.Errorf("the photo wasn't sent: %s", messages)
	}
}

func TestRecogniseIgnoresCodesThatDontExist(t *testing.T) {
	srv, _ := fakeClaude(t, "end_turn",
		`{"machines":[{"code":"M9","confidence":"high"},{"code":"M1","confidence":"maybe"},{"code":"M1","confidence":"high"}],"exercise":"M2.1","description":"A leg press."}`)
	r := NewRecogniser("test-key", "", testCatalogue(), option.WithBaseURL(srv.URL))

	got, err := r.Recognise(context.Background(), []byte{1}, "image/jpeg")
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Matches) != 1 || got.Matches[0].MachineID != "leg-press" || got.Matches[0].Confidence != Low {
		t.Errorf("matches = %+v", got.Matches)
	}
	// The exercise must belong to the first match.
	if got.ExerciseID != "" {
		t.Errorf("exercise = %q, want none", got.ExerciseID)
	}
}

func TestRecogniseWithNoMatch(t *testing.T) {
	srv, _ := fakeClaude(t, "end_turn", `{"machines":[],"exercise":"","description":"A Nautilus pullover machine."}`)
	r := NewRecogniser("test-key", "", testCatalogue(), option.WithBaseURL(srv.URL))

	got, err := r.Recognise(context.Background(), []byte{1}, "image/png")
	if err != nil {
		t.Fatal(err)
	}
	if len(got.Matches) != 0 || got.Description != "A Nautilus pullover machine." {
		t.Errorf("got %+v", got)
	}
}

func TestRecogniseRefusal(t *testing.T) {
	srv, _ := fakeClaude(t, "refusal", "")
	r := NewRecogniser("test-key", "", testCatalogue(), option.WithBaseURL(srv.URL))

	if _, err := r.Recognise(context.Background(), []byte{1}, "image/jpeg"); !errors.Is(err, ErrUnreadable) {
		t.Errorf("err = %v, want ErrUnreadable", err)
	}
}
