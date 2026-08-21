# CloudFuze HR Onboarding Portal

Paperless HR onboarding: HR manages every candidate from one dashboard, and each
candidate gets **one secure link** that carries them through the whole journey -
document upload and offer acceptance.

The workflow is strictly gated **on the server**:

```
docs_pending ──(HR verifies every required document)──▶ docs_approved
      │                                                      │
      │                                            (candidate accepts offer)
      ▼                                                      ▼
 uploads only                                          offer_accepted
                                                   "Onboarding Complete"
```

`offer_accepted` is terminal: accepting the offer is the last thing a candidate
does, and it sets `completedAt`.

A candidate cannot skip a stage by editing the URL, replaying an API call, or
poking at the React app: every candidate endpoint re-checks the token hash, the
token expiry, and the candidate stage before doing anything.

- **Backend**: Java 21, Spring Boot 3.3, Spring Web / Data JPA / Security, JWT, Bean Validation
- **Frontend**: React 18, Vite, Tailwind CSS, React Router, Axios
- **Database**: PostgreSQL 16
- **Storage**: pluggable `FileStorageService` (local on disk today, S3/Azure/CloudFuze storage next)

---

## 1. Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21+ | `java -version` |
| Node.js | 18+ (tested on 26) | `node -v` |
| PostgreSQL | 14+ (16 recommended) | or Docker, see below |
| Maven | not required | use the bundled `./mvnw` wrapper |
| Docker | optional | quickest way to run PostgreSQL |

---

## 2. MongoDB → PostgreSQL note

This build uses **PostgreSQL** (as requested), through Spring Data JPA. The
schema, including every index, is generated from the JPA model
(`spring.jpa.hibernate.ddl-auto=update` by default). For production, switch to
`JPA_DDL_AUTO=validate` and manage DDL with Flyway or Liquibase.

---

## 3. Database setup

### Option A - Docker (recommended)

```bash
cd hr-onboarding-portal
docker compose up -d postgres
# optional local mail catcher on http://localhost:8025
docker compose --profile mail up -d mailpit
```

### Option B - existing PostgreSQL

```sql
CREATE DATABASE cloudfuze_onboarding;
CREATE USER cloudfuze WITH PASSWORD 'cloudfuze';
GRANT ALL PRIVILEGES ON DATABASE cloudfuze_onboarding TO cloudfuze;
```

Point `DATABASE_URL`, `DATABASE_USERNAME` and `DATABASE_PASSWORD` at it.

---

## 4. Running the backend

```bash
cd backend

# development profile: throwaway JWT secret, seeded HR user, invitation
# emails written to the log
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The API starts on <http://localhost:8080>.

For anything other than local development, supply real configuration instead of
the `dev` profile:

```bash
export DATABASE_URL=jdbc:postgresql://db-host:5432/cloudfuze_onboarding
export DATABASE_USERNAME=cloudfuze
export DATABASE_PASSWORD=...
export JWT_SECRET="$(openssl rand -base64 48)"
export FRONTEND_URL=https://portal.cloudfuze.com
export EMAIL_PROVIDER=smtp MAIL_HOST=... MAIL_USERNAME=... MAIL_PASSWORD=...
export SEED_ENABLED=false
./mvnw spring-boot:run
```

Package a jar with `./mvnw clean package` → `target/onboarding-portal-1.0.0.jar`.

**The application refuses to start without `JWT_SECRET` (min 32 chars) unless the
`dev` profile is active.** That is deliberate - no secret is baked into the code.

---

## 5. Running the frontend

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

`/api` is proxied to `http://localhost:8080` in development (override with
`VITE_API_PROXY_TARGET`). Production build:

```bash
npm run build        # dist/
npm run preview      # serve the build locally
```

If the API is served from another origin, set `VITE_API_BASE_URL` and add that
origin to `CORS_ALLOWED_ORIGINS` on the backend.

---

## 6. Environment variables

