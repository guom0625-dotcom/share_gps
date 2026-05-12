-- share_gps 초기 스키마
-- 모든 시각 컬럼은 unix epoch milliseconds (INTEGER)

CREATE TABLE users (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    role        TEXT NOT NULL CHECK(role IN ('parent','child')),
    created_at  INTEGER NOT NULL,
    revoked_at  INTEGER
);

CREATE TABLE auth_keys (
    key_hash      TEXT PRIMARY KEY,
    user_id       TEXT NOT NULL REFERENCES users(id),
    created_at    INTEGER NOT NULL,
    last_used_at  INTEGER,
    revoked_at    INTEGER
);
CREATE INDEX idx_auth_keys_user ON auth_keys(user_id);

CREATE TABLE locations (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      TEXT    NOT NULL REFERENCES users(id),
    lat          REAL    NOT NULL,
    lng          REAL    NOT NULL,
    accuracy     REAL,
    activity     TEXT,
    battery      INTEGER,
    recorded_at  INTEGER NOT NULL,
    received_at  INTEGER NOT NULL
);
CREATE INDEX idx_locations_user_time ON locations(user_id, recorded_at DESC);

CREATE TABLE current_location (
    user_id      TEXT PRIMARY KEY REFERENCES users(id),
    lat          REAL    NOT NULL,
    lng          REAL    NOT NULL,
    accuracy     REAL,
    activity     TEXT,
    battery      INTEGER,
    recorded_at  INTEGER NOT NULL
);

CREATE TABLE share_state (
    user_id         TEXT PRIMARY KEY REFERENCES users(id),
    mode            TEXT NOT NULL DEFAULT 'sharing'
                    CHECK(mode IN ('sharing','paused','off')),
    precision_mode  TEXT NOT NULL DEFAULT 'exact'
                    CHECK(precision_mode IN ('exact','approximate')),
    paused_until    INTEGER,
    updated_at      INTEGER NOT NULL
);
