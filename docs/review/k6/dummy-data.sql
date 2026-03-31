-- ============================================================
-- k6 성능 테스트용 더미 데이터
-- 대상: prod RDS (MySQL)
-- 실행 전 확인: USE onfilm; (또는 실제 DB명으로 변경)
-- ============================================================

-- ============================================================
-- 0. 기존 더미 데이터 정리 (재실행 시 사용)
-- ============================================================
-- SET FOREIGN_KEY_CHECKS = 0;
-- DELETE FROM storyboard_card;
-- DELETE FROM storyboard_scene;
-- DELETE FROM storyboard_project;
-- DELETE FROM person_gallery;
-- DELETE FROM movie_person;
-- DELETE FROM movie;
-- DELETE FROM person_sns;
-- DELETE FROM profile_tag;
-- DELETE FROM person WHERE public_id = 'k6-test-person-uuid-0001';
-- SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 1. person 1명
-- ============================================================
INSERT INTO person (name, birth_date, birth_place, one_line_intro, profile_image_url,
                    filmography_file_key, public_id, filmography_private, gallery_private)
VALUES ('k6 테스트 배우', '1990-01-01', '서울', 'k6 성능 테스트용 더미 배우입니다.',
        NULL, NULL, 'k6-test-person-uuid-0001', false, false);

SET @person_id = LAST_INSERT_ID();

-- ============================================================
-- 2. person_sns 5개
-- ============================================================
INSERT INTO person_sns (id, type, url, person_id) VALUES
(9001, 'INSTAGRAM', 'https://instagram.com/k6test1',  @person_id),
(9002, 'YOUTUBE',   'https://youtube.com/k6test2',    @person_id),
(9003, 'TWITTER',   'https://twitter.com/k6test3',    @person_id),
(9004, 'FACEBOOK',  'https://facebook.com/k6test4',   @person_id),
(9005, 'NAVER',     'https://blog.naver.com/k6test5', @person_id);

-- ============================================================
-- 3. profile_tag 10개
-- ============================================================
INSERT INTO profile_tag (id, person_id, raw_text, normalized) VALUES
(9001, @person_id, '액션',   '액션'),
(9002, @person_id, '멜로',   '멜로'),
(9003, @person_id, '코미디', '코미디'),
(9004, @person_id, '드라마', '드라마'),
(9005, @person_id, '스릴러', '스릴러'),
(9006, @person_id, '공포',   '공포'),
(9007, @person_id, 'SF',     'sf'),
(9008, @person_id, '판타지', '판타지'),
(9009, @person_id, '범죄',   '범죄'),
(9010, @person_id, '다큐',   '다큐');

-- ============================================================
-- 4. person_gallery 30개
-- ============================================================
INSERT INTO person_gallery (person_id, image_key, is_private, sort_order) VALUES
(@person_id, 'gallery/k6/img_01.jpg', false, 0),
(@person_id, 'gallery/k6/img_02.jpg', false, 1),
(@person_id, 'gallery/k6/img_03.jpg', false, 2),
(@person_id, 'gallery/k6/img_04.jpg', false, 3),
(@person_id, 'gallery/k6/img_05.jpg', false, 4),
(@person_id, 'gallery/k6/img_06.jpg', false, 5),
(@person_id, 'gallery/k6/img_07.jpg', false, 6),
(@person_id, 'gallery/k6/img_08.jpg', false, 7),
(@person_id, 'gallery/k6/img_09.jpg', false, 8),
(@person_id, 'gallery/k6/img_10.jpg', false, 9),
(@person_id, 'gallery/k6/img_11.jpg', false, 10),
(@person_id, 'gallery/k6/img_12.jpg', false, 11),
(@person_id, 'gallery/k6/img_13.jpg', false, 12),
(@person_id, 'gallery/k6/img_14.jpg', false, 13),
(@person_id, 'gallery/k6/img_15.jpg', false, 14),
(@person_id, 'gallery/k6/img_16.jpg', false, 15),
(@person_id, 'gallery/k6/img_17.jpg', false, 16),
(@person_id, 'gallery/k6/img_18.jpg', false, 17),
(@person_id, 'gallery/k6/img_19.jpg', false, 18),
(@person_id, 'gallery/k6/img_20.jpg', false, 19),
(@person_id, 'gallery/k6/img_21.jpg', false, 20),
(@person_id, 'gallery/k6/img_22.jpg', false, 21),
(@person_id, 'gallery/k6/img_23.jpg', false, 22),
(@person_id, 'gallery/k6/img_24.jpg', false, 23),
(@person_id, 'gallery/k6/img_25.jpg', false, 24),
(@person_id, 'gallery/k6/img_26.jpg', false, 25),
(@person_id, 'gallery/k6/img_27.jpg', false, 26),
(@person_id, 'gallery/k6/img_28.jpg', false, 27),
(@person_id, 'gallery/k6/img_29.jpg', false, 28),
(@person_id, 'gallery/k6/img_30.jpg', false, 29);