Everything is externalised - see [`.env.example`](.env.example),
[`backend/src/main/resources/application-example.properties`](backend/src/main/resources/application-example.properties)
and [`frontend/.env.example`](frontend/.env.example).

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | PostgreSQL connection |
| `JPA_DDL_AUTO` | `update` (dev) or `validate` (prod with migrations) |
| `JWT_SECRET` | HR token signing key, **required**, ≥32 chars |
| `JWT_EXPIRATION` | HR session length, default `8h` |
| `FRONTEND_URL`, `PORTAL_PATH` | used to build candidate portal links |
| `PORTAL_TOKEN_TTL` | candidate link lifetime, default `14d` |
| `CORS_ALLOWED_ORIGINS` | browser origins allowed to call the API |
| `EMAIL_PROVIDER` | `log` or `smtp` |
| `EMAIL_FROM_ADDRESS`, `EMAIL_FROM_NAME`, `EMAIL_REPLY_TO`, `EMAIL_SUPPORT_CONTACT` | invitation email identity |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP credentials |
| `STORAGE_PROVIDER`, `STORAGE_LOCAL_ROOT` | document storage |
| `MAX_FILE_SIZE`, `MAX_FILE_SIZE_BYTES` | upload limit (default 10 MB) |
| `SEED_ENABLED`, `SEED_HR_EMAIL`, `SEED_HR_PASSWORD`, `SEED_SAMPLE_CANDIDATES` | development seeding |

---

## 7. Test credentials

With the `dev` profile:

| Field | Value |
| --- | --- |
| URL | <http://localhost:5173/login> |
| Email | `admin@cloudfuze.com` |
| Password | `Cloudfuze@123` |

Override with `SEED_HR_PASSWORD`. If you leave it blank, a strong password is
generated and printed **once** in the startup log. Set `SEED_ENABLED=false`
outside development.

`SEED_SAMPLE_CANDIDATES=true` (on by default in the `dev` profile) also creates a
few demo candidates and logs their portal links.

---

## 8. Testing the candidate portal

1. Sign in as HR and click **New candidate**.
2. Fill in name, email, role, department and tick the documents you require.
3. Submit. The backend creates the candidate, generates one token, stores only
   its SHA-256 hash, and sends one invitation email.
4. Because `EMAIL_PROVIDER=log` in development, the email (with the link) is
   printed in the backend console. The link is also shown once in the UI dialog,
   with a copy button.
5. Open that link - `http://localhost:5173/upload/<token>` - ideally in a private
   window, to experience it as the candidate.
6. Upload documents, then open the candidate from the **Pipeline** in the HR
   console to verify or reject them. Rejections reopen only that document.
7. When every mandatory document is verified, the candidate moves to
   `docs_approved` and the offer unlocks. Publish an offer letter in
   **Offer Letters**, then accept it from the portal. The candidate reaches
   `offer_accepted` and sees **Onboarding Complete**; HR sees the completed
   pipeline row and the full audit trail.

Lost links: **Resend invite** or **New portal link** issues a fresh token and
invalidates the previous one immediately.

### Automated tests

```bash
cd backend  && ./mvnw test     # 27 integration tests, H2 in PostgreSQL mode
cd frontend && npm run test    # 27 component/unit tests (Vitest)
```

The backend suite drives the real HTTP API and covers: HR login and JWT
protection, candidate creation, token hashing, invalid/expired token rejection,
upload and re-upload rules, verify/reject with reasons, the
all-mandatory-verified rule, the offer gate before and after approval, offer
acceptance completing onboarding, duplicate acceptance, that no bond route
exists on either API, token regeneration invalidating the old link, pipeline
search and filtering, and that download URLs never leak storage keys.

---

## 9. Important API endpoints

### HR (JWT: `Authorization: Bearer <token>`)

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/login` | Sign in, returns JWT |
| `GET` | `/api/auth/me` | Current HR user |
| `GET` | `/api/hr/dashboard/stats` | Active candidates, documents pending, awaiting review, onboarding complete |
| `GET` | `/api/hr/candidates?q=&stage=&page=&size=` | Pipeline, searchable and filterable |
| `POST` | `/api/hr/candidates` | Create candidate + token + one invitation email |
| `GET` | `/api/hr/candidates/{id}` | Candidate, documents, offer, audit trail |
| `GET` | `/api/hr/candidates/{id}/documents` | Document review list |
| `POST` | `/api/hr/documents/{documentId}/verify` | Verify one document |
| `POST` | `/api/hr/documents/{documentId}/reject` | Reject one document (reason required) |
| `GET` | `/api/hr/documents/{documentId}/file` | Stream a submitted document |
| `POST` | `/api/hr/candidates/{id}/offer` | Publish/replace offer letter (multipart) |
| `GET` | `/api/hr/candidates/{id}/offer` · `/offer/file` | Offer status / file |
| `GET` | `/api/hr/candidates/{id}/audit` | Full audit trail |
| `POST` | `/api/hr/candidates/{id}/resend-invite` | New link, emailed, old link dead |
| `POST` | `/api/hr/candidates/{id}/regenerate-token` | Same, flagged as a regeneration |

### Candidate (portal token in the path, no password)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/portal/{token}` | Identity, stage, current step, gate reasons |
| `GET` | `/api/portal/{token}/documents` | Requested documents and their statuses |
| `POST` | `/api/portal/{token}/documents/{documentType}` | Upload / re-upload (multipart) |
| `GET` | `/api/portal/{token}/documents/{documentId}/file` | Own document only |
| `GET` | `/api/portal/{token}/offer` | Offer - **403 before `docs_approved`** |
| `POST` | `/api/portal/{token}/offer/view` | Record that the offer was viewed |
| `POST` | `/api/portal/{token}/offer/accept` | Accept → `offer_accepted`, onboarding complete |
| `GET` | `/api/meta` | Document types, stages, upload limits (public reference data) |

