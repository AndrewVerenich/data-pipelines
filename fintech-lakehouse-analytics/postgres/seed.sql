INSERT INTO customers (full_name, email, phone, country, city, date_of_birth, kyc_status, risk_level, is_active, created_at, updated_at)
SELECT
    'Customer ' || gs::TEXT,
    'customer' || gs::TEXT || '@fintech.demo',
    '+100000' || LPAD(gs::TEXT, 6, '0'),
    (ARRAY['USA', 'Germany', 'France', 'UK', 'Canada', 'Japan', 'Spain', 'Poland', 'India', 'Brazil'])[1 + floor(random() * 10)::INT],
    (ARRAY['New York', 'Berlin', 'Paris', 'London', 'Toronto', 'Tokyo', 'Madrid', 'Warsaw', 'Mumbai', 'Sao Paulo'])[1 + floor(random() * 10)::INT],
    DATE '1965-01-01' + ((random() * 14000)::INT),
    (ARRAY['VERIFIED', 'PENDING', 'REJECTED'])[1 + floor(random() * 3)::INT],
    (ARRAY['LOW', 'MEDIUM', 'HIGH'])[1 + floor(random() * 3)::INT],
    random() > 0.08,
    NOW() - ((random() * 730)::INT || ' days')::INTERVAL,
    NOW() - ((random() * 30)::INT || ' days')::INTERVAL
FROM generate_series(1, 200) AS gs
ON CONFLICT (email) DO NOTHING;

INSERT INTO accounts (customer_id, account_type, account_status, currency_code, balance, credit_limit, interest_rate, opened_at, updated_at)
SELECT
    c.id,
    (ARRAY['CHECKING', 'SAVINGS', 'INVESTMENT', 'CREDIT'])[1 + floor(random() * 4)::INT],
    (ARRAY['ACTIVE', 'BLOCKED', 'CLOSED'])[1 + floor(random() * 3)::INT],
    (ARRAY['USD', 'EUR', 'GBP'])[1 + floor(random() * 3)::INT],
    round((random() * 200000)::NUMERIC, 2),
    round((random() * 50000)::NUMERIC, 2),
    round((random() * 12)::NUMERIC, 4),
    NOW() - ((random() * 1500)::INT || ' days')::INTERVAL,
    NOW() - ((random() * 20)::INT || ' days')::INTERVAL
FROM customers c
CROSS JOIN LATERAL generate_series(1, 1 + floor(random() * 2)::INT) AS g;

INSERT INTO merchants (merchant_name, merchant_category, mcc_code, country, city, is_online, created_at)
SELECT
    'Merchant ' || gs::TEXT,
    (ARRAY['RETAIL', 'FOOD', 'TRANSPORT', 'UTILITIES', 'ENTERTAINMENT', 'HEALTHCARE'])[1 + floor(random() * 6)::INT],
    LPAD((5000 + floor(random() * 900)::INT)::TEXT, 4, '0'),
    (ARRAY['USA', 'Germany', 'France', 'UK', 'Canada', 'Japan'])[1 + floor(random() * 6)::INT],
    (ARRAY['New York', 'Berlin', 'Paris', 'London', 'Toronto', 'Tokyo'])[1 + floor(random() * 6)::INT],
    random() > 0.35,
    NOW() - ((random() * 365)::INT || ' days')::INTERVAL
FROM generate_series(1, 120) AS gs;

INSERT INTO loans (customer_id, account_id, loan_type, principal_amount, interest_rate, term_months, monthly_payment, outstanding_balance, loan_status, issued_at, maturity_at, updated_at)
SELECT
    a.customer_id,
    a.id,
    (ARRAY['PERSONAL', 'MORTGAGE', 'AUTO', 'BUSINESS'])[1 + floor(random() * 4)::INT],
    principal,
    irate,
    term,
    round((principal / term + (principal * irate / 1200))::NUMERIC, 2),
    round((principal * random())::NUMERIC, 2),
    (ARRAY['ACTIVE', 'PAID_OFF', 'DEFAULTED', 'DELINQUENT'])[1 + floor(random() * 4)::INT],
    issued_at,
    issued_at + (term || ' months')::INTERVAL,
    NOW() - ((random() * 15)::INT || ' days')::INTERVAL
FROM (
    SELECT
        a.*,
        round((5000 + random() * 250000)::NUMERIC, 2) AS principal,
        round((3 + random() * 15)::NUMERIC, 4) AS irate,
        (12 + floor(random() * 72)::INT) AS term,
        NOW() - ((random() * 1200)::INT || ' days')::INTERVAL AS issued_at
    FROM accounts a
    WHERE random() > 0.55
    LIMIT 500
) a;

