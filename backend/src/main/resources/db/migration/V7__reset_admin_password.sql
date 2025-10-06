UPDATE users
SET password_hash = '$2b$10$AeS3LwKeoIoAMIB2q0N7Q.xSon5w7ltIoHCVWjEOlZ7IDIggSKRSy'
WHERE lower(email) = 'admin@sip.local'
  AND password_hash IN (
    '$2b$10$vYb341lBoIRIQRoM.p6EEu.VrYWNO6vNwW4fI9Lv.Os9RcW5XMwDu',
    '$2b$10$B38EZG7TD6xY3DIGVz3N3uUxqNTLI7LgHcPoM72X/hbmqOHy9eTx6'
  );