Every error uses one shape:

```json
{
  "timestamp": "2026-08-20T18:07:11.482Z",
  "status": 403,
  "code": "STAGE_FORBIDDEN",
  "message": "Your offer letter is not available yet. HR is still reviewing your documents - the offer unlocks once every required document is approved.",
  "path": "/api/portal/<token>/offer",
  "details": { "currentStage": "docs_pending", "requiredStage": "docs_approved" }
}
```

---

## 10. Project structure

```
hr-onboarding-portal/
├── docker-compose.yml            PostgreSQL (+ optional Mailpit)
├── .env.example                  every environment variable
├── backend/
│   ├── mvnw / mvnw.cmd           Maven wrapper (no global Maven needed)
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/cloudfuze/onboarding/
│       │   ├── controller/       AuthController, HrCandidateController,
│       │   │                     HrDocumentController, HrOfferController,
│       │   │                     PortalController, MetadataController
│       │   ├── service/          CandidateService, DocumentService, OfferService,
│       │   │                     PortalService, DocumentApprovalService,
│       │   │                     DashboardService, AuthService, StageGuard,
│       │   │                     FileUploadValidator, OnboardingMapper
│       │   ├── repository/       Spring Data JPA repositories + specifications
│       │   ├── model/            Candidate, CandidateDocument, CandidateProfile,
│       │   │                     Offer, AuditLog, HrUser, Stage, enums
│       │   ├── dto/              request/response records with validation
│       │   ├── security/         SecurityConfig, JwtService, JwtAuthenticationFilter,
│       │   │                     PortalTokenService, HrPrincipal
│       │   ├── storage/          FileStorageService + LocalFileStorageService
│       │   ├── email/            EmailService, Logging + SMTP, InvitationMailComposer
│       │   ├── audit/            AuditService
│       │   ├── exception/        ApiException hierarchy + GlobalExceptionHandler
│       │   └── config/           AppProperties, JwtProperties, StorageProperties,
│       │                         EmailProperties, WebConfig, DataSeeder
│       ├── main/resources/       application.properties, -dev, -example
│       └── test/                 OnboardingWorkflowIntegrationTest
└── frontend/
    └── src/
        ├── components/           ui/ (Button, Modal, StatusPill, FileDropzone, …)
        │                         hr/ (CandidateTable, DocumentReviewPanel,
        │                         OfferPanel, PortalLinkCard, AuditTimeline, …)
        │                         portal/ (ProgressTracker, LockedPanel)
        ├── pages/                hr/ (Login, Dashboard, Candidates,
        │                         CandidateDetail, Offers) · portal/ (Overview,
        │                         Details, Documents, Review, Offer)
        ├── layouts/              HrLayout (sidebar/topbar), PortalLayout
        ├── services/             apiClient, authService, hrService, portalService
        ├── context/              AuthContext, ToastContext
        ├── hooks/                useAsync
        ├── routes/               AppRoutes, ProtectedRoute
        ├── utils/                format, status
        └── test/                 Vitest suites
```

### Frontend routes

| Route | Who |
| --- | --- |
| `/login` | HR |
| `/dashboard` `/candidates` `/candidates/:id` `/offers` | HR (JWT required) |
| `/upload/:token` | candidate entry point from the email |
| `/portal/:token` `/portal/:token/details` `/portal/:token/documents` `/portal/:token/review` `/portal/:token/offer` | candidate |

