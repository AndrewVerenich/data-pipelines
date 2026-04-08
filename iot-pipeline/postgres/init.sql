-- Room configuration: source of truth for user intents; CDC to Kafka for stream processing.
CREATE TABLE IF NOT EXISTS room_config (
    room_id              VARCHAR(64) PRIMARY KEY,
    desired_temperature  DOUBLE PRECISION NOT NULL DEFAULT 22.0,
    climate_deadband     DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    hvac_mode            VARCHAR(16)      NOT NULL DEFAULT 'auto',
    security_mode        VARCHAR(16)      NOT NULL DEFAULT 'disarmed',
    lighting_mode        VARCHAR(16)      NOT NULL DEFAULT 'auto',
    lux_on_threshold     DOUBLE PRECISION NOT NULL DEFAULT 200.0,
    lux_off_threshold    DOUBLE PRECISION NOT NULL DEFAULT 350.0,
    updated_at           TIMESTAMPTZ      NOT NULL DEFAULT now()
);

INSERT INTO room_config (room_id, desired_temperature, climate_deadband, hvac_mode, security_mode, lighting_mode)
VALUES ('living-room', 22.0, 1.0, 'auto', 'disarmed', 'auto'),
       ('bedroom', 20.0, 1.0, 'auto', 'disarmed', 'auto'),
       ('kitchen', 21.0, 1.0, 'auto', 'disarmed', 'auto')
ON CONFLICT (room_id) DO NOTHING;
