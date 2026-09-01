ALTER TABLE movie_person_role
    ADD CONSTRAINT ck_movie_person_role_sort_order_non_negative
        CHECK (sort_order >= 0);

ALTER TABLE trailer
    ADD CONSTRAINT ck_trailer_sort_order_non_negative
        CHECK (sort_order >= 0);

ALTER TABLE profile_tag
    ADD CONSTRAINT ck_profile_tag_sort_order_non_negative
        CHECK (sort_order >= 0);

ALTER TABLE storyboard_project
    ADD CONSTRAINT ck_storyboard_project_sort_order_non_negative
        CHECK (sort_order >= 0);

ALTER TABLE storyboard_scene
    ADD CONSTRAINT ck_storyboard_scene_sort_order_non_negative
        CHECK (sort_order >= 0);

ALTER TABLE storyboard_card
    ADD CONSTRAINT ck_storyboard_card_sort_order_non_negative
        CHECK (sort_order >= 0);

ALTER TABLE movie_person
    ADD CONSTRAINT ck_movie_person_sort_order_non_negative
        CHECK (sort_order >= 0);

ALTER TABLE person_gallery
    ADD CONSTRAINT ck_person_gallery_sort_order_non_negative
        CHECK (sort_order >= 0),
    ADD CONSTRAINT uk_person_gallery_image_key
        UNIQUE (person_id, image_key);

ALTER TABLE movie
    ADD CONSTRAINT ck_movie_runtime
        CHECK (runtime BETWEEN 1 AND 1000),
    ADD CONSTRAINT ck_movie_release_year_min
        CHECK (release_year >= 1900);