---

## 11. How the flow works end to end

1. **HR creates a candidate.** `CandidateService.create` writes the candidate at
   stage `docs_pending`, asks `PortalTokenService` for 32 bytes of
   `SecureRandom`, stores **only the SHA-256 hash** plus an expiry, and sends
   exactly one invitation email covering both stages. The raw link is
   returned once so HR can copy it; it is never retrievable again.
2. **Candidate opens the link.** `PortalService.authenticate` hashes the token,
   looks it up, and enforces expiry. The overview response carries the stage and
   only the step the candidate is actually on - finished and unreachable stages
   are left out entirely, so the UI never invents an unlocked stage.
3. **Documents.** The candidate only sees what HR requested. `DocumentService`
   validates the file type and size, stores it under an opaque key, and records
   `document_uploaded`. Uploads are refused unless the stage is `docs_pending`
   and the slot is empty or rejected.
4. **HR review.** Verify or reject each document; rejection requires a reason the
   candidate sees. Only the rejected document reopens, and a re-upload bumps its
   version back to `submitted`.
5. **The offer gate.** When the last mandatory document is verified,
   `evaluateDocumentApproval` moves the candidate to `docs_approved` and records
   `documents_approved`. Before that, every offer endpoint returns
   `403 STAGE_FORBIDDEN`.
6. **Offer acceptance.** The candidate reads the offer (status `sent` → `viewed`),
   then accepts by typing their name and confirming. `OfferService.accept` stores
   who accepted, when, and from which IP, moves the stage to `offer_accepted`,
   sets `completedAt`, and audits `offer_accepted` + `onboarding_completed`.
   This is the final transition; accepting twice is refused with
   `409 OFFER_ALREADY_ACCEPTED`.
7. **Completion.** The portal shows **Onboarding Complete**; HR sees the
   completed pipeline row and the whole audit trail (actor, timestamp, IP,
   document version, metadata).

---

## 12. Security summary

- Candidate tokens: 32 bytes of `SecureRandom`, URL-safe base64; only the
  SHA-256 hash is persisted (unique, indexed); expiry enforced on every call;
  regeneration overwrites the hash, killing the old link instantly.
- Two independent auth paths: HR APIs need a JWT (`ROLE_HR`); candidate APIs are
  authenticated purely by the path token and authorised by stage.
- Every gated action is enforced server-side by `StageGuard` - React only renders
  what the backend already decided.
- Documents are never publicly reachable: storage keys never leave the backend,
  downloads stream through authorised controllers, and a candidate can only
  fetch their own files.
- BCrypt (strength 12) for HR passwords, stateless sessions, CSRF disabled by
  design (no cookies), CORS limited to configured origins.
- No personal data in URLs or query strings; the portal token is a path segment.
- Append-only `AuditLog` for every significant event.

---

## 13. What still needs real CloudFuze configuration

1. **SMTP credentials.** `EMAIL_PROVIDER=log` prints invitations to the log.
   Set `EMAIL_PROVIDER=smtp` with real `MAIL_*` values (and a verified sender)
   to actually send email.
2. **Document storage.** `LocalFileStorageService` writes to disk, which is fine
   for one node. Implement `FileStorageService` for S3 / Azure Blob / CloudFuze
   secure storage and register it - no calling code changes.
3. **Schema migrations.** `ddl-auto=update` generates the schema (and indexes) in
   development. Adopt Flyway/Liquibase and switch to `validate` before production.
4. **HTTPS, real host names and email deliverability**: set `FRONTEND_URL` to the
   public HTTPS origin so portal links are correct, and list it in
   `CORS_ALLOWED_ORIGINS`.

### Assumptions worth flagging

- **HR may prepare the offer at any time.** Candidate visibility is still gated
  (the offer needs `docs_approved`); letting HR upload early avoids a pointless
  wait once documents clear.
- **Resend invite issues a new token.** Since only the hash is stored, the
  original link cannot be re-derived - so resending necessarily produces a fresh
  link and invalidates the old one (matching the security requirement).
- **Optional documents do not gate the stage.** Only documents marked mandatory
  must be verified before `docs_approved`.
- **Offer acceptance ends onboarding.** There is no signing stage: once the
  candidate accepts, `offer_accepted` is terminal and the offer is locked
  against replacement.
