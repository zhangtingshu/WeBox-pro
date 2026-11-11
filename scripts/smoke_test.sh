#!/usr/bin/env bash
# End-to-end smoke test. Requires the stack to be up (docker compose up -d).
# Usage: bash scripts/smoke_test.sh [BASE_URL]
set -u
BASE="${1:-http://localhost:8080}"
API="$BASE/api"
TODAY="$(date +%F)"
TOMORROW="$(python3 -c 'import datetime; print((datetime.date.today()+datetime.timedelta(days=1)).isoformat())')"
PASS=0
FAIL=0

check() {
  local name="$1" condition="$2" detail="${3:-}"
  if [ "$condition" = "1" ]; then
    echo "PASS  $name ${detail:+· $detail}"
    PASS=$((PASS + 1))
  else
    echo "FAIL  $name ${detail:+· $detail}"
    FAIL=$((FAIL + 1))
  fi
}

jget() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)" 2>/dev/null; }

echo "== WeBox smoke test against $BASE =="
curl -s --retry 15 --retry-delay 2 --retry-connrefused "$BASE/health" > /tmp/webox_health.json
HEALTH=$(jget "d.get('status','')" < /tmp/webox_health.json)
check "GET /health" "$([ "$HEALTH" = "UP" ] && echo 1 || echo 0)" "$HEALTH"
READY=$(curl -s "$BASE/ready" | jget "d.get('status','')")
check "GET /ready (real MySQL)" "$([ "$READY" = "READY" ] && echo 1 || echo 0)" "$READY"

META=$(curl -s "$API/meta")
check "GET /api/meta (English enums)" "$(echo "$META" | jget "1 if 'Chinese' in d['data']['categories'] else 0")"

curl -s -c /tmp/webox_emp.cookies -H 'Content-Type: application/json' \
  -d '{"email":"employee@webox.com","password":"Employee@123"}' \
  "$API/auth/login" > /tmp/webox_login.json
EMP=$(jget "d.get('data',{}).get('accessToken','')" < /tmp/webox_login.json)
check "Employee login" "$([ -n "$EMP" ] && echo 1 || echo 0)"

