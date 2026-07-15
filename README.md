# Job Scanner

Full-stack Job Scanner for scanning/importing jobs, storing offers in PostgreSQL, generating German Anschreiben drafts with Spring AI, and sending applications only after explicit manual confirmation from the frontend.

## Stack

- Backend: Java 21, Spring Boot, Spring AI, Spring Data JPA, Liquibase, Maven
- Database: PostgreSQL
- Frontend: Vue 3 + TypeScript + Vite
- API: REST

## Safety Rule

The application never sends CVs or Anschreiben automatically. AI can only create drafts. A draft is sent only when the user clicks `Send application with CV`, which calls `POST /api/drafts/{id}/send`. The current sender is `MockApplicationSender`, so the first implementation only marks the draft as `SENT` and the job as `APPLIED`.

## Run With Docker

```bash
docker compose up --build
```

Docker activates the Spring `docker` profile automatically. That profile uses the Compose service name `postgres` for the database connection.

Services:

- Frontend: http://localhost:8081
- Backend: http://localhost:8088
- PostgreSQL: localhost:5432

Optional AI configuration:

```bash
export OPENAI_API_KEY=...
export OPENAI_MODEL=gpt-5.5
docker compose up --build
```

Use a model that is enabled for your OpenAI project. If you see `model_not_found` or `does not have access to model`, change `OPENAI_MODEL` to a model returned by the OpenAI models API for your project. Do not set `OPENAI_TEMPERATURE` or `SPRING_AI_OPENAI_CHAT_OPTIONS_TEMPERATURE`; the app intentionally leaves temperature unset because some models only accept the provider default.

Without a working OpenAI key, the backend falls back to a deterministic draft and match explanation so the app remains testable.

## Local Development

By default, no Spring profile is active. The backend uses [application.yml](backend/src/main/resources/application.yml), which is intended for running without Docker and points at a PostgreSQL instance on `localhost`.

Start PostgreSQL:

```bash
docker compose up postgres
```

Run backend:

```bash
cd backend
mvn spring-boot:run
```

Run backend with the Maven `docker` profile:

```bash
cd backend
mvn -Pdocker spring-boot:run
```

If you want to run the backend against the Docker Compose database while still starting the backend from your IDE or Maven, use the default local profile and point `SPRING_DATASOURCE_URL` at `localhost`.

Run frontend:

```bash
cd frontend
npm install
npm run dev
```

Frontend dev server: http://localhost:5173

## First Test Run

1. Open the frontend.
2. Go to `Search Criteria` and review the seeded `Java Spring Remote` criteria.
3. Go to `Jobs`.
4. Click `Scan starten`.
5. Open a job or click `Anschreiben`.
6. Edit and save the generated Anschreiben.
7. Click `Send application with CV` only when you want to mark the mock application as sent.

## REST API

- `GET /api/jobs`
- `GET /api/jobs/{id}`
- `POST /api/jobs/scan`
- `POST /api/jobs/{id}/generate-anschreiben`
- `GET /api/jobs/{id}/draft`
- `PUT /api/drafts/{id}`
- `POST /api/drafts/{id}/send`
- `GET /api/search-criteria`
- `POST /api/search-criteria`
- `PUT /api/search-criteria/{id}`

## Project Structure

```text
backend/src/main/java/com/prognimak/jobscanner
  ai
  config
  controller
  dto
  entity
  mapper
  repository
  scanner
  sender
  service

frontend/src
  api
  components
  pages
  types
```

## Scanner Implementations

- `MockJobSourceScanner`: returns mock jobs for first tests.
- `FreelancermapScanner`: fetches freelancermap search pages, extracts project detail links, parses detail pages with JSoup, and returns normalized job offers.
- `AdzunaScanner`: calls the official Adzuna REST API and maps JSON job ads into normalized job offers.
- `ArbeitsagenturScanner`: calls the Bundesagentur fuer Arbeit Jobsuche JSON endpoints, fetches detail records by reference number, and maps public job ads into normalized job offers.
- `MeinestadtScanner`: fetches meinestadt.de job result pages, parses rendered result cards, fetches detail pages, and imports only locally matching jobs.
- `GlassdoorScanner`: fetches Glassdoor search result pages, extracts job detail links, parses detail pages with JSoup, and returns normalized job offers when Glassdoor provides server-rendered HTML.
- `StepStoneScanner`: source adapter skeleton.