-- ============================================================
-- 5. movie 20개 + movie_person 20개
-- ============================================================
INSERT INTO movie (title, runtime, release_year, movie_url, thumbnail_url, age_rating) VALUES
('테스트 영화 01', 120, 2020, 'movie/k6/movie_01.mp4', NULL, 'ALL'),
('테스트 영화 02', 115, 2020, 'movie/k6/movie_02.mp4', NULL, 'AGE_12'),
('테스트 영화 03', 130, 2021, 'movie/k6/movie_03.mp4', NULL, 'AGE_15'),
('테스트 영화 04', 105, 2021, 'movie/k6/movie_04.mp4', NULL, 'ALL'),
('테스트 영화 05', 98,  2021, 'movie/k6/movie_05.mp4', NULL, 'AGE_18'),
('테스트 영화 06', 145, 2022, 'movie/k6/movie_06.mp4', NULL, 'ALL'),
('테스트 영화 07', 112, 2022, 'movie/k6/movie_07.mp4', NULL, 'AGE_12'),
('테스트 영화 08', 125, 2022, 'movie/k6/movie_08.mp4', NULL, 'AGE_15'),
('테스트 영화 09', 90,  2022, 'movie/k6/movie_09.mp4', NULL, 'ALL'),
('테스트 영화 10', 118, 2023, 'movie/k6/movie_10.mp4', NULL, 'AGE_12'),
('테스트 영화 11', 132, 2023, 'movie/k6/movie_11.mp4', NULL, 'ALL'),
('테스트 영화 12', 108, 2023, 'movie/k6/movie_12.mp4', NULL, 'AGE_15'),
('테스트 영화 13', 140, 2023, 'movie/k6/movie_13.mp4', NULL, 'AGE_18'),
('테스트 영화 14', 95,  2023, 'movie/k6/movie_14.mp4', NULL, 'ALL'),
('테스트 영화 15', 122, 2024, 'movie/k6/movie_15.mp4', NULL, 'AGE_12'),
('테스트 영화 16', 135, 2024, 'movie/k6/movie_16.mp4', NULL, 'AGE_15'),
('테스트 영화 17', 110, 2024, 'movie/k6/movie_17.mp4', NULL, 'ALL'),
('테스트 영화 18', 100, 2024, 'movie/k6/movie_18.mp4', NULL, 'AGE_12'),
('테스트 영화 19', 128, 2025, 'movie/k6/movie_19.mp4', NULL, 'AGE_15'),
('테스트 영화 20', 117, 2025, 'movie/k6/movie_20.mp4', NULL, 'ALL');

-- movie_person: 위 20개 영화에 각각 1:1로 연결
-- MySQL 멀티 ROW INSERT 시 LAST_INSERT_ID()는 첫 번째 행의 ID를 반환
SET @movie_base = LAST_INSERT_ID();

