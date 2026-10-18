CREATE TABLE program (
    id          BIGSERIAL     PRIMARY KEY,
    owner_id    BIGINT        NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(1000),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    version     BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT fk_program_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_program_owner_id ON program(owner_id);

-- One row per date with a plan; a date without a row is a rest day
CREATE TABLE program_day (
    id          BIGSERIAL     PRIMARY KEY,
    program_id  BIGINT        NOT NULL,
    day_date    DATE          NOT NULL,
    status      VARCHAR(255)  NOT NULL,
    notes       VARCHAR(2000),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP,
    version     BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT fk_program_day_program FOREIGN KEY (program_id) REFERENCES program(id) ON DELETE CASCADE,
    CONSTRAINT uq_program_day_program_date UNIQUE (program_id, day_date)
);

CREATE TABLE workout_part (
    id               BIGSERIAL     PRIMARY KEY,
    program_day_id   BIGINT        NOT NULL,
    order_index      INTEGER       NOT NULL,
    title            VARCHAR(150)  NOT NULL,
    description      VARCHAR(4000),
    duration_minutes INTEGER,
    completed        BOOLEAN       NOT NULL DEFAULT FALSE,
    result_notes     VARCHAR(2000),

    CONSTRAINT fk_workout_part_program_day FOREIGN KEY (program_day_id) REFERENCES program_day(id) ON DELETE CASCADE,
    -- Deferred: replacing a day's parts inserts the new ones before Hibernate deletes the old ones
    CONSTRAINT uq_workout_part_day_order UNIQUE (program_day_id, order_index) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT chk_workout_part_order_index CHECK (order_index >= 0),
    CONSTRAINT chk_workout_part_duration_minutes CHECK (duration_minutes > 0)
);

CREATE INDEX idx_workout_part_program_day_id ON workout_part(program_day_id);
