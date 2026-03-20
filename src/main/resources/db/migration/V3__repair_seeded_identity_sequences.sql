SELECT setval(
    pg_get_serial_sequence('auth_user', 'id'),
    COALESCE((SELECT MAX(id) FROM auth_user), 1),
    true
);

SELECT setval(
    pg_get_serial_sequence('user_profile', 'id'),
    COALESCE((SELECT MAX(id) FROM user_profile), 1),
    true
);
