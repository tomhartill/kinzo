package auth

import (
	"errors"
	"strings"

	"github.com/nyaruka/phonenumbers"
)

var (
	// ErrInvalidPhone means a typed number couldn't be read as a phone number.
	ErrInvalidPhone = errors.New("invalid phone number")
	// ErrUnsupportedCountry means codes aren't sent to that country yet.
	ErrUnsupportedCountry = errors.New("unsupported country")
)

// PhoneRules decides which typed numbers can get a code.
type PhoneRules struct {
	// Countries are ISO region codes such as "GB". A number is allowed when it has one of
	// their calling codes, so "GB" also covers Jersey, Guernsey and the Isle of Man, which
	// share +44 and which the library can't always tell apart.
	Countries []string
	// TestNumbers are always allowed, even though they aren't real numbers.
	TestNumbers map[string]string
}

// Parse reads a typed number, which should start with its country code (+44...), and returns
// it in E.164 (+447700900123).
func (p PhoneRules) Parse(typed string) (string, error) {
	typed = strings.TrimSpace(typed)
	if typed == "" {
		return "", ErrInvalidPhone
	}
	// A number without a country code is read as being in the first allowed country.
	region := "GB"
	if len(p.Countries) > 0 {
		region = p.Countries[0]
	}
	n, err := phonenumbers.Parse(typed, region)
	if err != nil {
		return "", ErrInvalidPhone
	}
	e164 := phonenumbers.Format(n, phonenumbers.E164)
	if _, ok := p.TestNumbers[e164]; ok {
		return e164, nil
	}
	if !phonenumbers.IsValidNumber(n) {
		return "", ErrInvalidPhone
	}
	for _, c := range p.Countries {
		if int32(phonenumbers.GetCountryCodeForRegion(c)) == n.GetCountryCode() {
			return e164, nil
		}
	}
	return "", ErrUnsupportedCountry
}

// PhoneHint is what the app may show of a number: its last three digits.
func PhoneHint(e164 string) string {
	if len(e164) < 3 {
		return "••••"
	}
	return "•••• " + e164[len(e164)-3:]
}
