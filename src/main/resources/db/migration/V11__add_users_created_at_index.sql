-- Index to support efficient admin queries ordered or filtered by registration date.
CREATE INDEX idx_users_created_at ON users (created_at DESC);
