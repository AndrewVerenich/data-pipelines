CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    country VARCHAR(50),
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE transactions (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id),
    amount NUMERIC(10,2),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT now()
);

INSERT INTO users (name, country) VALUES
    ('Alice', 'USA'),
    ('Bob', 'Germany'),
    ('Charlie', 'France');

INSERT INTO transactions (user_id, amount, status) VALUES
    (1, 100.50, 'SUCCESS'),
    (2, 200.00, 'FAILED'),
    (3, 300.75, 'SUCCESS');
