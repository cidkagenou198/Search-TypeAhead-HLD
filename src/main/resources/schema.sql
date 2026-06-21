CREATE TABLE IF NOT EXISTS queries (
    query TEXT PRIMARY KEY,
    count INTEGER DEFAULT 0,
    last_searched REAL DEFAULT 0
);
