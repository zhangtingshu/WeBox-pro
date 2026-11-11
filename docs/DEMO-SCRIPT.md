# 5-minute demo script (English UI)

Open **http://localhost:8081**. Have a second browser window (or an incognito window) ready for
the real-time stock check.

## 0. Preparation (30 s)

```bash
cd WeBox-Submission
docker compose up -d --build
curl http://localhost:8080/health     # UP
curl http://localhost:8080/ready      # READY, database mysql
```

Login as **employee@webox.com / Employee@123** in window A, and as a second employee
(register `demo2@webox.com / Demo@12345`) in window B.

## 1. Tier 1 — core flow (90 s)

1. **Menu**: point out English cards with image, price `¥22.50`, cuisine tag, stock badge.
   - Filter: click `Chinese` + `Japanese` chips → multi-select works.
   - Search: type `chicken` → 300 ms debounce, only matching dishes.
   - Paging: show `Page 1 of 3` (30 seeded dishes) → server-side paging.
2. **Detail + customization**: open `Classic Beef Burger` → choose `Whole Wheat` + `Mustard` +
   `Cheese (+¥3.00)` → price updates live from `¥38.00` to `¥41.00`. Try to leave `Sauce`
   unselected to show required-option validation.
3. **Allergen warning**: open `Kung Pao Chicken` (contains `Peanuts`, which the demo account
   flagged) → English confirm `This dish contains an allergen you flagged: [Peanuts]. Add anyway?`
   → note dishes are **warned, not filtered**.
4. **Cart**: add 3 + 2 portions of two dishes; show different configurations as separate lines,
   live total; the 6th `+` is disabled with the 5-portion message.
   Remove one, proceed to checkout.
5. **Checkout**: choose date / `Lunch` / address (history dropdown), see budget hint when over
   ¥35.00, click **Place Order**.
6. **Success page**: order number `WB...`, delivery date, period, address, total.
   **Re-submit the same request with the same `Idempotency-Key`** (curl snippet in `docs/API.md`)
   → same `orderNo`, no duplicate order.

## 2. Tier 2 — rules and preferences (60 s)

7. **Preferences**: flag allergens, pick cuisines, spice, taste, budget, enable "Recommend for me".
   Back on the menu, preferred dishes move to the front with a `★ For you` highlight; toggle off →
   default order returns.
8. **Cutoff rule**: mention the server-side rule (Lunch closes 10:00 → today's Dinner → tomorrow's
   Lunch) and show `OrderRulesTest` covering all three cases.
9. **Duplicate order**: return to checkout for the same meal → button reads **View Existing Order**.
10. **My Orders**: open the order → options, quantity, subtotal, total; **Cancel** (Pending only),
    then show stock restored.

## 3. Console (admin only) (60 s)

11. Sign out, sign in as **admin@webox.com / Admin@12345** → nav shows **Console**, which uses a
    completely different sidebar layout.
12. Try `curl -H "Authorization: Bearer <employee-token>" http://localhost:8080/api/console/dishes`
    → **403 Forbidden** (RBAC evidence).
13. **Dishes**: search / category filter / paging, edit a dish, upload an image, take it offline →
    employee menu refreshes automatically (SSE `refresh` event).
14. **Daily Menu**: default tomorrow, adjust today's supply → stock changes are preserved relative
    to quantities already sold.
15. **Dashboard**: after the demo orders, show today's orders/revenue, Top 10 bars, Lunch vs Dinner,
    7-day trend, low-stock list; click **Refresh** → numbers come from real orders.

## 4. Tier 3 — realtime + AI (60 s)

16. Window A + window B both show `Tomato Pasta` with `Only 3 left`. Place an order in A →
    window B's badge drops **without a manual refresh** (SSE `stock` event).
17. Click **Ask the AI assistant**, type `I want something light today` → status/reason tokens
    stream in, dish cards appear one by one with personalised English reasons; `View & add`
    opens the dish detail. `/api/ai/status` shows the active engine; with `LLM_API_KEY` set it is a
    real OpenAI-compatible stream, otherwise the built-in local engine still works.
18. Mention the server hard-filters allergens, last-7-day repeats and sold-out dishes before the
    model sees anything.

## 5. Delivery evidence (30 s)

Open the folder and show:

| File | What it proves |
|------|----------------|
| `README.md` | From-zero startup, tech-stack reasoning |
| `docs/API.md` | Full endpoint list with request/response shapes |
| `docs/ARCHITECTURE.md` | Diagram, data flow, concurrency/cache/idempotency decisions |
| `docs/TESTING.md` | 19 passing tests + curl smoke results |
| `docs/SCORING-MATRIX.md` | Appendix A point-by-point evidence |
| `docs/PROMPT-DECK.md` | The R0–R11 copy-paste prompt deck extracted from the PRD |
| **`ai-conversations/`** | **Tool-native raw conversation export (`*-native.jsonl`, verbatim session log from the first instruction to the final verification) + provenance README — no screenshots, excerpts or after-the-fact summaries** |

## 6. Closing talking points (30 s)

- **Tech choices**: Spring Boot 3 + JDK 17 + standalone MySQL 8 (mandated), React+Vite for a
  component-based SPA, in-process TTL cache because Redis is optional, SSE because stock changes
  are one-directional.
- **3 problems solved**: (1) double submit → idempotency key + unique index + race recovery;
  (2) overselling → atomic conditional UPDATE inside one transaction; (3) login lost on refresh →
  httpOnly rotating refresh token instead of localStorage.
- **Long-term ownership**: layered code, versioned SQL migrations, env-only config with fail-fast
  validation, typed errors, structured logs with request IDs, graceful shutdown, health probes.
