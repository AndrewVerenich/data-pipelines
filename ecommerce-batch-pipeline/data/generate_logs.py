import json, random, time

events = [
    "User logged in", "Page viewed", "Product viewed",
    "Product added to cart", "Checkout started",
    "Payment completed", "Payment failed"
]
devices = ["mobile", "desktop", "tablet"]
pages = ["home", "search", "product", "checkout"]

target_size = 1 * 1024 * 1024  # 256 MB

with open("logs.txt", "w") as f:
    f.write("[\n")
    first = True
    while f.tell() < target_size:
        log = {
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S"),
            "level": random.choice(["INFO", "ERROR"]),
            "event": random.choice(events),
            "userId": f"u{random.randint(1,10000)}",
            "sessionId": f"s{random.randint(1,100000)}",
            "device": random.choice(devices),
            "page": random.choice(pages)
        }
        if not first:
            f.write(",\n")
        f.write(json.dumps(log))
        first = False
    f.write("\n]\n")
