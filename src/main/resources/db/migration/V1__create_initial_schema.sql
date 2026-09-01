CREATE TABLE genre (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(60) NOT NULL,
    normalized VARCHAR(60) NOT NULL,
    is_active BIT NOT NULL,
    CONSTRAINT pk_genre PRIMARY KEY (id),
    CONSTRAINT uk_genre_normalized UNIQUE (normalized)
) ENGINE = InnoDB;

CREATE TABLE person (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(36) NOT NULL,
    name VARCHAR(60) NOT NULL,
    birth_date DATE NULL,
    birth_place VARCHAR(80) NULL,
    one_line_intro VARCHAR(120) NULL,
    profile_image_url VARCHAR(512) NULL,
    filmography_file_key VARCHAR(512) NULL,
    filmography_private BIT NOT NULL,
    gallery_private BIT NOT NULL,
    CONSTRAINT pk_person PRIMARY KEY (id),
    CONSTRAINT uk_person_public_id UNIQUE (public_id)
) ENGINE = InnoDB;

CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    email VARCHAR(254) NOT NULL,
    encoded_password VARCHAR(255) NOT NULL,
    username VARCHAR(20) NOT NULL,
    username_normalized VARCHAR(20) NOT NULL,
    avatar_image_key VARCHAR(512) NULL,
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uk_users_person UNIQUE (person_id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username_normalized UNIQUE (username_normalized),
    CONSTRAINT fk_users_person
        FOREIGN KEY (person_id) REFERENCES person (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(43) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    last_used_at DATETIME(6) NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    INDEX idx_refresh_token_user_id (user_id),
    INDEX idx_refresh_token_expires_at (expires_at),
    INDEX idx_refresh_token_revoked_at (revoked_at)
) ENGINE = InnoDB;

CREATE TABLE movie (
    movie_id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    runtime INT NOT NULL,
    release_year INT NOT NULL,
    movie_url VARCHAR(512) NULL,
    thumbnail_url VARCHAR(512) NULL,
    age_rating ENUM ('AGE_12', 'AGE_15', 'AGE_18', 'ALL') NOT NULL,
    CONSTRAINT pk_movie PRIMARY KEY (movie_id)
) ENGINE = InnoDB;

CREATE TABLE movie_likes (
    movie_movie_id BIGINT NOT NULL,
    likes VARCHAR(255) NULL,
    CONSTRAINT fk_movie_likes_movie
        FOREIGN KEY (movie_movie_id) REFERENCES movie (movie_id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE movie_person (
    id BIGINT NOT NULL AUTO_INCREMENT,
    movie_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    is_private BIT NOT NULL,
    CONSTRAINT pk_movie_person PRIMARY KEY (id),
    CONSTRAINT uk_movie_person_movie_id_person_id UNIQUE (movie_id, person_id),
    CONSTRAINT fk_movie_person_movie
        FOREIGN KEY (movie_id) REFERENCES movie (movie_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_movie_person_person
        FOREIGN KEY (person_id) REFERENCES person (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE movie_person_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    movie_person_id BIGINT NOT NULL,
    role ENUM ('ACTOR', 'DIRECTOR', 'WRITER') NOT NULL,
    cast_type ENUM ('CAMEO', 'LEAD', 'SUPPORTING') NULL,
    character_name VARCHAR(100) NULL,
    sort_order INT NULL,
    CONSTRAINT pk_movie_person_role PRIMARY KEY (id),
    CONSTRAINT uk_movie_person_role_participation_role
        UNIQUE (movie_person_id, role),
    CONSTRAINT ck_movie_person_role_actor_fields CHECK (
        (role = 'ACTOR' AND cast_type IS NOT NULL)
        OR (
            role IN ('DIRECTOR', 'WRITER')
            AND cast_type IS NULL
            AND character_name IS NULL
        )
    ),
    CONSTRAINT fk_movie_person_role_movie_person
        FOREIGN KEY (movie_person_id) REFERENCES movie_person (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE movie_genre (
    id BIGINT NOT NULL AUTO_INCREMENT,
    movie_id BIGINT NOT NULL,
    genre_id BIGINT NULL,
    raw_text VARCHAR(60) NOT NULL,
    normalized_text VARCHAR(60) NOT NULL,
    CONSTRAINT pk_movie_genre PRIMARY KEY (id),
    CONSTRAINT uk_movie_genre_normalized UNIQUE (movie_id, normalized_text),
    INDEX idx_movie_genre_movie (movie_id),
    INDEX idx_movie_genre_genre (genre_id),
    INDEX idx_movie_genre_norm (normalized_text),
    CONSTRAINT fk_movie_genre_movie
        FOREIGN KEY (movie_id) REFERENCES movie (movie_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_movie_genre_genre
        FOREIGN KEY (genre_id) REFERENCES genre (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE trailer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    movie_id BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    sort_order INT NULL,
    CONSTRAINT pk_trailer PRIMARY KEY (id),
    CONSTRAINT uk_trailer_movie_storage_key UNIQUE (movie_id, storage_key),
    CONSTRAINT fk_trailer_movie
        FOREIGN KEY (movie_id) REFERENCES movie (movie_id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE person_sns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    type ENUM ('ETC', 'INSTAGRAM', 'TIKTOK', 'YOUTUBE') NOT NULL,
    url VARCHAR(512) NOT NULL,
    CONSTRAINT pk_person_sns PRIMARY KEY (id),
    CONSTRAINT uk_person_sns_url UNIQUE (person_id, url),
    CONSTRAINT fk_person_sns_person
        FOREIGN KEY (person_id) REFERENCES person (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE profile_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    raw_text VARCHAR(30) NOT NULL,
    normalized VARCHAR(30) NOT NULL,
    sort_order INT NULL,
    CONSTRAINT pk_profile_tag PRIMARY KEY (id),
    CONSTRAINT uk_person_tag UNIQUE (person_id, normalized),
    CONSTRAINT fk_profile_tag_person
        FOREIGN KEY (person_id) REFERENCES person (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE person_gallery (
    person_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    image_key VARCHAR(512) NOT NULL,
    is_private BIT NOT NULL,
    CONSTRAINT pk_person_gallery PRIMARY KEY (sort_order, person_id),
    CONSTRAINT fk_person_gallery_person
        FOREIGN KEY (person_id) REFERENCES person (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE storyboard_project (
    id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    sort_order INT NULL,
    CONSTRAINT pk_storyboard_project PRIMARY KEY (id),
    CONSTRAINT fk_storyboard_project_person
        FOREIGN KEY (person_id) REFERENCES person (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE storyboard_scene (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    title VARCHAR(120) NULL,
    script_html TINYTEXT NULL,
    sort_order INT NULL,
    CONSTRAINT pk_storyboard_scene PRIMARY KEY (id),
    CONSTRAINT fk_storyboard_scene_project
        FOREIGN KEY (project_id) REFERENCES storyboard_project (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE storyboard_card (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scene_id BIGINT NOT NULL,
    image_key VARCHAR(512) NULL,
    sort_order INT NULL,
    CONSTRAINT pk_storyboard_card PRIMARY KEY (id),
    CONSTRAINT fk_storyboard_card_scene
        FOREIGN KEY (scene_id) REFERENCES storyboard_scene (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE media_upload_requests (
    id VARCHAR(36) NOT NULL,
    version BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    movie_id BIGINT NOT NULL,
    job_type ENUM ('MOVIE', 'THUMBNAIL', 'TRAILER') NOT NULL,
    bucket VARCHAR(63) NOT NULL,
    source_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    status ENUM ('COMPLETED', 'EXPIRED', 'ISSUED') NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    job_id VARCHAR(36) NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT pk_media_upload_requests PRIMARY KEY (id),
    INDEX idx_media_upload_user (requested_by_user_id),
    INDEX idx_media_upload_status_expires (status, expires_at)
) ENGINE = InnoDB;

CREATE TABLE media_encode_jobs (
    id VARCHAR(36) NOT NULL,
    version BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    movie_id BIGINT NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    job_type ENUM ('MOVIE', 'THUMBNAIL', 'TRAILER') NOT NULL,
    preset ENUM (
        'THUMBNAIL_1280X720',
        'VIDEO_HLS_720P_2500K_AAC_96K'
    ) NOT NULL,
    source_bucket VARCHAR(63) NOT NULL,
    source_key VARCHAR(512) NOT NULL,
    target_bucket VARCHAR(63) NOT NULL,
    target_key VARCHAR(512) NOT NULL,
    source_content_type VARCHAR(128) NOT NULL,
    target_content_type VARCHAR(128) NOT NULL,
    status ENUM ('DONE', 'FAILED', 'PROCESSING', 'REQUESTED') NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    failure_code VARCHAR(64) NULL,
    failure_reason VARCHAR(1000) NULL,
    CONSTRAINT pk_media_encode_jobs PRIMARY KEY (id),
    CONSTRAINT uk_media_encode_job_request UNIQUE (request_id),
    INDEX idx_media_encode_job_user_status (requested_by_user_id, status),
    INDEX idx_media_encode_job_status_requested (status, requested_at)
) ENGINE = InnoDB;

CREATE TABLE media_encode_outbox (
    id VARCHAR(36) NOT NULL,
    version BIGINT NOT NULL,
    job_id VARCHAR(36) NOT NULL,
    schema_version INT NOT NULL,
    payload TEXT NOT NULL,
    status ENUM ('DEAD', 'PENDING', 'PUBLISHED', 'PUBLISHING') NOT NULL,
    attempts INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_until DATETIME(6) NULL,
    published_at DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    CONSTRAINT pk_media_encode_outbox PRIMARY KEY (id),
    CONSTRAINT uk_media_encode_outbox_job UNIQUE (job_id),
    INDEX idx_media_outbox_dispatch (status, next_attempt_at)
) ENGINE = InnoDB;
