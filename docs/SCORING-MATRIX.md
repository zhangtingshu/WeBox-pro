# Scoring-matrix mapping (PRD Appendix A → implementation evidence)

This document is a self-audit against the interviewer's rubric. Every row names the concrete file
that can be inspected or the endpoint that can be exercised during the demo.

## A. Feature completeness — Tier 1 (30) + Tier 2 (20) = 50 baseline

| PRD section | Feature | Implementation | Evidence |
|-------------|---------|----------------|----------|
| 3.1 | Register / login, email format + uniqueness, password ≥8 with letters+digits, hashed, refresh-safe session, roles | `AuthService`, `PasswordHasher`, `JwtUtil`, `AuthInterceptor` | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`; `SecurityUtilsTest` |
| 3.2 | Daily menu cards (image/name/price/cuisine), detail with protein, allergens, spice, customization with live price, multi-select cuisine filter, keyword search | `MenuService`, `DishAssembler`, `DishDetail` component | `GET /api/menu?categories=Chinese,Japanese&keyword=...`; `GET /api/dishes/{id}` |
| 3.3 | Cart, quantity ±, remove, live total, distinct lines per configuration, add throttling | `App.jsx` cart state, `lineKey`, `PricingService` | Cart page; `PricingServiceTest` |
| 3.4 | Checkout, delivery date/period/address, order success page, order history + detail, cancel `Pending` only, English statuses | `OrderService`, `OrderController`, `CheckoutPage`, `OrdersPage` | `POST /api/orders`, `GET /api/orders`, `POST /api/orders/{id}/cancel` |
| 4.1 | Allergen flags (warn, never filter), cuisine/spice/taste preferences, budget hint, "Recommend for me" sorting + highlighting | `PreferenceService`, `AllergenMatcher`, `MenuService.score` | Preferences page; `GET /api/menu?recommend=true`; `CacheAndAllergenTest` |
| 4.2 | ≤5 portions **total**, 10:00/15:00 cutoffs with automatic switch, one active order per meal (button becomes `View Existing Order`) | `OrderRules`, `OrderService`, `OrderController.active` | `OrderRulesTest`; `GET /api/orders/active`; `POST /api/orders` |
| 4.3 | Admin Console with distinct navigation, dish CRUD, image upload, on/off shelf, daily menu with per-day supply, **today's orderable seed menu** | `ConsoleController`, `ConsoleService`, `Console.jsx`, `V2__seed.sql` | `/api/console/**` (403 for employees); `docs/DEMO-SCRIPT.md` |

## Tier 3 (optional +10)

| PRD section | Feature | Evidence |
|-------------|---------|----------|
| 5.1 | Remaining stock per dish, ≤3 visual cue, sold-out cannot be ordered, atomic no-oversell deduction, **SSE live update without manual refresh**, re-check at submit with dish names, restore on cancel | `DailyMenuRepository.deductStock` (conditional UPDATE), `SseHub`, `App.jsx` SSE effect, `OrderService.cancel` |
| 5.2 | English natural-language AI assistant, streaming response, dish **cards** with personalised reasons, respects preferences / allergens / last 7 days / sold-out | `AiRecommendService`, `AiController`, `AiAssistant` component, `POST /api/ai/recommend` |
| 5.3 | Dashboard: today's orders/revenue/status counts, Top 10 bar chart, Lunch vs Dinner, 7-day trend line, low-stock list, manual refresh, all from real orders | `DashboardService` native aggregates, `DashboardSection` + SVG charts |

## A.1 Front-end P6+ (20)

| Point | Where it is solved |
|-------|--------------------|
| Responsive / multi-screen (4) | `styles.css` grid + `@media` at 960 px / 640 px, fixed bottom tab bar on phones, fluid dish grid, horizontally scrollable tables |
| Paging / list performance (4) | Server-side `PageRequest` (12 per page, max 48), lazy image loading, 30 seeded dishes with growth headroom, batched option-group queries (no N+1) |
| Debounce / throttle (4) | `useDebounced` 300 ms on search + Console search; 200 ms throttle on "Add to cart"; submit button disabled while in flight |
| Price precision (4) | `Money` (integer cents) + JS `yuan()` / `yuanToCents()` string parsing; `DECIMAL(10,2)` in MySQL; `MoneyTest` |
| Input validation & limits (4) | `maxLength` on email (200), address (200), search (50), prompt (500); client regex + server Bean Validation, `TextUtil.clean`, enum whitelists |

## A.2 Back-end P6+ (20)

| Point | Where it is solved |
|-------|--------------------|
| Cache strategy (4) | `MenuCache`: 30 s TTL, read-through, hit/miss metrics (`/api/console/cache-stats`), **write-invalidate** on every stock/catalogue/preference write |
| Idempotent submit (4) | `Idempotency-Key` + `UNIQUE(orders.idempotency_key)` + duplicate-race recovery; `OrderIdempotencyTest` |
| SQL-injection protection (4) | JPA `Specification` parameter binding everywhere, LIKE `%`/`_` escaping with explicit escape char, no concatenated SQL, whitelisted enums |
| RBAC (4) | `AuthInterceptor` + `@RequireRole("ADMIN")` on all Console endpoints; employee token → 403; access token never in URL/localStorage |
| Concurrency / no oversell (4) | Atomic conditional `UPDATE daily_menu ... remaining_qty >= :qty` inside one transaction, `SELECT ... FOR UPDATE` on the user row, rollback on any shortage |

## Hard requirements and "cascade-to-zero" defences

| Requirement | Status |
|-------------|--------|
| Complete runnable frontend + backend | ✅ Docker Compose, one command |
| JDK 17 + Spring Boot | ✅ `maven:3.9-eclipse-temurin-17` build stage, `eclipse-temurin:17-jre-alpine` runtime |
| Independent MySQL process (no H2/SQLite) | ✅ dedicated `mysql:8.0` service + healthcheck + `/ready` probe |
| All user-facing content in English | ✅ UI, seed data, errors, AI reasons, dashboard |
| **Full raw AI conversation export** | ✅ `ai-conversations/` holds **only** the tool-native `*-native.jsonl` — a verbatim, unedited copy of the coding tool's own session log (every prompt, tool call, raw result and failed attempt, from the first instruction to the final verification) — plus a short provenance README. No screenshots, excerpts, reconstructed transcript, derived appendix or after-the-fact summary, exactly as §7.2 demands. |
| Ordering works end-to-end (Tier 3 bonus not zeroed) | ✅ smoke-tested register → menu → cart → order → stock → cancel → dashboard, see `docs/TESTING.md` |
| Docs: README / API / architecture / tests | ✅ `README.md`, `docs/*` |
| Long-term maintainability (§7.3) | ✅ layered code, versioned SQL migrations, env-only config, typed errors, structured logs, graceful shutdown, fail-fast validation |
