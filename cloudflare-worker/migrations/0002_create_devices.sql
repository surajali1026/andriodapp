CREATE TABLE IF NOT EXISTS devices (
  id TEXT PRIMARY KEY,
  fcm_token TEXT NOT NULL,
  platform TEXT NOT NULL DEFAULT 'android',
  updated_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_devices_fcm_token
ON devices(fcm_token);
