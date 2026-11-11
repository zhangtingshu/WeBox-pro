# WeBox — Employee Meal Ordering Platform

A production-minded full-stack implementation of the **AI Vibe Coding V3.0** PRD, built for the
1.5-hour full-stack challenge. The whole stack (MySQL, JDK 17 + Spring Boot API, React SPA + nginx)
runs in Docker; **no local JDK, Maven, Node or MySQL installation is required**.

---

## 1. Deliverables — PRD §7.2 checklist

The submission is organised **exactly around the six deliverables** required by PRD §7.2. Nothing
else is needed to evaluate it.

| # | PRD §7.2 deliverable | Location | How to verify |
|---|----------------------|----------|---------------|
| 1 | Runnable code — complete frontend + backend + standalone MySQL | `frontend/`, `backend/`, `docker-compose.yml`, `.env.example` | `docker compose up --build -d` → http://localhost:8081 |
| 2 | README — intro, stack rationale, zero-to-running steps | `README.md` (this file) | §2–§3 below, including default sign-in |
| 3 | API documentation — endpoint list, request/response formats | `docs/API.md` | curl examples + `scripts/smoke_test.sh` |
| 4 | Architecture description — module boundaries and data flow | `docs/ARCHITECTURE.md` | Mermaid component + order sequence diagrams |
| 5 | Tests — runnable, passing, covering core business logic | `backend/src/test/java/com/webox/`, `docs/TESTING.md`, `scripts/` | `mvn test` (19/19), `smoke_test.sh` (23/23) |
| 6 | **AI programming conversation record** — complete raw export, tool-native, no summaries | **`ai-conversations/`** | `session-2026-11-11-workbuddy-native.jsonl` (verbatim session log) + `README.md` (provenance & integrity) |

> **Item 6 is a graded red line.** PRD §7.2 rejects screenshots, excerpts, fragments and
> *after-the-fact summaries*. `ai-conversations/` therefore contains **only** the coding tool's
> native raw export plus a short provenance note — no reconstructed transcript, no derived
> appendix. The export spans the first user instruction through the final verification of this
> delivery, including every failed build, compile error and correction.

### Supplementary material (not part of the §7.2 checklist)

| File | Purpose |
|------|---------|
| `docs/PROMPT-DECK.md` | The extracted "how to talk to an AI coding tool" conversation script (R0–R11), ready to paste |
| `docs/SCORING-MATRIX.md` | Appendix A scoring points mapped to implementation evidence |
| `docs/DEMO-SCRIPT.md` | 5-minute demonstration walkthrough |
| `scripts/*.py`, `scripts/*.sh` | Repeatable verification: smoke, concurrency, image regression |

All user-facing content (UI, dish data, toasts, errors, AI reasons, dashboard labels) is **English**.

> **Default sign-in after the stack is up** — open http://localhost:8081 and use either account:
>
> | Role | Email | Password | What it opens |
> |------|-------|----------|---------------|
> | Employee (default) | `employee@webox.com` | `Employee@123` | Menu, cart, checkout, orders, preferences, AI assistant |
> | Administrator | `admin@webox.com` | `Admin@12345` | The above **plus** the separate Console (dishes, daily menu, dashboard) |
>
> These two accounts are seeded by `backend/src/main/resources/db/V2__seed.sql` with PBKDF2 salted
> hashes (no plaintext passwords). Change them before any non-demo deployment.

---

## 2. Technology choices and why

