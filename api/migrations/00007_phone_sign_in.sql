-- +goose Up
-- Sign in with an email and a code texted or WhatsApped to a phone. The phone number is what's
-- verified; the email is only the account's name for it. See
-- docs/specs/email-and-phone-sign-in.md.

-- Emails typed at a phone sign-up are nobody's proof, so they stay unverified (null) and are
-- never used to join a Google or Facebook sign-in to the account. Until now only
-- provider-verified emails were stored, so existing ones count as verified.
ALTER TABLE users ADD COLUMN email_verified_at timestamptz;
UPDATE users SET email_verified_at = created_at WHERE email IS NOT NULL;

-- A phone identity's subject is the number in E.164 (+447700900123).
ALTER TABLE auth_identities DROP CONSTRAINT auth_identities_provider_check;
ALTER TABLE auth_identities ADD CONSTRAINT auth_identities_provider_check
    CHECK (provider IN ('google', 'apple', 'facebook', 'phone'));

-- One row per code sent. The code itself lives with the sender (Twilio Verify makes and checks
-- it); provider_ref is the sender's handle on it. Rows also count towards the sending limits,
-- and are cleared out after a day.
CREATE TABLE phone_challenges (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    purpose             text NOT NULL CHECK (purpose IN ('sign_in', 'add_phone')),
    user_id             uuid REFERENCES users (id) ON DELETE CASCADE,
    email               text,
    phone_e164          text NOT NULL,
    channel             text NOT NULL CHECK (channel IN ('sms', 'whatsapp')),
    provider_ref        text NOT NULL,
    attempts            integer NOT NULL DEFAULT 0,
    ip                  inet,
    created_at          timestamptz NOT NULL DEFAULT now(),
    expires_at          timestamptz NOT NULL,
    used_at             timestamptz
);
CREATE INDEX phone_challenges_phone ON phone_challenges (phone_e164, created_at);
CREATE INDEX phone_challenges_ip ON phone_challenges (ip, created_at);

-- +goose Down
DROP TABLE phone_challenges;
DELETE FROM auth_identities WHERE provider = 'phone';
ALTER TABLE auth_identities DROP CONSTRAINT auth_identities_provider_check;
ALTER TABLE auth_identities ADD CONSTRAINT auth_identities_provider_check
    CHECK (provider IN ('google', 'apple', 'facebook'));
ALTER TABLE users DROP COLUMN email_verified_at;
