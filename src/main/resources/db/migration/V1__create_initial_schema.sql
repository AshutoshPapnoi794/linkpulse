-- 1. USERS
CREATE TABLE users (
   id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

   email VARCHAR(320) NOT NULL,

   password_hash VARCHAR(255) NOT NULL,

   created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Email addresses are treated case-insensitively.
CREATE UNIQUE INDEX uk_users_email_lower
    ON users (LOWER(email));


-- 2. SHORT URLS
CREATE TABLE short_urls (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    user_id BIGINT NOT NULL,

    original_url TEXT NOT NULL,

    short_code VARCHAR(16) NOT NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    expires_at TIMESTAMPTZ,

    click_count BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_short_urls_user
        FOREIGN KEY (user_id)
            REFERENCES users(id)
            ON DELETE CASCADE,

    CONSTRAINT uk_short_urls_short_code
        UNIQUE (short_code),

    CONSTRAINT chk_short_urls_click_count
        CHECK (click_count >= 0),

    CONSTRAINT chk_short_urls_short_code
        CHECK (short_code ~ '^[A-Za-z0-9_-]{3,16}$')
    );

CREATE INDEX idx_short_urls_user_created
    ON short_urls (user_id, created_at DESC);


-- 3. CLICK EVENTS
CREATE TABLE click_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    short_url_id BIGINT NOT NULL,

    clicked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    referrer VARCHAR(2048),

    CONSTRAINT fk_click_events_short_url
      FOREIGN KEY (short_url_id)
          REFERENCES short_urls(id)
          ON DELETE CASCADE
);

CREATE INDEX idx_click_events_url_clicked
    ON click_events (short_url_id, clicked_at DESC);
