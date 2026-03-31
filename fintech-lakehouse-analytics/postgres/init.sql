CREATE TABLE customers (
    id SERIAL PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(180) UNIQUE NOT NULL,
    phone VARCHAR(30),
    country VARCHAR(60) NOT NULL,
    city VARCHAR(80) NOT NULL,
    date_of_birth DATE NOT NULL,
    kyc_status VARCHAR(20) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE accounts (
    id SERIAL PRIMARY KEY,
    customer_id INT NOT NULL REFERENCES customers(id),
    account_type VARCHAR(20) NOT NULL,
    account_status VARCHAR(20) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    balance NUMERIC(18, 2) NOT NULL,
    credit_limit NUMERIC(18, 2) NOT NULL DEFAULT 0,
    interest_rate NUMERIC(8, 4) NOT NULL DEFAULT 0,
    opened_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE merchants (
    id SERIAL PRIMARY KEY,
    merchant_name VARCHAR(140) NOT NULL,
    merchant_category VARCHAR(40) NOT NULL,
    mcc_code VARCHAR(4) NOT NULL,
    country VARCHAR(60) NOT NULL,
    city VARCHAR(80) NOT NULL,
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE loans (
    id SERIAL PRIMARY KEY,
    customer_id INT NOT NULL REFERENCES customers(id),
    account_id INT NOT NULL REFERENCES accounts(id),
    loan_type VARCHAR(20) NOT NULL,
    principal_amount NUMERIC(18, 2) NOT NULL,
    interest_rate NUMERIC(8, 4) NOT NULL,
    term_months INT NOT NULL,
    monthly_payment NUMERIC(18, 2) NOT NULL,
    outstanding_balance NUMERIC(18, 2) NOT NULL,
    loan_status VARCHAR(20) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    maturity_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE loan_payments (
    id SERIAL PRIMARY KEY,
    loan_id INT NOT NULL REFERENCES loans(id),
    payment_amount NUMERIC(18, 2) NOT NULL,
    principal_portion NUMERIC(18, 2) NOT NULL,
    interest_portion NUMERIC(18, 2) NOT NULL,
    payment_status VARCHAR(20) NOT NULL,
    payment_channel VARCHAR(20) NOT NULL,
    payment_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE exchange_rates (
    id SERIAL PRIMARY KEY,
    base_currency VARCHAR(3) NOT NULL,
    target_currency VARCHAR(3) NOT NULL,
    rate NUMERIC(12, 6) NOT NULL,
    effective_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(base_currency, target_currency, effective_date)
);

CREATE TABLE transactions (
    id SERIAL PRIMARY KEY,
    customer_id INT NOT NULL REFERENCES customers(id),
    account_id INT NOT NULL REFERENCES accounts(id),
    merchant_id INT REFERENCES merchants(id),
    transaction_type VARCHAR(20) NOT NULL,
    transaction_status VARCHAR(20) NOT NULL,
    payment_channel VARCHAR(20) NOT NULL,
    device_type VARCHAR(20) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    exchange_rate NUMERIC(12, 6) NOT NULL DEFAULT 1,
    amount NUMERIC(18, 2) NOT NULL,
    amount_usd NUMERIC(18, 2) NOT NULL,
    fee_amount NUMERIC(18, 2) NOT NULL DEFAULT 0,
    is_international BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);