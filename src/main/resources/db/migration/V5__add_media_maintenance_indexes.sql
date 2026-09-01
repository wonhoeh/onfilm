CREATE INDEX idx_media_encode_job_status_completed
    ON media_encode_jobs (status, completed_at);

CREATE INDEX idx_media_outbox_status_published
    ON media_encode_outbox (status, published_at);

CREATE INDEX idx_media_outbox_status_created
    ON media_encode_outbox (status, created_at);
