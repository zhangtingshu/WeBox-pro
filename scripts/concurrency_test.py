#!/usr/bin/env python3
"""Concurrency proof: two employees order the last 3 portions of the same dish at the same time.

Exactly one order must succeed; the other must get STOCK_INSUFFICIENT with the dish name.
No overselling, no negative stock. Requires the Docker stack to be running.
"""
import json
import urllib.request
import urllib.error
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date

BASE = "http://localhost:8080/api"
DISH_ID = 9  # Classic Beef Burger, seeded with 3 portions today (low stock)
REQUIRED_OPTIONS = [15, 17]  # Bun: Plain, Sauce: Ketchup


def call(method, path, body=None, token=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read().decode())


def login(email, password):
    status, payload = call("POST", "/auth/login", {"email": email, "password": password})
    assert status == 200, (email, payload)
    return payload["data"]["accessToken"]


def main():
    today = date.today().isoformat()
    employee = login("employee@webox.com", "Employee@123")
    admin = login("admin@webox.com", "Admin@12345")
    call("POST", "/auth/register", {
        "email": "stress@webox.com", "password": "Stress@123", "fullName": "Stress Tester",
    })
    second = login("stress@webox.com", "Stress@123")

    # Clean up any Pending demo orders so the duplicate-order rule cannot mask the stock race.
    for token in (employee, second):
        _, orders = call("GET", "/orders", token=token)
        for order in orders["data"]:
            if order["status"] == "Pending":
                call("POST", f"/orders/{order['id']}/cancel", token=token)

    # Make sure the target dish has exactly 3 portions available today.
    _, menu = call("GET", f"/console/daily-menu?date={today}", token=admin)
    entries = menu["data"]["entries"]
    target = next((e for e in entries if e["dishId"] == DISH_ID), None)
    if target is None or target["remainingQty"] < 3:
        if target is None:
            entries.append({"dishId": DISH_ID, "totalQty": 3, "remainingQty": 3})
        else:
            # New supply = already sold + 3 available portions.
            sold = target["totalQty"] - target["remainingQty"]
            target["totalQty"] = sold + 3
        payload = {
            "date": today,
            "entries": [{"dishId": e["dishId"], "totalQty": e["totalQty"]} for e in entries],
        }
        status, result = call("PUT", "/console/daily-menu", payload, token=admin)
        assert status == 200, result

    _, before = call("GET", "/menu?keyword=Classic%20Beef%20Burger&size=1", token=employee)
    remaining_before = before["data"]["items"][0]["remaining"]
    print(f"Remaining before concurrent orders: {remaining_before}")

    body = {
        "deliveryDate": today, "mealPeriod": "Dinner", "address": "Concurrency Lab",
        "items": [{"dishId": DISH_ID, "quantity": 3, "optionItemIds": REQUIRED_OPTIONS}],
    }

    def submit(token, key):
        return call("POST", "/orders", body, token=token,
                    headers={"Idempotency-Key": key})

    run_id = uuid.uuid4().hex[:8]
    with ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(submit, employee, f"race-employee-a-{run_id}"),
                   pool.submit(submit, second, f"race-employee-b-{run_id}")]
        results = [future.result() for future in futures]

    for index, (status, payload) in enumerate(results, start=1):
        order_no = (payload.get("data") or {}).get("orderNo")
        error = (payload.get("error") or {}).get("code")
        message = (payload.get("error") or {}).get("message")
        print(f"Employee {index}: HTTP {status} -> orderNo={order_no} error={error}")
        if error:
            print(f"  message: {message}")

    successes = sum(1 for _, payload in results
                    if (payload.get("data") or {}).get("orderNo"))
    failures = sum(1 for _, payload in results
                    if (payload.get("error") or {}).get("code") == "STOCK_INSUFFICIENT")

    _, after = call("GET", "/menu?keyword=Classic%20Beef%20Burger&size=1", token=employee)
    remaining_after = after["data"]["items"][0]["remaining"]
    print(f"Remaining after concurrent orders: {remaining_after}")

    assert successes == 1, f"expected exactly 1 successful order, got {successes}"
    assert failures == 1, f"expected exactly 1 stock rejection, got {failures}"
    assert remaining_after == 0, "stock must never go negative"
    print("PASS  No overselling: one order created, one rejected with STOCK_INSUFFICIENT, stock = 0")


if __name__ == "__main__":
    main()