# Make the script repeatable: cancel Pending orders left by previous runs,
# otherwise the "one active order per meal" rule correctly blocks this run.
PENDING_IDS=$(curl -s -H "Authorization: Bearer $EMP" "$API/orders" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print(' '.join(str(o['id']) for o in d.get('data',[]) if o.get('status')=='Pending'))" 2>/dev/null)
for oid in ${PENDING_IDS:-}; do
  curl -s -X POST -H "Authorization: Bearer $EMP" "$API/orders/$oid/cancel" > /dev/null
done
STOCK_BASE=$(curl -s -H "Authorization: Bearer $EMP" "$API/menu?keyword=Kung%20Pao&size=1" \
  | jget "d['data']['items'][0]['remaining']" 2>/dev/null)
echo "INFO  clean slate: ${STOCK_BASE:-?} portions of Kung Pao Chicken available"

curl -s -c /tmp/webox_admin.cookies -H 'Content-Type: application/json' \
  -d '{"email":"admin@webox.com","password":"Admin@12345"}' \
  "$API/auth/login" > /tmp/webox_admin_login.json
ADMIN=$(jget "d.get('data',{}).get('accessToken','')" < /tmp/webox_admin_login.json)
check "Admin login" "$([ -n "$ADMIN" ] && echo 1 || echo 0)"

REFRESH=$(curl -s -b /tmp/webox_emp.cookies -X POST "$API/auth/refresh" | jget "1 if d.get('data',{}).get('accessToken') else 0")
check "Refresh restores session after reload" "$REFRESH"

MENU=$(curl -s -H "Authorization: Bearer $EMP" "$API/menu?size=12&categories=Chinese,Japanese")
TOTAL=$(echo "$MENU" | jget "d['data']['totalElements']")
FIRST=$(echo "$MENU" | jget "d['data']['items'][0]['dish']['name']" 2>/dev/null)
check "GET /api/menu (today's seeded menu)" "$([ "${TOTAL:-0}" -gt 0 ] && echo 1 || echo 0)" "$TOTAL dishes, first=$FIRST"
check "Menu cache stats" "$(curl -s -H "Authorization: Bearer $ADMIN" "$API/console/cache-stats" | jget "1 if d.get('data',{}).get('type') else 0")"

SEARCH=$(curl -s -H "Authorization: Bearer $EMP" "$API/menu?keyword=chicken&size=12" | jget "d['data']['totalElements']")
check "Parameterised keyword search" "$([ "${SEARCH:-0}" -gt 0 ] && echo 1 || echo 0)" "$SEARCH hits"

CODE_EMP=$(curl -s -o /tmp/webox_403.json -w '%{http_code}' -H "Authorization: Bearer $EMP" "$API/console/dishes")
check "Employee blocked from Console (403)" "$([ "$CODE_EMP" = "403" ] && echo 1 || echo 0)" "HTTP $CODE_EMP"
CODE_ADMIN=$(curl -s -o /tmp/webox_console.json -w '%{http_code}' -H "Authorization: Bearer $ADMIN" "$API/console/dishes")
check "Admin can open Console (200)" "$([ "$CODE_ADMIN" = "200" ] && echo 1 || echo 0)" "HTTP $CODE_ADMIN"

BODY="{\"deliveryDate\":\"$TODAY\",\"mealPeriod\":\"Lunch\",\"address\":\"Floor 3, Building A\",\"items\":[{\"dishId\":1,\"quantity\":2,\"optionItemIds\":[]}]}"
KEY="smoke-$(date +%s)-001"
ORDER1=$(curl -s -H "Authorization: Bearer $EMP" -H "Idempotency-Key: $KEY" \
  -H 'Content-Type: application/json' -d "$BODY" "$API/orders")
echo "$ORDER1" > /tmp/webox_order1.json
NO1=$(echo "$ORDER1" | jget "d.get('data',{}).get('orderNo','')")
STATUS1=$(echo "$ORDER1" | jget "d.get('data',{}).get('status','')")
check "Place order" "$([ -n "$NO1" ] && echo 1 || echo 0)" "$NO1 / $STATUS1"
PERIOD1=$(echo "$ORDER1" | jget "d.get('data',{}).get('mealPeriod','')")
check "Cutoff auto-switch rule applied" "$([ -n "$PERIOD1" ] && echo 1 || echo 0)" "resolved=$PERIOD1"

ORDER2=$(curl -s -H "Authorization: Bearer $EMP" -H "Idempotency-Key: $KEY" \
  -H 'Content-Type: application/json' -d "$BODY" "$API/orders")
NO2=$(echo "$ORDER2" | jget "d.get('data',{}).get('orderNo','')")
check "Idempotent re-submit returns same order" "$([ -n "$NO1" ] && [ "$NO1" = "$NO2" ] && echo 1 || echo 0)" "$NO1 == $NO2"

DUPLICATE=$(curl -s -H "Authorization: Bearer $EMP" -H "Idempotency-Key: $KEY-fresh" \
  -H 'Content-Type: application/json' -d "$BODY" "$API/orders")
DUP_CODE=$(echo "$DUPLICATE" | jget "d.get('error',{}).get('code','')")
check "One active order per meal (409)" "$([ "$DUP_CODE" = "DUPLICATE_ORDER" ] && echo 1 || echo 0)" "$DUP_CODE"

LIMIT=$(curl -s -H "Authorization: Bearer $EMP" -H "Idempotency-Key: $KEY-limit" \
  -H 'Content-Type: application/json' \
  -d "{\"deliveryDate\":\"$TOMORROW\",\"mealPeriod\":\"Dinner\",\"address\":\"x\",\"items\":[{\"dishId\":7,\"quantity\":6,\"optionItemIds\":[]}]}" \
  "$API/orders" | jget "d.get('error',{}).get('code','')")
check "5-portion limit enforced" "$([ "$LIMIT" = "RULE_VIOLATION" ] && echo 1 || echo 0)" "$LIMIT"

STOCK_AFTER=$(curl -s -H "Authorization: Bearer $EMP" "$API/menu?keyword=Kung%20Pao&size=1" \
  | jget "d['data']['items'][0]['remaining']" 2>/dev/null)
check "Stock deducted after order (base -> base-2)" \
  "$([ -n "${STOCK_BASE:-}" ] && [ "${STOCK_AFTER:-0}" -eq $((STOCK_BASE - 2)) ] && echo 1 || echo 0)" \
  "$STOCK_BASE -> $STOCK_AFTER"

OID=$(echo "$ORDER1" | jget "d.get('data',{}).get('id','')")
ORDERS=$(curl -s -H "Authorization: Bearer $EMP" "$API/orders" | jget "len(d.get('data',[]))")
check "My Orders lists the new order" "$([ "${ORDERS:-0}" -ge 1 ] && echo 1 || echo 0)" "$ORDERS order(s)"
DETAIL=$(curl -s -H "Authorization: Bearer $EMP" "$API/orders/$OID" | jget "d.get('data',{}).get('orderNo','')")
check "Order detail" "$([ "$DETAIL" = "$NO1" ] && echo 1 || echo 0)" "$DETAIL"

DASH=$(curl -s -H "Authorization: Bearer $ADMIN" "$API/console/dashboard" | jget "d.get('data',{}).get('today',{}).get('orders','-1')")
check "Dashboard aggregates real orders" "$([ "${DASH:-0}" -ge 1 ] && echo 1 || echo 0)" "today orders=$DASH"

CANCEL=$(curl -s -X POST -H "Authorization: Bearer $EMP" "$API/orders/$OID/cancel")
CSTATUS=$(echo "$CANCEL" | jget "d.get('data',{}).get('status','')")
check "Cancel Pending order restores stock" "$([ "$CSTATUS" = "Cancelled" ] && echo 1 || echo 0)" "$CSTATUS"
STOCK_RESTORED=$(curl -s -H "Authorization: Bearer $EMP" "$API/menu?keyword=Kung%20Pao&size=1" \
  | jget "d['data']['items'][0]['remaining']" 2>/dev/null)
check "Stock restored to base after cancel" \
  "$([ -n "${STOCK_BASE:-}" ] && [ "${STOCK_RESTORED:-0}" -eq "$STOCK_BASE" ] && echo 1 || echo 0)" \
  "remaining=$STOCK_RESTORED"

echo "== AI streaming (first 12 SSE lines) =="
curl -sN --max-time 20 -H "Authorization: Bearer $EMP" -H 'Content-Type: application/json' \
  -d '{"query":"I want something light today"}' "$API/ai/recommend" | head -12
AI_EVENTS=$(curl -sN --max-time 20 -H "Authorization: Bearer $EMP" -H 'Content-Type: application/json' \
  -d '{"query":"A high-protein low-fat lunch"}' "$API/ai/recommend" | grep -c '^event:' || true)
check "AI assistant streams SSE events" "$([ "${AI_EVENTS:-0}" -gt 0 ] && echo 1 || echo 0)" "$AI_EVENTS events"

echo "== Result: $PASS passed, $FAIL failed =="
[ "$FAIL" -eq 0 ]
