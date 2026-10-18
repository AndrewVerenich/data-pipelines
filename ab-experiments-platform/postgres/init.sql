-- Experiment config is the assignment source of truth.
-- Salts and keys match DemoCatalog in platform-lib. Planted lifts live in the sampler, not here.

CREATE TABLE experiments (
    experiment_id              INT PRIMARY KEY,
    experiment_key             TEXT UNIQUE NOT NULL,
    name                       TEXT NOT NULL,
    status                     TEXT NOT NULL,
    salt                       TEXT NOT NULL,
    traffic_pct                INT NOT NULL,
    attribution_window_seconds INT NOT NULL,
    started_at                 TIMESTAMPTZ NOT NULL,
    stopped_at                 TIMESTAMPTZ
);

CREATE TABLE variants (
    variant_id    SERIAL PRIMARY KEY,
    experiment_id INT NOT NULL REFERENCES experiments (experiment_id),
    variant_key   TEXT NOT NULL,
    weight        INT NOT NULL,
    UNIQUE (experiment_id, variant_key)
);

CREATE TABLE metrics (
    metric_id        SERIAL PRIMARY KEY,
    experiment_id    INT NOT NULL REFERENCES experiments (experiment_id),
    metric_key       TEXT NOT NULL,
    metric_type      TEXT NOT NULL,
    role             TEXT NOT NULL,
    min_effect       DOUBLE PRECISION NOT NULL,
    higher_is_better BOOLEAN NOT NULL,
    UNIQUE (experiment_id, metric_key)
);

INSERT INTO experiments (
    experiment_id, experiment_key, name, status, salt, traffic_pct, attribution_window_seconds, started_at
) VALUES
    (1, 'checkout_cta', 'Checkout CTA copy', 'running', 'checkout_cta_v1', 100, 3600, now() - INTERVAL '14 days'),
    (2, 'free_shipping', 'Free shipping threshold', 'running', 'free_shipping_v1', 100, 3600, now() - INTERVAL '14 days'),
    (3, 'aa_checkout', 'Checkout A/A', 'running', 'aa_checkout_v1', 100, 3600, now() - INTERVAL '14 days');

INSERT INTO variants (experiment_id, variant_key, weight) VALUES
    (1, 'control', 50), (1, 'treatment', 50),
    (2, 'control', 50), (2, 'treatment', 50),
    (3, 'control', 50), (3, 'treatment', 50);

INSERT INTO metrics (experiment_id, metric_key, metric_type, role, min_effect, higher_is_better)
SELECT experiment_id, metric_key, metric_type, role, min_effect, higher_is_better
FROM experiments
CROSS JOIN (
    VALUES
        ('conversion', 'binomial', 'primary', 0.02::float8, true),
        ('revenue', 'continuous', 'secondary', 0.0::float8, true),
        ('refund', 'binomial', 'guardrail', 0.02::float8, false)
) AS spec(metric_key, metric_type, role, min_effect, higher_is_better);
