ALTER TABLE refresh_tokens
    MODIFY COLUMN token_hash VARCHAR(43)
        CHARACTER SET ascii
        COLLATE ascii_bin
        NOT NULL;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
        ON DELETE CASCADE;
