import json, random, datetime

events = [
    "User logged in", "Page viewed", "Product viewed",
    "Product added to cart", "Checkout started",
    "Payment completed", "Payment failed"
]
devices = ["mobile", "desktop", "tablet"]
pages = ["home", "search", "product", "checkout"]
error_types = [
    "card_declined", "payment_failed", "insufficient_funds",
    "network_error", "invalid_cvv", "expired_card", "fraud_suspected"
]
payment_methods = ["card", "paypal", "apple_pay", "google_pay"]
categories = ["electronics", "books", "clothing", "toys", "sports", "home"]

target_lines = 1000

with open("logs.txt", "w", encoding="utf-8") as f:
    for i in range(target_lines):
        now = datetime.datetime.now()
        delta = datetime.timedelta(seconds=random.randint(0, 3600))
        ts = (now + delta).strftime("%Y-%m-%dT%H:%M:%S")

        event = random.choice(events)
        level = random.choice(["INFO", "ERROR"])

        log = {
            "timestamp": ts,
            "level": level,
            "event": event,
            "userId": f"u{random.randint(1,10000)}",
            "sessionId": f"s{random.randint(1,100000)}",
            "device": random.choice(devices),
            "page": random.choice(pages),
            "errorType": random.choice(error_types) if level == "ERROR" else None,
            "paymentMethod": random.choice(payment_methods) if event == "Payment completed" else None,
            "category": random.choice(categories) if event == "Product viewed" else None,
            "productId": f"p{random.randint(100,999)}" if event in ["Product viewed", "Product added to cart"] else None
        }

        f.write(json.dumps(log, ensure_ascii=False) + "\n")
