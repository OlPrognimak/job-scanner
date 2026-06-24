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

Services:

- Frontend: http://localhost:8081
- Backend: http://localhost:8080
- PostgreSQL: localhost:5432

Optional AI configuration:

```bash
export OPENAI_API_KEY=...
docker compose up --build
```

Without a working OpenAI key, the backend falls back to a deterministic draft and match explanation so the app remains testable.

## Local Development

Start PostgreSQL:

```bash
docker compose up postgres
```

Run backend:

```bash
cd backend
mvn spring-boot:run
```

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
- `FreelancermapScanner`: source adapter skeleton.
- `StepStoneScanner`: source adapter skeleton.

Real scanners should keep source-specific parsing, rate limiting, and legal/policy handling inside scanner implementations, not controllers.
