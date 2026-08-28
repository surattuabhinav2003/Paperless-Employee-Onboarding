# Deployment configuration

Everything the infrastructure team needs to set. Nothing here is optional
guesswork: each value below either has no safe default, or has a default that is
correct for a developer's laptop and wrong for a server.

Values not listed have sensible defaults and should be left unset.

---

## 1. Secrets — generate these, do not reuse anything

The application refuses to start without the first two. That is deliberate: it
should fail loudly rather than run with a guessable key.

| Variable | How to produce it |
|---|---|
| `JWT_SECRET` | `openssl rand -base64 48` — signs HR session tokens |
| `PORTAL_TOKEN_ENCRYPTION_SECRET` | `openssl rand -base64 48` — encrypts candidate portal links |
| `DATABASE_PASSWORD` | the database user's password |
| `MAIL_PASSWORD` | SendGrid API key (the username is the literal `apikey`) |
| `AZURE_CLIENT_SECRET` | app registration → Certificates & secrets |

**Rotating `PORTAL_TOKEN_ENCRYPTION_SECRET` invalidates every live candidate
link.** Existing candidates would need their invitation resending. Set it once
and keep it.

---

## 2. Database

```
DATABASE_URL=jdbc:postgresql://<host>:5432/<database>
DATABASE_USERNAME=<user>
DATABASE_PASSWORD=<secret>
```

The user needs `CREATE TABLE` rights on first start — Flyway builds the schema.
Thereafter it only reads and writes.

**Do not set `JPA_DDL_AUTO`.** It defaults to `validate`, which is what keeps
Hibernate from touching the schema. Setting it to `update` puts schema changes
back in Hibernate's hands, unversioned and unreviewable.

Uploaded documents live in this database (`stored_files`), so **the database
backup is the document backup**. Size it accordingly: uploads are capped at
10MB each.

---

## 3. URLs — the ones that break quietly

```
FRONTEND_URL=https://onboarding.cloudfuze.com
CORS_ALLOWED_ORIGINS=https://onboarding.cloudfuze.com
```

`FRONTEND_URL` is what every emailed link is built from. Left unset it defaults
to `localhost:5173`, and candidates receive links that work on nobody's machine.

`CORS_ALLOWED_ORIGINS` is the exact origin the browser loads the app from —
scheme and host, no trailing slash. Wrong, and every API call is blocked by the
browser with nothing useful in the server log. Comma-separate if there is more
than one.

---

## 4. Email

```
EMAIL_PROVIDER=smtp
EMAIL_FROM_ADDRESS=onboarding@cloudfuze.com
EMAIL_FROM_NAME=Neutara Onboarding
EMAIL_REPLY_TO=Aditya.Rompella@neutara.com
EMAIL_SUPPORT_CONTACT=Aditya.Rompella@neutara.com
EMAIL_HR_NOTIFY=Aditya.Rompella@neutara.com
EMAIL_ARCHIVE=Aditya.Rompella@neutara.com

MAIL_HOST=smtp.sendgrid.net
MAIL_PORT=587
MAIL_USERNAME=apikey
MAIL_PASSWORD=<SendGrid API key>
```

`EMAIL_FROM_ADDRESS` **must be a sender SendGrid has authenticated**, or every
send is rejected with `550 does not match a verified Sender Identity` and
nothing goes out.

`EMAIL_ARCHIVE` blind-copies one mailbox on everything, so the whole
correspondence sits in one place. Verification codes are deliberately excluded.

### Sent Items (not yet available)

Emails currently go out through SendGrid, which is a relay: nothing appears in
anyone's Outlook **Sent Items**. Fixing that needs a tenant permission on the
`HR-onboarding` app registration (client ID `94567b93-…`):

- Microsoft Graph → **Application permissions** → `Mail.Send`
- **Grant admin consent**
- Scope it to one mailbox with an Exchange `New-ApplicationAccessPolicy`,
  otherwise the grant lets the app send as anyone in the tenant

Then, and only then:

```
EMAIL_PROVIDER=graph
EMAIL_GRAPH_SENDER=Aditya.Rompella@neutara.com
```

---

## 5. Microsoft sign-in

```
AZURE_TENANT_ID=66d8848d-26b6-4147-8124-127624d7b3a6
AZURE_CLIENT_ID=94567b93-d905-43f6-9648-63a98eb1429a
AZURE_HR_EMAILS=<comma-separated addresses allowed to sign in>
```

`AZURE_HR_EMAILS` is an allow-list. **Blank means anyone in the tenant can sign
in to the HR console** — set it.

In the app registration, the production origin must be registered as a
**Single-page application** redirect URI (not Web), or sign-in fails with a CORS
error.

---

## 6. Seeding the first account

```
SEED_HR_EMAIL=admin@cloudfuze.com
SEED_HR_PASSWORD=<a real password>
SEED_ADMIN_EMAILS=<comma-separated admins>
```

Creates one HR account on first start if it does not exist. With no password set
it generates one and prints it to the log — fine for a first boot you are
watching, poor if logs are shipped elsewhere.

`SEED_ADMIN_EMAILS` promotes those addresses to administrator. **Check the
default before deploying** — it still contains an address from a previous
project.

Set `SEED_ENABLED=false` once the account exists.

---

## 7. Frontend — build-time, not runtime

Vite inlines these **when the frontend is built**. Setting them on the server
does nothing; the built files already contain whatever was present at build
time.

```
VITE_AZURE_CLIENT_ID=94567b93-d905-43f6-9648-63a98eb1429a
VITE_AZURE_TENANT_ID=66d8848d-26b6-4147-8124-127624d7b3a6
VITE_API_BASE_URL=https://api.cloudfuze.com/api   # only if the API is on another origin
```

Build without the Azure pair and the site ships with the Microsoft button
silently absent. This is the single easiest thing to get wrong in the whole
deployment.

If the frontend is served from a static host, it needs a **rewrite rule sending
unknown paths to `index.html`** — it is a single-page app, so `/candidates/123`
is not a file, and without the rule a refresh or an emailed deep link 404s.

---

## 8. Health check

```
GET /actuator/health   →   200 {"status":"UP"}
```

Point the platform's health check here. It reports on the database, not on the
mail server: a mail outage must not read as an outage of the console.

Allow ~30s for startup before the first check.

---

## Things to leave alone

| Variable | Why |
|---|---|
| `JPA_DDL_AUTO` | defaults to `validate`; Flyway owns the schema |
| `FLYWAY_ENABLED` | on; turning it off means no schema |
| `STORAGE_PROVIDER` | defaults to `database`; `local` loses files on redeploy |
| `PORTAL_VERIFICATION_ENABLED` | on; it is the second factor on candidate portals |
| `RATE_LIMIT_ENABLED` | on; it is the brute-force protection on sign-in |

---

## Known limitations to plan around

- **Run one instance.** Rate-limit counters are held in memory, so each instance
  keeps its own — two instances double the effective limits. Fixing this means
  moving them to shared storage.
- **Restarts reset rate limits.** A restart clears the counters.
- **Documents live in the database.** Fine at this scale; if it grows past a few
  gigabytes, object storage is a drop-in replacement for one interface.
