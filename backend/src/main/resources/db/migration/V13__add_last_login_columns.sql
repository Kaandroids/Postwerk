-- Track last login time and IP for security visibility
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN last_login_ip VARCHAR(45);
