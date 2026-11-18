CREATE TABLE catalog_processed_event (
    event_id TEXT PRIMARY KEY,
    notified_version BIGINT NOT NULL CHECK (notified_version >= 0),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