Real scanners should keep source-specific parsing, rate limiting, and legal/policy handling inside scanner implementations, not controllers.

To scan freelancermap manually, create or update a search criterion with:

```text
sourceWebsite = freelancermap
```

To scan Glassdoor manually, create or update a search criterion with:

```text
sourceWebsite = glassdoor
```

To scan Arbeitsagentur manually, create or update a search criterion with:

```text
sourceWebsite = arbeitsagentur
```

The Arbeitsagentur scanner does not need personal API credentials. It uses the public client id header documented by `bundesAPI`:

```bash
ARBEITSAGENTUR_API_KEY=jobboerse-jobsuche
ARBEITSAGENTUR_MAX_PAGES=1
ARBEITSAGENTUR_RESULTS_PER_PAGE=25
ARBEITSAGENTUR_MAX_DETAILS=25
ARBEITSAGENTUR_PUBLISHED_WITHIN_DAYS=100
```

To scan meinestadt.de manually, create or update a search criterion with:

```text
sourceWebsite = meinestadt
```

The meinestadt.de scanner is HTML-based and intentionally conservative. It does not use browser automation or bypass login/CAPTCHA/security layers. It parses public result/detail HTML and applies local keyword filtering before importing:

```bash
MEINESTADT_MAX_PAGES=1
MEINESTADT_MAX_DETAILS=20
MEINESTADT_MAX_CANDIDATES=30
MEINESTADT_MAX_PARALLEL_DETAIL_REQUESTS=4
MEINESTADT_REQUEST_DELAY_MS=500
```

One search criterion can scan multiple sources. The frontend stores the selected sources as a comma-separated value:

```text
sourceWebsite = arbeitsagentur,adzuna,freelancermap,meinestadt
```

To scan Adzuna, register for API credentials at https://developer.adzuna.com/ and set:

```bash
ADZUNA_APP_ID=...
ADZUNA_APP_KEY=...
ADZUNA_COUNTRY=de
ADZUNA_RESULTS_PER_PAGE=10
ADZUNA_REQUEST_TIMEOUT_SECONDS=15
```

Then use:

```text
sourceWebsite = adzuna
```

### Getting Adzuna API Credentials

1. Open https://developer.adzuna.com/.
2. Create an Adzuna developer account or sign in.
3. Register a new application in the developer dashboard.
4. Copy the generated `app_id` and `app_key`.
5. Set the credentials before starting the backend:

```bash
export ADZUNA_APP_ID=your_app_id
export ADZUNA_APP_KEY=your_app_key
export ADZUNA_COUNTRY=de
```

For Docker:

```bash
ADZUNA_APP_ID=your_app_id ADZUNA_APP_KEY=your_app_key docker compose up --build
```

For IntelliJ, add these environment variables to the backend run configuration:

```text
ADZUNA_APP_ID=your_app_id
ADZUNA_APP_KEY=your_app_key
ADZUNA_COUNTRY=de
```

Useful environment settings:

```bash
FREELANCERMAP_MAX_PAGES=1
FREELANCERMAP_MAX_DETAILS=10
FREELANCERMAP_MAX_CANDIDATES=20
FREELANCERMAP_MAX_PARALLEL_DETAIL_REQUESTS=6
FREELANCERMAP_REQUEST_DELAY_MS=250
FREELANCERMAP_REQUEST_TIMEOUT_SECONDS=15
```

The freelancermap scanner sends `keyword` to freelancermap as the portal search query and trusts the returned result set for keyword relevance. Local filtering is only applied for structured fields such as remote type, contract type, and location.

Glassdoor can serve limited, login-gated, JavaScript-heavy pages, or a `Security | Glassdoor` 403 page depending on request context. The scanner works with public server-rendered result cards and skips cleanly when Glassdoor blocks the request.

By default, the scan does not run Spring AI matching for every imported job because that makes API-based scans feel slow. Generate an Anschreiben from the job details page to run AI for a selected job. If you want match scoring during every scan, set:

```bash
AI_MATCHING_DURING_SCAN=true
```
