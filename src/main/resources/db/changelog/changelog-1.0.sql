--liquibase formatted sql

--changeset mario:1 labels:user-table context:create-user-table
--comment: Initial commit with user table
DROP TYPE IF EXISTS user_role;
CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');

CREATE TABLE "user" (
    uuid CHAR(36) PRIMARY KEY NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    mail VARCHAR(100) NOT NULL,
    street VARCHAR(255),
    house_number VARCHAR(10),
    postal_code VARCHAR(5),
    city VARCHAR(100),
    roles user_role ARRAY NOT NULL,
    CONSTRAINT "uc_user_mail" UNIQUE (mail)
);
--rollback DROP TABLE user;

