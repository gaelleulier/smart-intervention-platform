ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(100);

-- Initialize password hashes for existing rows (if any)
UPDATE users
SET password_hash = '$2b$10$B38EZG7TD6xY3DIGVz3N3uUxqNTLI7LgHcPoM72X/hbmqOHy9eTx6'
WHERE password_hash IS NULL;

ALTER TABLE users
    ALTER COLUMN password_hash SET NOT NULL;

-- Seed a default administrator account if none exists
INSERT INTO users (email, full_name, role, password_hash)
VALUES (
    'admin@sip.local',
    'System Administrator',
    'ADMIN',
    '$2b$10$vYb341lBoIRIQRoM.p6EEu.VrYWNO6vNwW4fI9Lv.Os9RcW5XMwDu'
)
ON CONFLICT (lower(email)) DO NOTHING;
