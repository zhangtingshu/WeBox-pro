# Test report

Everything below was executed against the running Docker stack on 2026-11-11 (Docker 28.5.1,
macOS). No embedded/in-memory database is used anywhere, including in tests.

## 1. Unit tests — JUnit 5 (19 tests, all passing)

```bash
docker run --rm -v "$PWD/backend":/build -v webox-m2:/root/.m2 \
  -w /build maven:3.9-eclipse-temurin-17 mvn -B test
```

```
[INFO] Running com.webox.OrderIdempotencyTest   Tests run: 1, Failures: 0, Errors: 0
[INFO] Running com.webox.CacheAndAllergenTest   Tests run: 2, Failures: 0, Errors: 0
[INFO] Running com.webox.SecurityUtilsTest      Tests run: 3, Failures: 0, Errors: 0
[INFO] Running com.webox.OrderRulesTest         Tests run: 6, Failures: 0, Errors: 0
[INFO] Running com.webox.MoneyTest              Tests run: 3, Failures: 0, Errors: 0
[INFO] Running com.webox.PricingServiceTest     Tests run: 4, Failures: 0, Errors: 0
[INFO] Results: Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| Test class | What it locks down |
|------------|--------------------|
| `MoneyTest` | Integer-cent arithmetic, ¥ formatting, `DECIMAL` round-trip, add-on totals |
| `OrderRulesTest` | 10:00/15:00 cutoffs, Lunch→Dinner, both closed→next-day Lunch, future dates, 5-portion limit, default period |
| `PricingServiceTest` | Add-on pricing, required groups, duplicate/invalid option ids, `maxSelect` |
| `SecurityUtilsTest` | PBKDF2 salt + verification, JWT integrity/tampering, LIKE escaping, enum whitelist, input length caps |
| `CacheAndAllergenTest` | Cache TTL expiry, write-invalidate, advisory allergen warning text |
| `OrderIdempotencyTest` | Repeated submit returns the same order and never opens a second transaction (Mockito) |

Unit tests are deliberately DB-free and fast; integration behaviour is covered by the real MySQL
smoke tests below (no mocked service layer).

## 2. Frontend build

```bash
docker run --rm -v "$PWD/frontend":/app -w /app node:20-alpine \
  sh -c "npm install --no-audit --no-fund && npm run build"
```

```
vite v5.4.21 building for production...
✓ 35 modules transformed.
dist/index.html                   0.56 kB │ gzip:  0.33 kB
dist/assets/index-E8r30twk.css   23.16 kB │ gzip:  5.42 kB
dist/assets/index-DOM_XdTQ.js   199.11 kB │ gzip: 61.43 kB
✓ built in 3.59s
```

## 3. Startup smoke

```bash
docker compose up -d --build
```

```
NAME             STATUS                    PORTS
webox-mysql      Up (healthy)              0.0.0.0:3306->3306/tcp
webox-backend    Up                        0.0.0.0:8080->8080/tcp
webox-frontend   Up                        0.0.0.0:8081->80/tcp

