# Deploying to a server

Three containers: PostgreSQL, the Spring Boot API, and nginx serving the built
React app and proxying `/api` to the API. Only nginx is published.

```bash
git clone <repo> hr-onboarding-portal && cd hr-onboarding-portal
cp .env.deploy.example .env         # fill it in - see below
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml logs -f backend
```

The first build takes a few minutes; it compiles the backend and the frontend
inside Docker, so the server needs nothing installed but Docker itself.

## The values that have no default

Compose refuses to start without these, rather than booting something
half-configured:

| Variable | Why it cannot be guessed |
|---|---|
| `POSTGRES_PASSWORD` | `openssl rand -base64 24` |
| `JWT_SECRET` | 32+ chars. `openssl rand -base64 48` |
| `FRONTEND_URL` | Every invitation link is built from it. Wrong, and candidates get a link to a machine they cannot reach |
| `AZURE_HR_EMAILS` | Left empty, **any** account in the tenant that signs in becomes an HR user and can read candidates' Aadhaar and PAN scans |
| `SEED_ADMIN_EMAILS` | Who gets Admin settings, including permanent deletion. The built-in default still lists an address from a previous company |

## Two things that catch people out

**Microsoft sign-in needs HTTPS.** Entra ID accepts `https://` redirect URIs for
a real hostname and `http://` only for localhost, so on a plain-http dev server
the Microsoft button cannot work at all. `FRONTEND_URL` must also be registered
as a **SPA** redirect URI on the app registration, or sign-in fails with
AADSTS50011. Until there is a certificate, use the seeded password login: leave
`SEED_HR_PASSWORD` blank and read the generated one out of the backend log.

**Email sends nothing until it is switched on.** `EMAIL_PROVIDER=log` is the
default and writes each message to the container log instead of delivering it -
deliberately, so a fresh deploy cannot mail real candidates on its own. Set
`smtp` with the `MAIL_*` values, or `graph`, which additionally needs the
**Mail.Send application permission with admin consent** on the app registration.
Without that consent Graph returns 403 and nothing sends.

## Where the data is

In PostgreSQL, in the `onboarding-db-data` volume - **including the uploaded
documents**, which are stored as rows rather than files. That means a redeploy
cannot lose them and there is no upload directory to mount, and it means the
volume is the only thing that needs backing up:

```bash
docker compose -f docker-compose.prod.yml exec -T db \
  pg_dump -U cloudfuze cloudfuze_onboarding | gzip > onboarding-$(date +%F).sql.gz
```

Flyway owns the schema and runs migrations at startup; Hibernate is set to
`validate`, so a mismatch fails the boot loudly instead of quietly rewriting
tables.

## Updating

```bash
git pull
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

The database volume is untouched by a rebuild. Anything under `VITE_*` is baked
into the frontend bundle at build time, so changing the Azure client or tenant id
needs this rebuild, not a restart.
