# WeBox REST API

Base URL: `http://localhost:8080/api` (the SPA calls the same-origin `/api` path through nginx).
All request/response bodies are JSON unless noted. **All amounts are integer cents** (`2250` = ¥22.50).
Every user-facing string, including error messages, is English.

## Conventions

| Item | Rule |
|------|------|
| Success envelope | `{ "success": true, "data": <T>, "error": null, "requestId": "a1b2c3d4" }` |
| Error envelope | `{ "success": false, "data": null, "error": { "code": "STOCK_INSUFFICIENT", "message": "..." }, "requestId": "..." }` |
| Auth header | `Authorization: Bearer <accessToken>` (30 min, held in browser memory only) |
| Refresh token | `webox_refresh` httpOnly cookie, path `/api/auth`, rotated on every refresh, stored hashed in MySQL |
| Idempotency | `Idempotency-Key: <stable key per checkout>` on `POST /orders` |
| Correlation | `X-Request-ID` echoed in the response and every structured log line |
| Errors | 400 validation · 401 unauthenticated · 403 wrong role · 404 not found · 409 conflict · 422 rule violation · 500 internal. Stack traces are never returned. |

`ErrorCode` values: `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `DUPLICATE_EMAIL`,
`DUPLICATE_ORDER`, `STOCK_INSUFFICIENT`, `RULE_VIOLATION`, `ORDER_STATE_CONFLICT`,
`AI_UNAVAILABLE`, `INTERNAL_ERROR`.

---

## 1. Public

### `POST /auth/register`
```json
{ "email": "jane@company.com", "password": "Passw0rd", "fullName": "Jane Doe" }
```
Rules: valid email ≤200; password ≥8 with letters **and** digits; duplicate email → 409 `DUPLICATE_EMAIL`.
→ `data: { "id": 3, "email": "...", "fullName": "...", "role": "EMPLOYEE" }`

### `POST /auth/login`
```json
{ "email": "employee@webox.com", "password": "Employee@123" }
```
→ `data: { "accessToken": "eyJ...", "expiresInSeconds": 1800, "user": { ... } }`
＋ `Set-Cookie: webox_refresh=...; HttpOnly; SameSite=Lax; Path=/api/auth`.

### `POST /auth/refresh` (no body, cookie required)
Rotates the refresh token and returns the same `AuthView` shape. Called on every page load.

### `POST /auth/logout`
Revokes the refresh token and clears the cookie.

### `GET /meta`
→ `data: { categories[], allergens[], spiceLevels[], tastes[], mealPeriods[], orderStatuses[], maxPortions: 5, lowStockThreshold: 3, lunchCutoff: "10:00", dinnerCutoff: "15:00" }`

### `GET /uploads/{name}` → `image/*` bytes (public dish images)
### `GET /health` → `{ "status": "UP", "service": "webox-backend" }`
### `GET /ready` → `{ "status": "READY", "database": "mysql", "probe": 1 }` (`SELECT 1` against MySQL)

---

## 2. Authenticated — employee

### `GET /auth/me`
→ `data: { "user": { ... }, "preferences": { ... } }`

### `GET /preferences` / `PUT /preferences`
```json
{
  "allergens": ["Peanuts"], "cuisines": ["Chinese", "Light Meal"],
  "spiceLevel": "Mild", "taste": "Balanced",
  "budgetMinCents": 1500, "budgetMaxCents": 3500, "recommendEnabled": true
}
```
Enum values are whitelisted server-side. Updating preferences invalidates cached menu pages.

### `GET /menu`
Query: `categories=Chinese,Japanese` (multi-select), `keyword` (≤50, LIKE wildcards escaped),
`recommend=true|false` (omitted → use the user's saved preference), `page=0`, `size=12` (max 48),
`date=YYYY-MM-DD` (defaults to today).
```json
{
  "success": true,
  "data": {
    "items": [{
      "dish": {
        "id": 9, "name": "Classic Beef Burger", "description": "...",
        "priceCents": 3800, "category": "Western", "protein": "Beef",
        "allergens": ["Gluten", "Dairy"], "spiceLevel": "None",
        "imageUrl": "/images/image_09.jpg", "active": true,
        "optionGroups": [{
          "id": 6, "name": "Bun", "required": true, "maxSelect": 1,
          "items": [{ "id": 15, "name": "Plain", "extraCents": 0 },
                    { "id": 16, "name": "Whole Wheat", "extraCents": 0 }]
        }]
      },
      "remaining": 3, "lowStock": true, "soldOut": false,
      "recommended": true, "matchScore": 8
    }],
    "totalElements": 30, "totalPages": 3, "page": 0, "size": 12,
    "menuDate": "2026-11-11", "defaultMealPeriod": "Lunch",
    "defaultDeliveryDate": "2026-11-11",
    "cutoffNotice": "", "recommendEnabled": true
  }
}
```
Only dishes published on that day's menu and `active=true` are returned. The response is cached
with a 30 s TTL; order/cancel/Console writes invalidate immediately.

### `GET /dishes/{id}` → `data: <DishView>` (404 if hidden)

### `GET /menu/stream` — SSE (`text/event-stream`)
Events: `connected`, `stock` (`[{"dishId":9,"remaining":2,"menuDate":"..."}]`), `refresh`
(Console edited the catalogue), and `:ping` heartbeats every 20 s. Read with `fetch()` streaming
so the Bearer token stays in a header.

### `POST /orders` — header `Idempotency-Key: chk-...`
```json
{
  "deliveryDate": "2026-11-11", "mealPeriod": "Lunch",
  "address": "Floor 3, Building A",
  "items": [
    { "dishId": 9, "quantity": 2, "optionItemIds": [16, 18, 21] },
    { "dishId": 1, "quantity": 1, "optionItemIds": [] }
  ]
}
```
Server behaviour (all inside one transaction):
1. `SELECT ... FOR UPDATE` on the employee row serialises same-user submissions.
2. Cutoff rules resolve the effective meal/date (late Lunch → today's Dinner → tomorrow's Lunch).
3. Rejects an existing `Pending`/`Confirmed` order for that meal (409 `DUPLICATE_ORDER`).
4. Rejects >5 total portions (422 `RULE_VIOLATION`), invalid/duplicate/missing required options.
5. **Recomputes all prices from the database**; client prices are ignored.
6. Atomic conditional stock decrement (`remaining_qty >= qty`); insufficient stock → 409
   `STOCK_INSUFFICIENT` with the offending dish names and available counts; the whole transaction rolls back.
7. A unique `idempotency_key` guarantees one order; a concurrent duplicate returns the winning order.

→ `data: { "id": 12, "orderNo": "WB20261111-A1B2C3", "deliveryDate": "...", "mealPeriod": "Lunch", "address": "...", "totalCents": 9200, "status": "Pending", "createdAt": "...", "items": [...], "notice": "..." }`

### `GET /orders` → `data: [OrderView]`
### `GET /orders/{id}` → `data: OrderView`
### `GET /orders/active?date=&mealPeriod=`
→ `data: { "exists": true, "orderId": 12, "orderNo": "...", "resolvedDate": "...", "resolvedMealPeriod": "...", "notice": "..." }`
Used to switch `Place Order` to `View Existing Order`.

### `GET /orders/addresses` → `data: ["Floor 3, Building A", ...]` (up to 5 distinct)
### `POST /orders/{id}/cancel`
Only `Pending`. Restores stock (`LEAST(total_qty, remaining_qty + qty)`), pushes SSE, invalidates cache.

### `POST /ai/recommend` — SSE (`text/event-stream`)
```json
{ "query": "I want something light today" }
```
Events, streamed progressively: `status` (`{"message": ...}`), `delta` (`{"text": ...}` — real
model tokens or local reasoning), `dish` (`AiDishView`: dishId/name/reason/priceCents/imageUrl/
category/remaining), `done` (`{"count":3,"engine":"llm"|"local"}`).
Server excludes flagged allergens, dishes ordered in the last 7 days, sold-out and inactive dishes
before the model ever sees them.

### `GET /ai/status` → `data: { "engine": "local-ranking-engine", "model": "built-in", "streaming": true }`

---

## 3. Admin only — `Authorization` with `role=ADMIN` (403 for employees)

| Method | Path | Body / query | Returns |
|--------|------|--------------|---------|
| GET | `/console/dishes` | `keyword`, `category`, `page`, `size` | `{items:[DishView], totalElements, totalPages, page, size}` (includes hidden dishes) |
| POST | `/console/dishes` | `ConsoleDishRequest` (name, description, priceCents, category, protein, allergens[], spiceLevel, imageUrl, active, optionGroups[]) | `DishView` |
| PUT | `/console/dishes/{id}` | same | `DishView` (replaces option groups) |
| PATCH | `/console/dishes/{id}/active` | `{ "active": false }` | `DishView` |
| POST | `/console/uploads` | `multipart/form-data: file` (jpg/png/webp ≤5 MB) | `{ "url": "/api/uploads/<uuid>.jpg" }` |
| GET | `/console/daily-menu?date=YYYY-MM-DD` | — | `{ date, entries:[{dishId,name,category,totalQty,remainingQty}] }` |
| PUT | `/console/daily-menu` | `{ "date": "...", "entries": [{"dishId":1,"totalQty":12}] }` | `DailyMenuView`; preserves sold quantities |
| GET | `/console/dashboard` | — | `DashboardView` (below) |
| GET | `/console/cache-stats` | — | `{type, ttlSeconds, hits, misses, entries}` |

`DashboardView`:
```json
{
  "today": { "orders": 8, "revenueCents": 23450, "pending": 3, "confirmed": 2, "completed": 2, "cancelled": 1 },
  "topDishes": [{ "name": "Mapo Tofu", "quantity": 6, "revenueCents": 10800 }],
  "mealSplit": { "lunch": 6, "dinner": 2 },
  "trend": [{ "date": "2026-11-05", "orders": 3, "revenueCents": 6800 }],
  "lowStock": [{ "dishId": 4, "name": "Tomato Pasta", "remaining": 3 }],
  "generatedAt": "2026-11-11T09:50:12"
}
```
All figures are aggregated from real `orders` / `order_items` / `daily_menu` rows.

---

## 4. End-to-end curl smoke (copy/paste)

```bash
BASE=http://localhost:8080/api
curl -s -c /tmp/webox.cookies -H 'Content-Type: application/json' \
  -d '{"email":"employee@webox.com","password":"Employee@123"}' $BASE/auth/login \
  | tee /tmp/login.json
TOKEN=$(python3 -c 'import json;print(json.load(open("/tmp/login.json"))["data"]["accessToken"])')

curl -s -H "Authorization: Bearer $TOKEN" "$BASE/menu?size=2&categories=Chinese" | head -c 400
KEY="chk-demo-001"
curl -s -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" \
  -H 'Content-Type: application/json' \
  -d "{\"deliveryDate\":\"$(date +%F)\",\"mealPeriod\":\"Lunch\",\"address\":\"Floor 3, Building A\",\"items\":[{\"dishId\":1,\"quantity\":1,\"optionItemIds\":[]}]}" \
  $BASE/orders
# Repeat the exact same request: same orderNo, no second order created.
```

See `docs/TESTING.md` for the full verification checklist (including admin 403, oversell prevention
and stock restoration).
