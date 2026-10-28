-- One row per phone number, keyed like the app: "<country code>:<national significant number>".
-- version only grows, the app downloads every row with a newer version than it has seen.
-- Deleted rows stay as tombstones so phones drop them from their local copy too.
CREATE TABLE numbers (
    number TEXT PRIMARY KEY,
    name_en TEXT NOT NULL,
    name_ar TEXT NOT NULL,
    category TEXT NOT NULL,
    source TEXT NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL
) WITHOUT ROWID;

CREATE INDEX numbers_by_version ON numbers (version, number);
