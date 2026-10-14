# Spec: sign in with email and a phone code

Status: **specced, not built yet**. Google and Facebook sign-in are live in the API and app;
this adds a third way in that needs no Google or Facebook account. The API side is next; the
app side follows the contract in [`openapi/kinzo.yaml`](../../openapi/kinzo.yaml).

## What the person sees

Sign-in screen: **Continue with Google**, **Continue with Facebook**, then **Continue with email**.

1. **Email.** "What's your email?" One field, keyboard and autofill set to email.
2. **Phone, first time only.** If the email is new: "What's your mobile number?" A country
   picker (defaulting to the phone's region) and the number. Two buttons: **Text me a code** and
   **WhatsApp me a code**.
   A returning person skips this step: the code goes straight to the number on their account,
   and the next screen says where ("We sent a code to •••• 821 by text").
3. **Code.** Six boxes. iOS fills an SMS code from the keyboard by itself; Android reads it with
   the SMS User Consent API (one tap, no permission). Under the boxes: **Resend** (after 30
   seconds), **Send by WhatsApp instead** / **Send by text instead**, and **Use a different
   email**.
4. **New account only:** the existing "Pick a username" screen, with a "Your name" field added
   above it (phone sign-ups have no name from a provider).

Wrong code: the boxes shake, clear, and say "That code isn't right. 4 tries left." Expired: "That
code has expired" with a **Send a new code** button. Errors never mention internals.

## Rules

- **The phone number is what's verified; the email is the account's name for it.** Typing a
  known email sends the code to that account's phone, and only a correct code signs in.
- **An email that belongs to a Google or Facebook account with no phone** doesn't get a code:
  the app says "This email signs in with Google" and shows that button. (Otherwise anyone who
  knows the email could attach their own phone to the account.) Once signed in that way, the
  person can add a phone in Account to use codes too.
- **New email, number already on an account:** the code still goes out, and a correct code signs
  in to the number's existing account. The typed email is not attached to it. The app says
  "This number already has an account, so we've signed you in to it."
- **Emails from phone sign-ups are unverified** (`email_verified_at` is null). They are never
  used to join a Google or Facebook sign-in to the account. If someone later signs in with Google
  or Facebook using that same, provider-verified email, the provider sign-in gets a new account
  with the email, and the unverified copy is removed from the phone account. Nobody can squat
  someone else's email to capture their Google sign-in.
- Emails are trimmed and lowercased. Phone numbers are stored in E.164 (`+447700900123`), parsed
  and checked with libphonenumber (`github.com/nyaruka/phonenumbers`).
- Codes: 6 digits, valid for 10 minutes, 5 wrong tries per code, then a new code is needed.
- Sending limits (to stop SMS-pumping fraud, which bills us per message):
  - 1 code per number per 30 seconds, 5 per hour, 10 per day.
  - 20 code requests per IP address per hour.
  - Only countries in `KINZO_PHONE_COUNTRIES` (starts as `GB`). Others get "Codes can't be sent
    to that country yet."
- A person can't remove their phone unless the account has another way in (Google or Facebook).

## API

Full schemas are in [`openapi/kinzo.yaml`](../../openapi/kinzo.yaml) (tagged "planned"). Successful
sign-in returns the same `SignedIn` body as the other providers, so the app's session handling
doesn't change.

| Endpoint | Body | Result |
| --- | --- | --- |
| `POST /v1/auth/phone/start` | `{email, phone?, channel}` | `next: "code"` (sent), `next: "phone"` (new email, ask for a number), or `next: "provider"` (use Google/Facebook) |
| `POST /v1/auth/phone/verify` | `{challenge_id, code}` | `SignedIn` (`token`, `user`, `new_user`) |
| `POST /v1/me/phone/start` | `{phone, channel}` | Signed in: send a code to add or change the account's phone |
| `POST /v1/me/phone/verify` | `{challenge_id, code}` | The updated `User` |
| `DELETE /v1/me/phone` | | Removes the phone, if the account has another way in |

`channel` is `sms` or `whatsapp`. Calling `start` again is how the app resends or switches
channel. `phone` is ignored when the email is already known, so the response never reveals more
than "this email has an account" and the last three digits of its number.

`/v1/auth/providers` gains `"phone"` in `providers` and a `phone_channels` list (`["sms",
"whatsapp"]`, or just `["sms"]` until WhatsApp is set up), so the app only shows what works.

`User` gains `phone` (the person's own number, E.164, or absent), `email_verified` and
`sign_in_methods` (for example `["google", "phone"]`).

Errors (all `{"error": {"code", "message"}}`, messages written for the person):

| Status | `code` | When |
| --- | --- | --- |
| 400 | `invalid_email`, `invalid_phone` | Can't be parsed |
| 400 | `unsupported_country` | Number outside `KINZO_PHONE_COUNTRIES` |
| 400 | `wrong_code` | Also returns `attempts_left` |
| 409 | `phone_taken` | Adding a number that is on another account |
| 409 | `last_sign_in_method` | Removing the only way in |
| 410 | `code_expired` | Over 10 minutes, or out of tries |
| 429 | `too_many_codes` | A sending limit; returns `retry_in` seconds |
| 502 | `send_failed` | The SMS/WhatsApp provider refused; the app offers the other channel |

## Data

Migration `00006_phone_sign_in.sql`:

- `users.email_verified_at timestamptz`. Backfilled to `created_at` for existing rows, since today
  only provider-verified emails are stored.
- `auth_identities.provider` allows `'phone'`; `subject` is the E.164 number. Phone sign-in then
  reuses the existing identity and session code.
- New `phone_challenges` table, one row per code sent:

  | Column | Type | Notes |
  | --- | --- | --- |
  | `id` | uuid | Returned as `challenge_id` |
  | `purpose` | text | `sign_in` or `add_phone` |
  | `user_id` | uuid null | The known account, if any |
  | `email` | text null | As typed, for a sign-up |
  | `phone_e164` | text | |
  | `channel` | text | `sms` or `whatsapp` |
  | `provider_ref` | text | The provider's verification ID |
  | `attempts` | int | Wrong tries so far |
  | `ip` | inet | For the per-IP limit |
  | `created_at`, `expires_at`, `used_at` | timestamptz | `used_at` set on success; a challenge works once |

  Indexed on `(phone_e164, created_at)` and `(ip, created_at)` for the limits. Rows older than a
  day are deleted on a timer.

## Sending the codes

Behind one Go interface in `internal/auth`, so the provider can change:

```go
type CodeSender interface {
    // Send texts or WhatsApps a new code to phone and returns the provider's reference.
    Send(ctx context.Context, phone, channel string) (ref string, err error)
    // Check reports whether code is right for that send.
    Check(ctx context.Context, ref, phone, code string) (bool, error)
}
```

- **Production: [Twilio Verify](https://www.twilio.com/docs/verify).** One service does SMS and
  WhatsApp, makes and checks the codes, and has SMS-pumping protection (Fraud Guard). Charged
  per successful verification plus the message; WhatsApp is usually cheaper than SMS. Check
  current prices on Twilio's pricing page before launch.
- **Development: `log` sender.** Prints the code to the API log and accepts it. Used when no
  Twilio settings are present and in tests.
- **Test numbers:** `KINZO_OTP_TEST_NUMBERS=+447700900123:123456` always accepts that code and
  sends nothing. The 07700 900xxx range is reserved by Ofcom for fiction, so it can never be a
  real person. Store reviewers get one of these.

New settings: `KINZO_OTP_PROVIDER` (`twilio` or `log`), `TWILIO_ACCOUNT_SID`,
`TWILIO_AUTH_TOKEN`, `TWILIO_VERIFY_SERVICE_SID`, `KINZO_PHONE_COUNTRIES`,
`KINZO_OTP_TEST_NUMBERS`.

## Tests

- Store and server tests in the style of `internal/server/accounts_test.go`, with a fake
  `CodeSender`:
  - New email and new number makes an account, with `new_user` true.
  - A known email sends the code to the number on the account and ignores a typed number.
  - A Google-only email gets `next: "provider"`, and no code is sent.
  - A new email with an existing number signs in to that number's account.
  - A provider sign-in with a verified email beats an unverified phone-account email.
  - Wrong codes count down and then expire; a used challenge can't be reused.
  - Every sending limit, the country list, and test numbers.
- App: the start/verify client calls against MockEngine, and the screen states in the view model.

## Open questions

- [ ] **WhatsApp sender.** Twilio Verify's WhatsApp channel may need Kinzo's own WhatsApp
  Business sender and an approved authentication template. Check during Twilio setup; launch
  with SMS only if it isn't ready.
- [ ] **Countries.** `GB` only to start. Which others, given "Where users are" in the product
  spec?
- [ ] **Verifying emails.** Sending a confirmation link needs an email provider (Postmark or
  Resend, say). It's worth adding before email is used for anything beyond sign-in lookup, such as
  receipts or recovery.
- [ ] **Monthly cap.** Set a spending alert in Twilio, and decide what the API does when it is hit.
