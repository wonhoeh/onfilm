-- MySQL 8.4 / OnFilm API V1~V4 index baseline dataset
-- This script is destructive only to the dedicated benchmark database/container.

USE onfilm_api;

CREATE TABLE benchmark_sequence (
    n INT NOT NULL,
    PRIMARY KEY (n)
) ENGINE = InnoDB;

INSERT INTO benchmark_sequence (n)
SELECT d5.n * 100000 + d4.n * 10000 + d3.n * 1000
     + d2.n * 100 + d1.n * 10 + d0.n
FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
CROSS JOIN (SELECT 0 n UNION ALL SELECT 1) d5
WHERE d5.n * 100000 + d4.n * 10000 + d3.n * 1000
    + d2.n * 100 + d1.n * 10 + d0.n < 200000;

INSERT INTO person (
    id, public_id, name, birth_date, birth_place, one_line_intro,
    profile_image_url, filmography_file_key,
    filmography_private, gallery_private
)
SELECT n + 1,
       CONCAT('00000000-0000-0000-', LPAD(n, 4, '0'), '-', LPAD(n + 1, 12, '0')),
       CONCAT('benchmark-person-', n + 1),
       NULL, NULL, NULL, NULL, NULL, 0, 0
FROM benchmark_sequence
WHERE n < 1000;

INSERT INTO movie (
    movie_id, title, runtime, release_year,
    movie_url, thumbnail_url, age_rating
)
SELECT n + 1,
       CONCAT('benchmark-movie-', n + 1),
       120,
       2026,
       CONCAT('movie/', n + 1, '/index.m3u8'),
       NULL,
       'ALL'
FROM benchmark_sequence
WHERE n < 100000;

INSERT INTO movie_person (
    id, movie_id, person_id, sort_order, is_private
)
SELECT n + 1,
       n + 1,
       MOD(n, 1000) + 1,
       FLOOR(n / 1000),
       0
FROM benchmark_sequence
WHERE n < 100000;

INSERT INTO movie_person_role (
    id, movie_person_id, role, cast_type, character_name, sort_order
)
SELECT n + 1,
       n + 1,
       'ACTOR',
       'LEAD',
       CONCAT('character-', n + 1),
       0
FROM benchmark_sequence
WHERE n < 100000;

INSERT INTO media_encode_jobs (
    id, version, request_id, movie_id, requested_by_user_id,
    job_type, preset,
    source_bucket, source_key, target_bucket, target_key,
    source_content_type, target_content_type,
    status, requested_at, started_at, completed_at,
    failure_code, failure_reason
)
SELECT CONCAT('10000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
       0,
       CONCAT('11000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
       MOD(n, 100000) + 1,
       MOD(n, 1000) + 1,
       'MOVIE',
       'VIDEO_HLS_720P_2500K_AAC_96K',
       'benchmark-bucket',
       CONCAT('movie/', MOD(n, 100000) + 1, '/raw/', n + 1, '.mp4'),
       'benchmark-bucket',
       CONCAT('movie/', MOD(n, 100000) + 1, '/hls/', n + 1, '/index.m3u8'),
       'video/mp4',
       'application/vnd.apple.mpegurl',
       CASE
           WHEN MOD(n, 10) < 6 THEN 'DONE'
           WHEN MOD(n, 10) = 6 THEN 'FAILED'
           WHEN MOD(n, 10) < 9 THEN 'REQUESTED'
           ELSE 'PROCESSING'
       END,
       TIMESTAMPADD(MINUTE, n, '2026-01-01 00:00:00'),
       CASE
           WHEN MOD(n, 10) = 9
               THEN TIMESTAMPADD(MINUTE, n + 1, '2026-01-01 00:00:00')
           ELSE NULL
       END,
       CASE
           WHEN MOD(n, 10) <= 6
               THEN TIMESTAMPADD(MINUTE, n + 2, '2026-01-01 00:00:00')
           ELSE NULL
       END,
       CASE WHEN MOD(n, 10) = 6 THEN 'BENCHMARK_FAILURE' ELSE NULL END,
       CASE WHEN MOD(n, 10) = 6 THEN 'benchmark failure reason' ELSE NULL END
FROM benchmark_sequence;

INSERT INTO media_encode_outbox (
    id, version, job_id, schema_version, payload,
    status, attempts, created_at, next_attempt_at,
    lease_until, published_at, last_error
)
SELECT CONCAT('20000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
       0,
       CONCAT('10000000-0000-0000-0000-', LPAD(n + 1, 12, '0')),
       1,
       '{"schemaVersion":1}',
       CASE
           WHEN MOD(n, 20) < 16 THEN 'PUBLISHED'
           WHEN MOD(n, 20) < 18 THEN 'PENDING'
           WHEN MOD(n, 20) = 18 THEN 'PUBLISHING'
           ELSE 'DEAD'
       END,
       1,
       TIMESTAMPADD(MINUTE, n, '2026-01-01 00:00:00'),
       TIMESTAMPADD(MINUTE, n + 5, '2026-01-01 00:00:00'),
       CASE
           WHEN MOD(n, 20) = 18
               THEN TIMESTAMPADD(MINUTE, n + 10, '2026-01-01 00:00:00')
           ELSE NULL
       END,
       CASE
           WHEN MOD(n, 20) < 16
               THEN TIMESTAMPADD(MINUTE, n + 1, '2026-01-01 00:00:00')
           ELSE NULL
       END,
       CASE WHEN MOD(n, 20) = 19 THEN 'benchmark dead event' ELSE NULL END
FROM benchmark_sequence;

ANALYZE TABLE person;
ANALYZE TABLE movie;
ANALYZE TABLE movie_person;
ANALYZE TABLE movie_person_role;
ANALYZE TABLE media_encode_jobs;
ANALYZE TABLE media_encode_outbox;

SELECT 'person' table_name, COUNT(*) row_count FROM person
UNION ALL SELECT 'movie', COUNT(*) FROM movie
UNION ALL SELECT 'movie_person', COUNT(*) FROM movie_person
UNION ALL SELECT 'movie_person_role', COUNT(*) FROM movie_person_role
UNION ALL SELECT 'media_encode_jobs', COUNT(*) FROM media_encode_jobs
UNION ALL SELECT 'media_encode_outbox', COUNT(*) FROM media_encode_outbox;
