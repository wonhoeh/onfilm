USE onfilm_api;

-- Q1. MoviePersonRepository.findFilmographyByPersonId(1L)
EXPLAIN ANALYZE
SELECT DISTINCT
       mp.id, mp.movie_id, mp.person_id, mp.sort_order, mp.is_private,
       m.movie_id, m.title, m.runtime, m.release_year,
       m.movie_url, m.thumbnail_url, m.age_rating,
       mpr.id, mpr.role, mpr.cast_type, mpr.character_name, mpr.sort_order
FROM movie_person mp
JOIN movie m ON m.movie_id = mp.movie_id
LEFT JOIN movie_person_role mpr ON mpr.movie_person_id = mp.id
WHERE mp.person_id = 1
ORDER BY mp.sort_order, mp.id;

-- Q2. MediaEncodeJobRepository.findTop100ByStatusInAndRequestedAtBefore(...)
EXPLAIN ANALYZE
SELECT j.*
FROM media_encode_jobs j
WHERE j.status IN ('REQUESTED', 'PROCESSING')
  AND j.requested_at < '2026-01-08 00:00:00'
LIMIT 100;

-- Q3. MediaEncodeJobRepository.deleteTerminalBefore(...)의 대상 탐색 부분
EXPLAIN ANALYZE
SELECT j.id
FROM media_encode_jobs j
WHERE j.status IN ('DONE', 'FAILED')
  AND j.completed_at < '2026-01-08 00:00:00';

-- Q4. MediaEncodeOutboxRepository.findClaimable(..., PageRequest.of(0, 100))
-- @Lock(PESSIMISTIC_WRITE)의 FOR UPDATE는 접근 경로 비교에 영향을 주지 않아 측정 SQL에서 제외한다.
EXPLAIN ANALYZE
SELECT o.*
FROM media_encode_outbox o
WHERE (o.status = 'PENDING' AND o.next_attempt_at <= '2026-01-08 00:00:00')
   OR (o.status = 'PUBLISHING' AND o.lease_until <= '2026-01-08 00:00:00')
ORDER BY o.created_at
LIMIT 100;

-- Q5. MediaEncodeOutboxRepository.deletePublishedBefore(...)의 대상 탐색 부분
EXPLAIN ANALYZE
SELECT o.id
FROM media_encode_outbox o
WHERE o.status = 'PUBLISHED'
  AND o.published_at < '2026-01-08 00:00:00';

-- Q6. MediaEncodeOutboxRepository.findOldestCreatedAtByStatus(PENDING)
EXPLAIN ANALYZE
SELECT MIN(o.created_at)
FROM media_encode_outbox o
WHERE o.status = 'PENDING';

SELECT table_name, index_name, seq_in_index, column_name, cardinality
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN (
      'movie_person',
      'movie_person_role',
      'media_encode_jobs',
      'media_encode_outbox'
  )
ORDER BY table_name, index_name, seq_in_index;
