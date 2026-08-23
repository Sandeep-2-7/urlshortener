

CREATE TABLE IF NOT EXISTS url_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code VARCHAR(10) NOT NULL UNIQUE,
    original_url VARCHAR(2048) NOT NULL,
    custom_alias BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NULL,
    click_count BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS click_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url_mapping_id BIGINT NOT NULL,
    clicked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    referrer VARCHAR(500) NULL,
    user_agent VARCHAR(500) NULL,
    CONSTRAINT fk_click_url FOREIGN KEY (url_mapping_id) REFERENCES url_mapping(id)
);

CREATE INDEX idx_short_code ON url_mapping(short_code);
CREATE INDEX idx_click_url_mapping ON click_event(url_mapping_id);

ALTER TABLE url_mapping ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;