INSERT INTO movie_person (movie_id, person_id, role, cast_type, character_name, sort_order, is_private) VALUES
(@movie_base +  0, @person_id, 'ACTOR', 'LEAD',       '캐릭터01', 0, false),
(@movie_base +  1, @person_id, 'ACTOR', 'LEAD',       '캐릭터02', 0, false),
(@movie_base +  2, @person_id, 'ACTOR', 'SUPPORTING', '캐릭터03', 0, false),
(@movie_base +  3, @person_id, 'ACTOR', 'LEAD',       '캐릭터04', 0, false),
(@movie_base +  4, @person_id, 'ACTOR', 'SUPPORTING', '캐릭터05', 0, false),
(@movie_base +  5, @person_id, 'ACTOR', 'LEAD',       '캐릭터06', 0, false),
(@movie_base +  6, @person_id, 'ACTOR', 'CAMEO',      NULL,        0, false),
(@movie_base +  7, @person_id, 'ACTOR', 'LEAD',       '캐릭터08', 0, false),
(@movie_base +  8, @person_id, 'ACTOR', 'SUPPORTING', '캐릭터09', 0, false),
(@movie_base +  9, @person_id, 'ACTOR', 'LEAD',       '캐릭터10', 0, false),
(@movie_base + 10, @person_id, 'ACTOR', 'LEAD',       '캐릭터11', 0, false),
(@movie_base + 11, @person_id, 'ACTOR', 'SUPPORTING', '캐릭터12', 0, false),
(@movie_base + 12, @person_id, 'ACTOR', 'LEAD',       '캐릭터13', 0, false),
(@movie_base + 13, @person_id, 'ACTOR', 'CAMEO',      NULL,        0, false),
(@movie_base + 14, @person_id, 'ACTOR', 'LEAD',       '캐릭터15', 0, false),
(@movie_base + 15, @person_id, 'ACTOR', 'SUPPORTING', '캐릭터16', 0, false),
(@movie_base + 16, @person_id, 'ACTOR', 'LEAD',       '캐릭터17', 0, false),
(@movie_base + 17, @person_id, 'ACTOR', 'LEAD',       '캐릭터18', 0, false),
(@movie_base + 18, @person_id, 'ACTOR', 'SUPPORTING', '캐릭터19', 0, false),
(@movie_base + 19, @person_id, 'ACTOR', 'LEAD',       '캐릭터20', 0, false);

-- ============================================================
-- 6. storyboard_project 20개 (각 프로젝트에 scene 10개씩)
-- ============================================================

-- project 1
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 01', @person_id, 0);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 2
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 02', @person_id, 1);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 3
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 03', @person_id, 2);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 4
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 04', @person_id, 3);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 5
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 05', @person_id, 4);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 6
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 06', @person_id, 5);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 7
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 07', @person_id, 6);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 8
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 08', @person_id, 7);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 9
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 09', @person_id, 8);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 10
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 10', @person_id, 9);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 11
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 11', @person_id, 10);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 12
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 12', @person_id, 11);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 13
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 13', @person_id, 12);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 14
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 14', @person_id, 13);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 15
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 15', @person_id, 14);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 16
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 16', @person_id, 15);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 17
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 17', @person_id, 16);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 18
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 18', @person_id, 17);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 19
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 19', @person_id, 18);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- project 20
INSERT INTO storyboard_project (title, person_id, sort_order) VALUES ('프로젝트 20', @person_id, 19);
SET @proj = LAST_INSERT_ID();
INSERT INTO storyboard_scene (title, script_html, project_id, sort_order) VALUES
('씬 01', '<p>스크립트 01</p>', @proj, 0),
('씬 02', '<p>스크립트 02</p>', @proj, 1),
('씬 03', '<p>스크립트 03</p>', @proj, 2),
('씬 04', '<p>스크립트 04</p>', @proj, 3),
('씬 05', '<p>스크립트 05</p>', @proj, 4),
('씬 06', '<p>스크립트 06</p>', @proj, 5),
('씬 07', '<p>스크립트 07</p>', @proj, 6),
('씬 08', '<p>스크립트 08</p>', @proj, 7),
('씬 09', '<p>스크립트 09</p>', @proj, 8),
('씬 10', '<p>스크립트 10</p>', @proj, 9);

-- ============================================================
-- 7. 확인 쿼리
-- ============================================================
SELECT 'person'              AS tbl, COUNT(*) AS cnt FROM person              WHERE public_id = 'k6-test-person-uuid-0001'
UNION ALL
SELECT 'person_sns',          COUNT(*) FROM person_sns          WHERE person_id = @person_id
UNION ALL
SELECT 'profile_tag',         COUNT(*) FROM profile_tag         WHERE person_id = @person_id
UNION ALL
SELECT 'person_gallery',      COUNT(*) FROM person_gallery      WHERE person_id = @person_id
UNION ALL
SELECT 'movie',               COUNT(*) FROM movie               WHERE movie_id  >= @movie_base AND movie_id < @movie_base + 20
UNION ALL
SELECT 'movie_person',        COUNT(*) FROM movie_person        WHERE person_id = @person_id
UNION ALL
SELECT 'storyboard_project',  COUNT(*) FROM storyboard_project  WHERE person_id = @person_id
UNION ALL
SELECT 'storyboard_scene',    COUNT(*) FROM storyboard_scene    WHERE project_id IN (SELECT id FROM storyboard_project WHERE person_id = @person_id);
