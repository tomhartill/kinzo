package store

import "testing"

func TestUsernameFromName(t *testing.T) {
	tests := map[string]string{
		"Tom Hartill":                            "tomhartill",
		"Zoë O'Brien":                            "zoeobrien",
		"José Álvarez":                           "josealvarez",
		"李":                                      "kinzo",
		"Al":                                     "kinzoal",
		"":                                       "kinzo",
		"Maximilian Alexander Featherstonehaugh": "maximilianalexanderf",
	}
	for name, want := range tests {
		if got := UsernameFromName(name); got != want {
			t.Errorf("UsernameFromName(%q) = %q, want %q", name, got, want)
		}
		if got := UsernameFromName(name); !ValidUsername(got) {
			t.Errorf("UsernameFromName(%q) = %q is not a valid username", name, got)
		}
	}
}

func TestValidUsername(t *testing.T) {
	for _, ok := range []string{"tom", "tom.hartill", "tom_h", "t0m"} {
		if !ValidUsername(ok) {
			t.Errorf("%q should be valid", ok)
		}
	}
	for _, bad := range []string{"to", "Tom", "tom hartill", "tom-h", "@tom", "abcdefghijabcdefghijabcdefghijk"} {
		if ValidUsername(bad) {
			t.Errorf("%q should be invalid", bad)
		}
	}
	if got := NormalizeUsername("  @Tom.H "); got != "tom.h" {
		t.Errorf("NormalizeUsername = %q", got)
	}
}