| Layer | Choice | Reason |
|-------|--------|--------|
| Frontend | React 18 + Vite 5, plain CSS, hand-built SVG charts | Component model fits a multi-view SPA; Vite gives a fast, reproducible build; zero chart/CDN dependency keeps the container offline-safe. Routing is state-based (one SPA, no router dependency). |
| Web server / proxy | nginx 1.27 (Alpine) | Serves the static build, reverse-proxies `/api/` to Spring Boot, disables proxy buffering for SSE. Same-origin browser calls avoid CORS complexity in production. |
| Backend | Java 17 + Spring Boot 3.2.5, Spring Data JPA, Bean Validation | Mandated by PRD §7.1. Controller → service → repository layering; business rules live in pure, unit-testable classes. |
| Database | **Standalone MySQL 8.0 process** (dedicated container), versioned SQL migrations | Mandated by PRD §7.1. No H2/SQLite anywhere, including tests (unit tests use no DB). `DECIMAL(10,2)` for money. |
| Cache | In-process TTL cache with write-invalidate (`MenuCache`) | PRD §7.1 makes Redis optional. A TTL cache removes the extra moving part while still protecting the 09:30–10:00 menu peak; every write path invalidates it. Redis can be swapped in behind the same interface. |
| Auth | PBKDF2-HMAC-SHA256 password hashes, HS256 short-lived access token (30 min, memory only), rotating refresh token in an **httpOnly cookie + DB**, RBAC interceptor | No plaintext passwords, no token in localStorage or URL, refresh survives a page reload. Employees get 403 on every Console endpoint. |
| Realtime | Server-Sent Events over `fetch()` streaming | Stock changes reach other tabs without a manual refresh; using fetch (not `EventSource`) keeps the Bearer token in the header. Heartbeats keep proxies alive. |
| AI | OpenAI-compatible streaming API (OpenAI / Ollama / vLLM) + built-in local ranking engine | The feature is genuinely runnable without a key on-site, and streams real model tokens when `LLM_API_KEY` is set. Hard constraints (allergens, last-7-days, sold-out) are enforced server-side, never delegated to the model. |
| Money | Integer cents end-to-end, converted to `DECIMAL(10,2)` only at persistence | Eliminates floating-point drift (¥22.50, add-ons, subtotals). |
| Infra | Docker Compose (MySQL + backend + frontend), all builds multi-stage | One command from a clean machine; dev machines need only Docker. |

---

## 3. Start from scratch (Docker only)

Prerequisites: Docker Desktop / Docker Engine with Compose v2 (verified on Docker 28.5.1).

```bash
cd WeBox-Submission
cp .env.example .env                 # optional: edit secrets, ports, LLM settings
docker compose up --build -d          # builds JDK17/Maven, Node, and starts MySQL 8
docker compose ps                     # wait until webox-mysql is (healthy)
```

Services:

| Service | URL | Notes |
|---------|-----|-------|
| Frontend SPA | http://localhost:8081 | All user flows |
| Backend API | http://localhost:8080 | `/health`, `/ready`, `/api/**` |
| MySQL 8 | localhost:3306 | DB `webox`, user `webox`, password from `DB_PASSWORD` |

```bash
curl http://localhost:8080/health           # {"status":"UP", ...}
curl http://localhost:8080/ready            # {"status":"READY","database":"mysql","probe":1}
docker compose logs -f backend              # structured JSON logs with requestId
docker compose down                        # stop; add -v to also drop the MySQL volume
```

On first start, `backend/src/main/resources/db/V1__schema.sql` and `V2__seed.sql` run automatically:
30 English dishes, customization groups, an admin and an employee, **today's and tomorrow's menus
with stock** (including a sold-out dish and two low-stock dishes for the demo). Migrations are
idempotent (`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`), so restarts never duplicate data.

### Dish images

The 20 photos from the PRD's `product_images/` folder are bundled in
`frontend/public/images/` as `image_01.jpg` … `image_20.jpg` — exactly the paths stored in
`dishes.image_url`. `image_01.jpg` … `image_10.jpg` map to the 10 PRD dishes, and dishes 21–30
reuse them so the 30-dish catalogue also exercises server-side paging. `placeholder.svg` is the
branded fallback shown by `DishImage` whenever a photo is missing or a custom upload is broken.

> The source bundle mixes plated dishes with a few packaged-drink shots, so a photo is a visual
> placeholder rather than a literal depiction of its dish name. Any dish can be given a better
> photo from the Console (image upload → `/api/uploads/…`, stored on the `uploads` volume).

Regenerate the canonical names from the original material bundle, or verify the running stack:

```bash
python3 scripts/sync_images.py            # copies ../product_images -> public/images/image_NN.jpg
python3 scripts/sync_images.py --check    # after cloning: just verify the bundled files
python3 scripts/verify_images.py          # 22 checks: every seed URL is a real image, 404 on missing
```

Missing images return a real `404` (nginx no longer falls back to `index.html` for static assets),
so the front-end fallback can actually trigger. See `docs/TESTING.md` §4 for the verification run.