curl http://localhost:8080/health  -> {"status":"UP","service":"webox-backend"}
curl http://localhost:8080/ready   -> {"status":"READY","database":"mysql","probe":1}
curl -o /dev/null -w '%{http_code}' http://localhost:8081/           -> 200
curl -o /dev/null -w '%{http_code}' http://localhost:8081/api/meta   -> 200  (nginx proxy)
```

## 4. End-to-end smoke — 23/23 passing

```bash
bash scripts/smoke_test.sh
```

```
== Result: 23 passed, 0 failed ==     (run 1)
== Result: 23 passed, 0 failed ==     (run 2, immediately repeated)
```

The script is repeatable: it cancels Pending demo orders first and asserts stock **relative** to the
observed baseline (`12 -> 10`, then back to `12`) instead of hard-coding a number.

Key checks and their real outputs (final run, after the stock-message fix):

| Check | Result |
|-------|--------|
| `/health`, `/ready` | `UP` / `READY` (real MySQL `SELECT 1`) |
| Employee + Admin login, refresh restores session | PASS |
| Today's menu (seeded) | `11 dishes` for `Chinese,Japanese` filter; `6 hits` for `chicken` |
| Menu cache stats endpoint | PASS |
| RBAC | Employee → `HTTP 403` on `/api/console/dishes`; Admin → `HTTP 200` |
| Place order | `WB20261111-5E3EAF / Pending`, resolved meal `Dinner` (12:xx is past the 10:00 Lunch cutoff) |
| **Idempotent re-submit** | `WB20261111-5E3EAF == WB20261111-5E3EAF` |
| One active order per meal | `DUPLICATE_ORDER` |
| 5-portion limit | `RULE_VIOLATION` |
| Stock deducted | `remaining=10` (12 − 2) |
| My Orders / detail / dashboard | `2 order(s)`, matching orderNo, `today orders=2` |
| Cancel + restore | `Cancelled`, `remaining=12` |
| AI streaming | `47 SSE events`, `event:dish` ×3 + `event:done` |

Other manual probes:

```
GET /api/menu/stream    -> event:connected / data:{"message":"Stock updates connected."} / :ping
POST /api/ai/recommend  -> event:status, event:delta (word groups), event:dish ×3, event:done
GET /api/ai/status      -> {"engine":"local-ranking-engine","model":"built-in","streaming":true}
```

### Dish image regression (added after the image fix)

```bash
python3 scripts/sync_images.py --check   # 20 canonical photos + placeholder.svg bundled
python3 scripts/verify_images.py         # every seed URL resolves through nginx
```

```
== WeBox dish image check against http://localhost:8081 ==
PASS  /images/image_01.jpg · HTTP 200 image/jpeg 7449 bytes
... (image_02 … image_20 all HTTP 200 image/jpeg)
PASS  fallback placeholder.svg · HTTP 200 image/svg+xml
PASS  missing image returns 404 (no SPA fallback) · HTTP 404
== Result: 22 passed, 0 failed ==
```

Cross-checked through the authenticated API: `GET /api/menu?page=0&size=48` returned 30 items and
**30/30** `dish.imageUrl` values resolved to a real `image/*` response. The full smoke test was
re-run after the front-end rebuild and still reports `23 passed, 0 failed`.

## 5. Concurrency / no-oversell proof

```bash
python3 scripts/concurrency_test.py
```

Two different employees submit 3 portions of `Classic Beef Burger` (exactly 3 portions left)
simultaneously, each with its own idempotency key:

```
Remaining before concurrent orders: 3
Employee 1: HTTP 200 -> orderNo=WB20261111-E748F2 error=None
Employee 2: HTTP 409 -> error=STOCK_INSUFFICIENT
  message: Insufficient stock for: Classic Beef Burger (requested 3, only 0 available now).
           Please adjust your cart and submit again.
Remaining after concurrent orders: 0
PASS  No overselling: one order created, one rejected with STOCK_INSUFFICIENT, stock = 0
```

The atomic conditional `UPDATE ... WHERE remaining_qty >= :qty` plus single-transaction rollback
is what makes this deterministic; the loser never creates an order or a partial stock deduction.

## 6. Problems found and fixed during verification

| # | Symptom | Root cause | Fix |
|---|---------|------------|-----|
| 1 | `mvn package` failed: `Optional<OrderEntity> cannot be converted to OrderEntity` | Repository method returns `Optional`, service assigned directly | `.orElse(null)` at both idempotency lookups |
| 2 | `DashboardService` lossy conversion `long → int` | Native aggregate returns `Number` | explicit `(int)` cast for `remaining` |
| 3 | Lambda compile error: "must be final or effectively final" | `key` was re-assigned before being captured in `TransactionTemplate` lambda | single `final String key = ...` expression |
| 4 | Failed concurrent order reported `available 3` while stock was actually 0 | MySQL REPEATABLE READ: the entity read before the atomic UPDATE is a stale snapshot | added a locking **current read** (`SELECT ... FOR UPDATE`) for both the error message and the SSE stock value; verified the message now reads `only 0 available now` |
| 5 | First smoke run: `timeout: command not found` (macOS) | BSD userland has no GNU `timeout` | replaced with `curl --max-time`, script now portable |
| 6 | Concurrency script reused idempotency keys across runs, so the second request correctly returned the previous order | test artifact, not a product bug | unique `run_id` per execution + cleanup of both users' Pending orders |
| 7 | `docker compose build` failed in this sandbox with a buildx activity-file rename permission error | local sandbox restriction, not a project issue | images built with the classic builder (`DOCKER_BUILDKIT=0`) and started with `docker compose up -d --no-build`; the documented `docker compose up --build` path is unaffected on a normal machine |
| 8 | **All dish photos appeared as empty boxes in the running app** | The 20 copied photos kept their vendor names (`image_01_s-1770018186746.jpg`) while `V2__seed.sql` references `/images/image_01.jpg`; nginx's SPA fallback answered the 404 with `index.html` (HTTP 200 `text/html`), and `onError` merely hid the `<img>` | `scripts/sync_images.py` normalises the files to `image_NN.jpg`; a shared `DishImage` component falls back to `placeholder.svg`; `nginx.conf` now uses `^~ /api/` plus a static-asset regex so missing images return a real 404; added `scripts/verify_images.py` (22 checks) and verified 30/30 API image URLs |

## 7. What is intentionally not covered

- Browser-level E2E (Playwright/Cypress) — the responsive layout and interactions were verified
  manually in the browser preview; the API contract is covered by the smoke script.
- Multi-replica cache coherence — the design note in `docs/ARCHITECTURE.md` explains the Redis
  swap-in; a single backend instance is used for the demo.
