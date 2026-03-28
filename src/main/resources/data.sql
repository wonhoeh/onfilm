-- ============================================================
-- N+1 분석용 테스트 더미 데이터
-- 계정: testactor / test1234
-- publicId: test-public-id-001
-- ============================================================

-- 1. Person
INSERT INTO person (id, name, birth_date, birth_place, one_line_intro, profile_image_url, filmography_file_key, public_id, filmography_private, gallery_private)
VALUES (1, '테스트 배우', '1995-03-15', '서울', '독립영화를 사랑하는 배우입니다.', NULL, NULL, 'test-public-id-001', FALSE, FALSE);

-- 2. User는 DevDataInitializer.java에서 PasswordEncoder로 생성 (BCrypt 해시 불일치 방지)

-- 3. SNS (2개)
INSERT INTO person_sns (id, type, url, person_id) VALUES (1, 'INSTAGRAM', 'https://instagram.com/testactor', 1);
INSERT INTO person_sns (id, type, url, person_id) VALUES (2, 'YOUTUBE', 'https://youtube.com/testactor', 1);

-- 4. 프로필 태그 (3개)
INSERT INTO profile_tag (id, person_id, raw_text, normalized) VALUES (1, 1, '연기', '연기');
INSERT INTO profile_tag (id, person_id, raw_text, normalized) VALUES (2, 1, '독립영화', '독립영화');
INSERT INTO profile_tag (id, person_id, raw_text, normalized) VALUES (3, 1, '단편영화', '단편영화');

-- 5. 갤러리 (3개)
INSERT INTO person_gallery (person_id, image_key, is_private, sort_order) VALUES (1, 'gallery/1/img1.jpg', FALSE, 0);
INSERT INTO person_gallery (person_id, image_key, is_private, sort_order) VALUES (1, 'gallery/1/img2.jpg', FALSE, 1);
INSERT INTO person_gallery (person_id, image_key, is_private, sort_order) VALUES (1, 'gallery/1/img3.jpg', FALSE, 2);

-- 6. 스토리보드 프로젝트 10개 (N+1 확인용)
INSERT INTO storyboard_project (id, title, person_id, sort_order) VALUES (1,  '프로젝트 1',  1, 0);
INSERT INTO storyboard_project (id, title, person_id, sort_order) VALUES (2,  '프로젝트 2',  1, 1);
INSERT INTO storyboard_project (id, title, person_id, sort_order) VALUES (3,  '프로젝트 3',  1, 2);
INSERT INTO storyboard_project (id, title, person_id, sort_order) VALUES (4,  '프로젝트 4',  1, 3);
INSERT INTO storyboard_project (id, title, person_id, sort_order) VALUES (5,  '프로젝트 5',  1, 4);
INSERT INTO storyboard_project (id, title, person_id, sort_order) VALUES (6,  '프로젝트 6',  1, 5);
INSERT INTO storyboard_project (id, title, person_id, sort_order) VALUES (7,  '프로젝트 7',  1, 6);
INSERT INTO storyboard_project (id, title, person_id, sort_order) VALUES (8,  '프로젝트 8',  1, 7);
INSERT INTO storyboard_project (id, title, person_id, sort_order) VALUES (9,  '프로젝트 9',  1, 8);
INSERT INTO storyboard_project (id, title, person_id, sort_order) VALUES (10, '프로젝트 10', 1, 9);

-- 7. 씬 (프로젝트당 3개 = 총 30개)
-- 프로젝트 1
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (1,  '씬 1', '<p>대사 내용입니다.</p>', 1, 0);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (2,  '씬 2', '<p>대사 내용입니다.</p>', 1, 1);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (3,  '씬 3', '<p>대사 내용입니다.</p>', 1, 2);
-- 프로젝트 2
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (4,  '씬 1', '<p>대사 내용입니다.</p>', 2, 0);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (5,  '씬 2', '<p>대사 내용입니다.</p>', 2, 1);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (6,  '씬 3', '<p>대사 내용입니다.</p>', 2, 2);
-- 프로젝트 3
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (7,  '씬 1', '<p>대사 내용입니다.</p>', 3, 0);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (8,  '씬 2', '<p>대사 내용입니다.</p>', 3, 1);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (9,  '씬 3', '<p>대사 내용입니다.</p>', 3, 2);
-- 프로젝트 4
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (10, '씬 1', '<p>대사 내용입니다.</p>', 4, 0);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (11, '씬 2', '<p>대사 내용입니다.</p>', 4, 1);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (12, '씬 3', '<p>대사 내용입니다.</p>', 4, 2);
-- 프로젝트 5
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (13, '씬 1', '<p>대사 내용입니다.</p>', 5, 0);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (14, '씬 2', '<p>대사 내용입니다.</p>', 5, 1);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (15, '씬 3', '<p>대사 내용입니다.</p>', 5, 2);
-- 프로젝트 6
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (16, '씬 1', '<p>대사 내용입니다.</p>', 6, 0);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (17, '씬 2', '<p>대사 내용입니다.</p>', 6, 1);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (18, '씬 3', '<p>대사 내용입니다.</p>', 6, 2);
-- 프로젝트 7
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (19, '씬 1', '<p>대사 내용입니다.</p>', 7, 0);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (20, '씬 2', '<p>대사 내용입니다.</p>', 7, 1);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (21, '씬 3', '<p>대사 내용입니다.</p>', 7, 2);
-- 프로젝트 8
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (22, '씬 1', '<p>대사 내용입니다.</p>', 8, 0);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (23, '씬 2', '<p>대사 내용입니다.</p>', 8, 1);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (24, '씬 3', '<p>대사 내용입니다.</p>', 8, 2);
-- 프로젝트 9
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (25, '씬 1', '<p>대사 내용입니다.</p>', 9, 0);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (26, '씬 2', '<p>대사 내용입니다.</p>', 9, 1);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (27, '씬 3', '<p>대사 내용입니다.</p>', 9, 2);
-- 프로젝트 10
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (28, '씬 1', '<p>대사 내용입니다.</p>', 10, 0);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (29, '씬 2', '<p>대사 내용입니다.</p>', 10, 1);
INSERT INTO storyboard_scene (id, title, script_html, project_id, sort_order) VALUES (30, '씬 3', '<p>대사 내용입니다.</p>', 10, 2);
