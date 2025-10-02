CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(320) NOT NULL,
  full_name VARCHAR(120) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'TECH',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- unicity case insensitive
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_ci ON users ((lower(email)));

-- possible values: 'ADMIN', 'DISPATCHER', 'TECH'
ALTER TABLE users
  ADD CONSTRAINT chk_users_role CHECK (role IN ('ADMIN','DISPATCHER','TECH'));
