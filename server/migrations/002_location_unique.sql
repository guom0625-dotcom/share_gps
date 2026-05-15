-- recorded_at 중복 제거 후 unique index 추가 (WS + batch 이중 저장 방지)
DELETE FROM locations WHERE rowid NOT IN (
    SELECT MIN(rowid) FROM locations GROUP BY user_id, recorded_at
);
CREATE UNIQUE INDEX idx_locations_user_recorded ON locations(user_id, recorded_at);