### Demo accounts

The two seeded accounts are the ones in the **Default sign-in** box at the top of this README:
`employee@webox.com / Employee@123` and `admin@webox.com / Admin@12345`.

> Replace `JWT_SECRET` in `.env` before any non-demo deployment; startup validation refuses a
> missing/short secret. Set `COOKIE_SECURE=true`-equivalent (see `AuthController`) when serving HTTPS.

### Local development without Docker (optional)

The Docker path is the supported one. If you prefer hot reload: start only MySQL with
`docker compose up -d mysql`, then run `mvn spring-boot:run` in `backend/` (JDK 17 + Maven) and
`npm install && npm run dev` in `frontend/` (Node 20, Vite proxies `/api` to :8080). Add
`http://localhost:5173` to `CORS_ORIGINS` (already in `.env.example`).

---

## 4. AI recommendation configuration

The assistant works in two modes, both streaming (SSE):

```bash
# Option A — OpenAI-compatible cloud model
LLM_BASE_URL=https://api.openai.com/v1
LLM_API_KEY=sk-...
LLM_MODEL=gpt-4o-mini

# Option B — local Ollama on the host machine
LLM_BASE_URL=http://host.docker.internal:11434/v1
LLM_API_KEY=ollama            # any non-empty value
LLM_MODEL=llama3.2

# Option C — leave LLM_API_KEY empty (default)
# Built-in local ranking engine: streams personalised reasons and dish cards, no external call.
```

`GET /api/ai/status` reports the active engine. If a configured model is unreachable, the server
falls back to the local engine mid-stream instead of failing the request.

---

## 5. Tests

```bash
docker run --rm -v "$PWD/backend":/build -v webox-m2:/root/.m2 \
  -w /build maven:3.9-eclipse-temurin-17 mvn -B test
```

Pure JUnit 5 unit tests (no DB container needed) cover money precision, cutoff rules, the 5-portion
limit, option pricing/required groups, allergen matching, password hashing, JWT integrity, LIKE
escaping, cache TTL/invalidation and idempotent submit. **19/19 pass.**

End-to-end checks (both repeatable, against the real MySQL stack):

```bash
bash scripts/smoke_test.sh          # 23 checks: auth, menu, RBAC, idempotency, stock, cancel, dashboard, AI SSE
python3 scripts/concurrency_test.py # two employees race for the last 3 portions: no overselling
python3 scripts/verify_images.py    # 22 checks: all seeded dish photos resolve, missing image -> 404
```

See `docs/TESTING.md` for the full report with real command output.

---

## 6. Repository layout

```
WeBox-Submission/
├── docker-compose.yml          # MySQL + backend + frontend, volumes, healthchecks
├── .env.example                # all environment variables (placeholders only)
├── backend/                    # JDK 17 + Spring Boot 3 (multi-stage Dockerfile)
│   └── src/main/
│       ├── java/com/webox/{config,common,security,entity,repository,dto,service,controller}
│       └── resources/{application.yml, db/V1__schema.sql, db/V2__seed.sql}
│   └── src/test/java/com/webox/
├── frontend/                   # React + Vite (+ nginx.conf, Dockerfile, 20 dish photos + placeholder)
├── scripts/                    # smoke_test.sh, concurrency_test.py, sync_images.py, verify_images.py
├── docs/                       # §7.2 items 3/4/5: API, architecture, testing (+ supplementary scoring/deck/demo)
└── ai-conversations/           # §7.2 item 6: tool-native RAW conversation export + provenance README (no summaries)
```

---

## 7. Known limitations / next steps

- Order status transitions beyond `Pending → Cancelled` are admin-driven in a real deployment;
  the status enum already supports `Confirmed`/`Completed` and the dashboard counts them.
- The in-process cache is per JVM instance; with multiple backend replicas, swap `MenuCache` for
  Redis (pub/sub invalidation) — the call sites do not change.
- Uploaded images live on a Docker volume; object storage (S3/MinIO) is the production path.
- Add Flyway/Liquibase if the migration set grows beyond a handful of versioned scripts.
- Rate limiting/WAF and HTTPS termination belong at the ingress layer.
