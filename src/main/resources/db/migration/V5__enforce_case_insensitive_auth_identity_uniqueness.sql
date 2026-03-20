CREATE UNIQUE INDEX ux_auth_user_username_lower
    ON auth_user (LOWER(username));

CREATE UNIQUE INDEX ux_auth_user_email_lower
    ON auth_user (LOWER(email));
