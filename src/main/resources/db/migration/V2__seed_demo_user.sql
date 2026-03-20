INSERT INTO auth_user (id, username, email, password_hash, role, enabled, created_at, updated_at)
VALUES (
    1,
    'demo',
    'demo@example.com',
    '$2y$10$R51kCmlq52SEJcVep3uDtOxTXp0r9jPwGa5oQQvRuMQA84PVwCjrK',
    'USER',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

INSERT INTO user_profile (id, user_id, display_name, bio, created_at, updated_at)
VALUES (
    1,
    1,
    'Demo User',
    'Session-backed example profile',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
