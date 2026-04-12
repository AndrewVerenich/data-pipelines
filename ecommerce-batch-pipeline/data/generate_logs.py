import json
import random
import datetime
from pathlib import Path

random.seed(42)

events = [
    "User logged in",
    "Page viewed",
    "Product viewed",
    "Product added to cart",
    "Checkout started",
    "Payment completed",
    "Payment failed",
]
devices = ["mobile", "desktop", "tablet"]
pages = ["home", "search", "product", "checkout"]
error_types = [
    "card_declined",
    "payment_failed",
    "insufficient_funds",
    "network_error",
    "invalid_cvv",
    "expired_card",
    "fraud_suspected",
]
payment_methods = ["card", "paypal", "apple_pay", "google_pay"]
categories = ["electronics", "books", "clothing", "toys", "sports", "home"]

n_users = 400
n_products = 60
n_orders = 200
target_lines = 2500

data_dir = Path(__file__).resolve().parent

user_ids = [f"u{i}" for i in range(1, n_users + 1)]
product_ids = [f"p{i}" for i in range(100, 100 + n_products)]
order_ids = [f"o{i}" for i in range(1, n_orders + 1)]

countries = ["US", "DE", "FR", "UK", "PL", "UA", "ES", "IT"]
cohorts = ["2023-Q1", "2023-Q2", "2024-Q1", "2024-Q2", "2024-Q3"]

users = []
for uid in user_ids:
    users.append(
        {
            "userId": uid,
            "email": f"{uid}@example.com",
            "country": random.choice(countries),
            "cohort": random.choice(cohorts),
        }
    )

products = []
for pid in product_ids:
    products.append(
        {
            "productId": pid,
            "productName": f"Product {pid}",
            "category": random.choice(categories),
            "unitPrice": round(random.uniform(5.0, 500.0), 2),
        }
    )

with open(data_dir / "users.jsonl", "w", encoding="utf-8") as f:
    for row in users:
        f.write(json.dumps(row, ensure_ascii=False) + "\n")

with open(data_dir / "products.jsonl", "w", encoding="utf-8") as f:
    for row in products:
        f.write(json.dumps(row, ensure_ascii=False) + "\n")

with open(data_dir / "events.jsonl", "w", encoding="utf-8") as f:
    for _ in range(target_lines):
        now = datetime.datetime.now()
        delta = datetime.timedelta(seconds=random.randint(0, 86_400))
        ts = (now + delta).strftime("%Y-%m-%dT%H:%M:%S")

        event = random.choice(events)
        level = random.choice(["INFO", "ERROR"])
        uid = random.choice(user_ids)
        pid = random.choice(product_ids) if random.random() > 0.15 else None

        log = {
            "timestamp": ts,
            "level": level,
            "event": event,
            "userId": uid,
            "sessionId": f"s{random.randint(1, 80_000)}",
            "device": random.choice(devices),
            "page": random.choice(pages),
            "errorType": random.choice(error_types) if level == "ERROR" else None,
            "paymentMethod": random.choice(payment_methods)
            if event == "Payment completed"
            else None,
            "category": random.choice(categories) if event == "Product viewed" else None,
            "productId": pid
            if event in ["Product viewed", "Product added to cart", "Checkout started"]
            else None,
            "orderId": random.choice(order_ids)
            if event
            in ["Checkout started", "Payment completed", "Payment failed"]
            else None,
        }

        f.write(json.dumps(log, ensure_ascii=False) + "\n")

# Backwards-compatible single file name for local scripts
with open(data_dir / "logs.txt", "w", encoding="utf-8") as f:
    f.write((data_dir / "events.jsonl").read_text(encoding="utf-8"))

print("Wrote users.jsonl, products.jsonl, events.jsonl, logs.txt")
