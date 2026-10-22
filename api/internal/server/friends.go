package server

import (
	"errors"
	"html/template"
	"net/http"
	"strings"

	"github.com/tomhartill/kinzo/api/internal/store"
)

// friendError answers the errors friend actions share.
func (s *Server) friendError(w http.ResponseWriter, what string, err error) bool {
	switch {
	case err == nil:
		return false
	case errors.Is(err, store.ErrNotFound):
		writeError(w, http.StatusNotFound, "not_found", "We couldn't find that person.")
	case errors.Is(err, store.ErrSelf):
		writeError(w, http.StatusBadRequest, "self", "That's you.")
	default:
		s.internalError(w, what, err)
	}
	return true
}

func (s *Server) searchUsers(w http.ResponseWriter, r *http.Request, sess session) {
	people, err := s.accounts.store.SearchUsers(r.Context(), sess.user.ID, r.URL.Query().Get("q"))
	if s.friendError(w, "search users", err) {
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"users": people})
}

func (s *Server) getUser(w http.ResponseWriter, r *http.Request, sess session) {
	person, err := s.accounts.store.PersonByID(r.Context(), sess.user.ID, r.PathValue("id"))
	if s.friendError(w, "get user", err) {
		return
	}
	writeJSON(w, http.StatusOK, person)
}

func (s *Server) listFriends(w http.ResponseWriter, r *http.Request, sess session) {
	lists, err := s.accounts.store.Friends(r.Context(), sess.user.ID)
	if s.friendError(w, "list friends", err) {
		return
	}
	writeJSON(w, http.StatusOK, lists)
}

func (s *Server) sendFriendRequest(w http.ResponseWriter, r *http.Request, sess session) {
	var body struct {
		UserID string `json:"user_id"`
	}
	if !readJSON(w, r, &body) {
		return
	}
	rel, err := s.accounts.store.SendFriendRequest(r.Context(), sess.user.ID, body.UserID)
	if s.friendError(w, "send friend request", err) {
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"relationship": rel})
}

