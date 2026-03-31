-- ============================================================
-- 인덱스 성능 테스트용 더미 데이터
-- 목적: movie_person 테이블에 ~50,000행 추가
--       인덱스 유무에 따른 풀스캔 vs 인덱스 스캔 성능 비교
--
-- 구조:
--   - 가짜 person 500명 (FK용, 로그인 불필요)
--   - movie 5,000편
--   - movie_person 50,000행 (영화 5,000편 × 10명)
--   - 테스트 대상 person(@person_id)의 20행은 기존 더미 데이터에 있음
--
-- 실행 방법:
--   DataGrip 기준: 이 파일 전체 선택 → Run
--   (DELIMITER는 DataGrip에서 자동 처리됨)
-- ============================================================

-- ============================================================
-- 정리 시 사용 (재실행 전 주석 해제)
-- ============================================================
-- SET FOREIGN_KEY_CHECKS = 0;
-- DELETE FROM movie_person WHERE movie_id IN (SELECT id FROM movie WHERE title LIKE 'idx더미영화%');
-- DELETE FROM movie         WHERE title LIKE 'idx더미영화%';
-- DELETE FROM person        WHERE name  LIKE 'idx더미배우%';
-- SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 프로시저 생성 후 실행
-- ============================================================
DROP PROCEDURE IF EXISTS insert_index_dummy;

CREATE PROCEDURE insert_index_dummy()
BEGIN
  DECLARE i        INT    DEFAULT 1;
  DECLARE j        INT    DEFAULT 0;
  DECLARE v_person_base BIGINT DEFAULT 0;
  DECLARE v_movie_base  BIGINT DEFAULT 0;

  SET autocommit = 0;

  -- ① 가짜 person 500명
  --    public_id는 UUID()로 유니크하게 생성
  SET i = 1;
  WHILE i <= 500 DO
    INSERT INTO person (name, birth_date, birth_place, one_line_intro,
                        profile_image_url, filmography_file_key,
                        public_id, filmography_private, gallery_private)
    VALUES (
      CONCAT('idx더미배우', LPAD(i, 4, '0')),
      '1990-01-01',
      '서울',
      '인덱스 테스트용 더미',
      NULL, NULL,
      UUID(),
      false, false
    );
    IF i = 1 THEN
      SET v_person_base = LAST_INSERT_ID();
    END IF;
    SET i = i + 1;
  END WHILE;
  COMMIT;

  -- ② movie 5,000편
  SET i = 1;
  WHILE i <= 5000 DO
    INSERT INTO movie (title, runtime, release_year, movie_url, thumbnail_url, age_rating)
    VALUES (
      CONCAT('idx더미영화', LPAD(i, 5, '0')),
      90 + (i MOD 60),
      2000 + (i MOD 25),
      CONCAT('movie/idx/', LPAD(i, 5, '0'), '.mp4'),
      NULL,
      ELT((i MOD 4) + 1, 'ALL', 'AGE_12', 'AGE_15', 'AGE_18')
    );
    IF i = 1 THEN
      SET v_movie_base = LAST_INSERT_ID();
    END IF;
    SET i = i + 1;
  END WHILE;
  COMMIT;

  -- ③ movie_person 50,000행
  --    영화 5,000편 × 10명 (가짜 person 500명 순환)
  --    테스트 대상 person(@person_id)은 여기에 포함되지 않음
  --    → 기존 20행 외에 노이즈 데이터만 추가
  SET i = 0;
  WHILE i < 5000 DO
    SET j = 0;
    WHILE j < 10 DO
      INSERT INTO movie_person (movie_id, person_id, role, cast_type, character_name, sort_order, is_private)
      VALUES (
        v_movie_base + i,
        v_person_base + ((i * 10 + j) MOD 500),
        'ACTOR',
        ELT((j MOD 3) + 1, 'LEAD', 'SUPPORTING', 'CAMEO'),
        IF(j MOD 3 = 2, NULL, CONCAT('캐릭터', LPAD(j + 1, 2, '0'))),
        j,
        false
      );
      SET j = j + 1;
    END WHILE;
    -- 500편마다 중간 커밋 (메모리 부담 분산)
    IF i MOD 500 = 499 THEN COMMIT; END IF;
    SET i = i + 1;
  END WHILE;
  COMMIT;

  SET autocommit = 1;
END;

CALL insert_index_dummy();
DROP PROCEDURE IF EXISTS insert_index_dummy;
