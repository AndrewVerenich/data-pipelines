CREATE TABLE IF NOT EXISTS log_events (
    id SERIAL PRIMARY KEY,
    timestamp TIMESTAMP,
    level TEXT,
    event TEXT,
    userId TEXT,
    sessionId TEXT,
    device TEXT,
    page TEXT,
    productId TEXT,
    category TEXT,
    errorType TEXT,
    paymentMethod TEXT,
    minute TEXT
);
