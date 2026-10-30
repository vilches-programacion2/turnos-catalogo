CREATE TABLE professional_category (
    id BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE professional (
    id BIGINT PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES professional_category(id),
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_professional_category ON professional(category_id);

CREATE TABLE weekly_schedule (
    id BIGINT PRIMARY KEY,
    professional_id BIGINT NOT NULL REFERENCES professional(id),
    day_of_week VARCHAR(9) NOT NULL CHECK (
        day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    ),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_duration_minutes INTEGER NOT NULL CHECK (slot_duration_minutes > 0),
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_weekly_schedule_professional_day ON weekly_schedule(professional_id, day_of_week);

CREATE TABLE catalog_state (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    applied_version BIGINT NOT NULL CHECK (applied_version >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
