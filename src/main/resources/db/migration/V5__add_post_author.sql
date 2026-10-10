-- V5__add_post_author.sql

-- Development-only destructive cleanup
DELETE FROM comments;
DELETE FROM posts;

ALTER TABLE posts
    ADD COLUMN author_id BIGINT NOT NULL;

ALTER TABLE posts
    ADD CONSTRAINT posts_author_fk
        FOREIGN KEY (author_id)
        REFERENCES users(id);