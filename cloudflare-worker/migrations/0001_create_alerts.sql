CREATE TABLE IF NOT EXISTS alerts (
  id TEXT PRIMARY KEY,
  symbol TEXT NOT NULL,
  condition TEXT NOT NULL,
  target_price REAL NOT NULL,
  price_type TEXT NOT NULL DEFAULT 'LAST',
  status TEXT NOT NULL DEFAULT 'ACTIVE',
  created_at TEXT NOT NULL,
  triggered_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_alerts_status_symbol
ON alerts(status, symbol);