func (s *Server) acceptFriendRequest(w http.ResponseWriter, r *http.Request, sess session) {
	err := s.accounts.store.AcceptFriendRequest(r.Context(), sess.user.ID, r.PathValue("id"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "not_found", "That friend request has been withdrawn.")
		return
	}
	if s.friendError(w, "accept friend request", err) {
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) deleteFriendRequest(w http.ResponseWriter, r *http.Request, sess session) {
	if s.friendError(w, "delete friend request", s.accounts.store.DeleteFriendRequest(r.Context(), sess.user.ID, r.PathValue("id"))) {
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) removeFriend(w http.ResponseWriter, r *http.Request, sess session) {
	if s.friendError(w, "remove friend", s.accounts.store.RemoveFriend(r.Context(), sess.user.ID, r.PathValue("id"))) {
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) listBlocks(w http.ResponseWriter, r *http.Request, sess session) {
	people, err := s.accounts.store.Blocked(r.Context(), sess.user.ID)
	if s.friendError(w, "list blocks", err) {
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"users": people})
}

func (s *Server) block(w http.ResponseWriter, r *http.Request, sess session) {
	var body struct {
		UserID string `json:"user_id"`
	}
	if !readJSON(w, r, &body) {
		return
	}
	if s.friendError(w, "block", s.accounts.store.Block(r.Context(), sess.user.ID, body.UserID)) {
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) unblock(w http.ResponseWriter, r *http.Request, sess session) {
	if s.friendError(w, "unblock", s.accounts.store.Unblock(r.Context(), sess.user.ID, r.PathValue("id"))) {
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// invite is someone's invite link.
type invite struct {
	Code string `json:"code"`
	URL  string `json:"url"`
}

func (s *Server) inviteFor(code string) invite {
	return invite{Code: code, URL: s.accounts.publicURL + "/i/" + code}
}

func (s *Server) myInvite(w http.ResponseWriter, r *http.Request, sess session) {
	code, err := s.accounts.store.InviteCode(r.Context(), sess.user.ID)
	if err != nil {
		s.internalError(w, "invite code", err)
		return
	}
	writeJSON(w, http.StatusOK, s.inviteFor(code))
}

func (s *Server) resetInvite(w http.ResponseWriter, r *http.Request, sess session) {
	code, err := s.accounts.store.ResetInviteCode(r.Context(), sess.user.ID)
	if err != nil {
		s.internalError(w, "reset invite code", err)
		return
	}
	writeJSON(w, http.StatusOK, s.inviteFor(code))
}

// getInvite shows whose invite this is before the person accepts it.
func (s *Server) getInvite(w http.ResponseWriter, r *http.Request, sess session) {
	inviter, err := s.accounts.store.Inviter(r.Context(), r.PathValue("code"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "invite_not_found", "This invite link no longer works. Ask for a new one.")
		return
	}
	if err != nil {
		s.internalError(w, "get invite", err)
		return
	}
	person, err := s.accounts.store.PersonByID(r.Context(), sess.user.ID, inviter.ID)
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "invite_not_found", "This invite link no longer works. Ask for a new one.")
		return
	}
	if err != nil {
		s.internalError(w, "get invite", err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"inviter": person, "is_you": inviter.ID == sess.user.ID})
}

func (s *Server) acceptInvite(w http.ResponseWriter, r *http.Request, sess session) {
	inviter, err := s.accounts.store.AcceptInvite(r.Context(), sess.user.ID, r.PathValue("code"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "invite_not_found", "This invite link no longer works. Ask for a new one.")
		return
	}
	if s.friendError(w, "accept invite", err) {
		return
	}
	writeJSON(w, http.StatusOK, store.Person{PublicUser: inviter, Relationship: store.RelationFriends})
}

// invitePage is what an invite link opens in a browser: who sent it and a button that opens
// the app on the invite.
func (s *Server) invitePage(w http.ResponseWriter, r *http.Request) {
	code := r.PathValue("code")
	inviter, err := s.accounts.store.Inviter(r.Context(), code)
	if err != nil {
		if !errors.Is(err, store.ErrNotFound) {
			s.logger.Error("invite page", "err", err)
		}
		writeMessagePage(w, http.StatusNotFound, "This invite link no longer works", "Ask your friend to send you a new one.")
		return
	}
	name := inviter.DisplayName
	if name == "" {
		name = "@" + inviter.Username
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_ = pageTemplate.Execute(w, page{
		Title:      name + " wants to train with you",
		Body:       "Open the invite in Kinzo to become friends and see each other's workouts and PRs.",
		ButtonText: "Open in Kinzo",
		ButtonURL:  template.URL("kinzo://invite/" + strings.Trim(code, "/")),
	})
}

type page struct {
	Title      string
	Body       string
	ButtonText string
	ButtonURL  template.URL
}

func writeMessagePage(w http.ResponseWriter, status int, title, body string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	_ = pageTemplate.Execute(w, page{Title: title, Body: body})
}

// A plain page in the app's style: black and white, flat, one button.
var pageTemplate = template.Must(template.New("page").Parse(`<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{{.Title}} · Kinzo</title>
<style>
  :root { color-scheme: light dark; --fg: #0a0a0a; --muted: #5c5c5c; --bg: #ffffff; }
  @media (prefers-color-scheme: dark) { :root { --fg: #f5f5f5; --muted: #a3a3a3; --bg: #0a0a0a; } }
  body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: var(--bg); color: var(--fg);
         font: 16px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
  main { max-width: 360px; padding: 32px 16px; }
  .brand { font-weight: 700; letter-spacing: .08em; text-transform: uppercase; font-size: 13px; color: var(--muted); }
  h1 { font-size: 28px; line-height: 1.2; margin: 12px 0; }
  p { color: var(--muted); margin: 0 0 28px; }
  a.button { display: block; text-align: center; padding: 14px 20px; border-radius: 12px; background: var(--fg); color: var(--bg);
             text-decoration: none; font-weight: 600; }
</style>
</head>
<body>
<main>
  <div class="brand">Kinzo</div>
  <h1>{{.Title}}</h1>
  <p>{{.Body}}</p>
  {{if .ButtonURL}}<a class="button" href="{{.ButtonURL}}">{{.ButtonText}}</a>{{end}}
</main>
</body>
</html>
`))
