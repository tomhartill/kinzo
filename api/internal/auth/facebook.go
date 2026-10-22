package auth

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"net/http"
	"net/url"
)

// DefaultFacebookGraphVersion is the Graph API version used unless configured otherwise.
// Facebook supports each version for about two years.
const DefaultFacebookGraphVersion = "v24.0"

// Facebook signs people in with Facebook Login through the browser.
type Facebook struct {
	AppID        string
	AppSecret    string
	GraphVersion string
	HTTP         *http.Client

	// GraphURL replaces https://graph.facebook.com in tests.
	GraphURL string
}

func (f *Facebook) Name() string { return "facebook" }

func (f *Facebook) AuthURL(state, redirectURI string) string {
	q := url.Values{
		"client_id":     {f.AppID},
		"redirect_uri":  {redirectURI},
		"response_type": {"code"},
		"scope":         {"public_profile,email"},
		"state":         {state},
	}
	return "https://www.facebook.com/" + f.version() + "/dialog/oauth?" + q.Encode()
}

func (f *Facebook) Exchange(ctx context.Context, code, redirectURI string) (Identity, error) {
	q := url.Values{
		"client_id":     {f.AppID},
		"client_secret": {f.AppSecret},
		"redirect_uri":  {redirectURI},
		"code":          {code},
	}
	var token struct {
		AccessToken string `json:"access_token"`
	}
	if err := f.get(ctx, "/oauth/access_token?"+q.Encode(), &token); err != nil {
		return Identity{}, fmt.Errorf("facebook token exchange: %w", err)
	}
	return f.profile(ctx, token.AccessToken)
}

// profile reads who the access token belongs to. Facebook only shares an email the person
// has confirmed, so it counts as verified.
func (f *Facebook) profile(ctx context.Context, accessToken string) (Identity, error) {
	// appsecret_proof ties the call to this app's secret, so a stolen token can't be replayed
	// through the API by someone else.
	mac := hmac.New(sha256.New, []byte(f.AppSecret))
	mac.Write([]byte(accessToken))
	q := url.Values{
		"fields":          {"id,name,email,picture.width(256).height(256)"},
		"access_token":    {accessToken},
		"appsecret_proof": {hex.EncodeToString(mac.Sum(nil))},
	}
	var me struct {
		ID      string `json:"id"`
		Name    string `json:"name"`
		Email   string `json:"email"`
		Picture struct {
			Data struct {
				URL          string `json:"url"`
				IsSilhouette bool   `json:"is_silhouette"`
			} `json:"data"`
		} `json:"picture"`
	}
	if err := f.get(ctx, "/me?"+q.Encode(), &me); err != nil {
		return Identity{}, fmt.Errorf("facebook profile: %w", err)
	}
	if me.ID == "" {
		return Identity{}, ErrInvalidToken
	}
	avatar := me.Picture.Data.URL
	if me.Picture.Data.IsSilhouette {
		avatar = ""
	}
	return Identity{
		Provider:      "facebook",
		Subject:       me.ID,
		Email:         me.Email,
		EmailVerified: me.Email != "",
		Name:          me.Name,
		AvatarURL:     avatar,
	}, nil
}

func (f *Facebook) get(ctx context.Context, pathAndQuery string, v any) error {
	base := f.GraphURL
	if base == "" {
		base = "https://graph.facebook.com"
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, base+"/"+f.version()+pathAndQuery, nil)
	if err != nil {
		return err
	}
	client := f.HTTP
	if client == nil {
		client = defaultHTTP
	}
	return doJSON(client, req, v)
}

func (f *Facebook) version() string {
	if f.GraphVersion != "" {
		return f.GraphVersion
	}
	return DefaultFacebookGraphVersion
}
