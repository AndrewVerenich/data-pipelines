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

INSERT INTO users (name, country)
VALUES
    ('Alice', 'USA'),
    ('Bob', 'Germany'),
    ('Charlie', 'France'),
    ('Diana', 'UK'),
    ('Eve', 'Canada'),
    ('Frank', 'Japan'),
    ('Grace', 'Italy'),
    ('Heidi', 'Spain'),
    ('Ivan', 'Russia'),
    ('Judy', 'Brazil'),
    ('Karl', 'Sweden'),
    ('Laura', 'Norway'),
    ('Mallory', 'Poland'),
    ('Niaj', 'India'),
    ('Olivia', 'China'),
    ('Peggy', 'Australia'),
    ('Quentin', 'Mexico'),
    ('Rupert', 'Netherlands'),
    ('Sybil', 'Switzerland'),
    ('Trent', 'Austria');

DO $$
DECLARE
i INT;
    uid INT;
    amt NUMERIC;
    st TEXT;
    dt TIMESTAMP;
BEGIN
FOR i IN 1..10000 LOOP
        uid := (random()*19 + 1)::INT; -- случайный user_id от 1 до 20
        amt := round((random()*3000 + 5)::NUMERIC, 2); -- сумма от 5 до 3005
        st := (ARRAY['SUCCESS','FAILED','PENDING'])[ceil(random()*3)];
        dt := now() - (random()*30 || ' days')::interval; -- случайная дата за последние 30 дней
INSERT INTO transactions (user_id, amount, status, created_at)
VALUES (uid, amt, st, dt);
END LOOP;
END $$;