INSERT INTO loan_payments (loan_id, payment_amount, principal_portion, interest_portion, payment_status, payment_channel, payment_date, created_at)
SELECT
    l.id,
    l.monthly_payment,
    round((l.monthly_payment * (0.55 + random() * 0.35))::NUMERIC, 2),
    round((l.monthly_payment * (0.15 + random() * 0.25))::NUMERIC, 2),
    (ARRAY['ON_TIME', 'LATE', 'MISSED'])[1 + floor(random() * 3)::INT],
    (ARRAY['MOBILE', 'WEB', 'WIRE', 'BRANCH'])[1 + floor(random() * 4)::INT],
    l.issued_at + (gs || ' months')::INTERVAL,
    l.issued_at + (gs || ' months')::INTERVAL
FROM loans l
CROSS JOIN LATERAL generate_series(1, LEAST(l.term_months, 24)) AS gs
LIMIT 5000;

INSERT INTO exchange_rates (base_currency, target_currency, rate, effective_date, created_at)
SELECT
    base_currency,
    target_currency,
    rate,
    effective_date,
    effective_date::TIMESTAMP + INTERVAL '1 hour'
FROM (
    SELECT
        b.currency AS base_currency,
        t.currency AS target_currency,
        (DATE '2025-01-01' + day_idx) AS effective_date,
        CASE
            WHEN b.currency = t.currency THEN 1.0
            WHEN b.currency = 'EUR' AND t.currency = 'USD' THEN round((1.06 + random() * 0.05)::NUMERIC, 6)
            WHEN b.currency = 'GBP' AND t.currency = 'USD' THEN round((1.22 + random() * 0.06)::NUMERIC, 6)
            WHEN b.currency = 'USD' AND t.currency = 'EUR' THEN round((0.90 + random() * 0.05)::NUMERIC, 6)
            WHEN b.currency = 'USD' AND t.currency = 'GBP' THEN round((0.78 + random() * 0.05)::NUMERIC, 6)
            ELSE round((0.85 + random() * 0.25)::NUMERIC, 6)
        END AS rate
    FROM (VALUES ('USD'), ('EUR'), ('GBP')) AS b(currency)
    CROSS JOIN (VALUES ('USD'), ('EUR'), ('GBP')) AS t(currency)
    CROSS JOIN generate_series(0, 89) AS day_idx
) s
ON CONFLICT (base_currency, target_currency, effective_date) DO NOTHING;

INSERT INTO transactions (
    customer_id,
    account_id,
    merchant_id,
    transaction_type,
    transaction_status,
    payment_channel,
    device_type,
    currency_code,
    exchange_rate,
    amount,
    amount_usd,
    fee_amount,
    is_international,
    created_at,
    updated_at
)
SELECT
    a.customer_id,
    a.id,
    CASE WHEN random() > 0.1 THEN (1 + floor(random() * 120)::INT) ELSE NULL END,
    (ARRAY['PURCHASE', 'TRANSFER', 'WITHDRAWAL', 'DEPOSIT', 'REFUND', 'FEE'])[1 + floor(random() * 6)::INT],
    (ARRAY['SUCCESS', 'FAILED', 'PENDING', 'REVERSED'])[1 + floor(random() * 4)::INT],
    (ARRAY['MOBILE', 'WEB', 'ATM', 'POS', 'WIRE'])[1 + floor(random() * 5)::INT],
    (ARRAY['IOS', 'ANDROID', 'WEB', 'TERMINAL'])[1 + floor(random() * 4)::INT],
    a.currency_code,
    coalesce(fx.rate, 1.0),
    txn_amount,
    round((txn_amount * coalesce(fx.rate, 1.0))::NUMERIC, 2),
    round((txn_amount * (0.002 + random() * 0.015))::NUMERIC, 2),
    random() > 0.75,
    txn_ts,
    txn_ts + ((random() * 2)::INT || ' minutes')::INTERVAL
FROM (
    SELECT
        a.*,
        round((5 + random() * 4500)::NUMERIC, 2) AS txn_amount,
        NOW() - ((random() * 90)::INT || ' days')::INTERVAL - ((random() * 86400)::INT || ' seconds')::INTERVAL AS txn_ts
    FROM accounts a
    CROSS JOIN generate_series(1, 130)
    LIMIT 50000
) a
LEFT JOIN LATERAL (
    SELECT rate
    FROM exchange_rates er
    WHERE er.base_currency = a.currency_code
      AND er.target_currency = 'USD'
      AND er.effective_date = DATE(a.txn_ts)
    LIMIT 1
) fx ON TRUE;
