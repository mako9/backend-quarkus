--liquibase formatted sql

--changeset mario:1 labels:user-table context:create-user-table
--comment: Initial commit with user table
CREATE TABLE "user" (
    uuid CHAR(36) PRIMARY KEY NOT NULL,
    name VARCHAR(50) NOT NULL
);
--rollback DROP TABLE user;

--changeset mario:2 labels:fruit-table context:create-fruit-table
CREATE TABLE fruit (
    id SERIAL PRIMARY KEY NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT UC_FRUIT_NAME UNIQUE (name)
    );
--rollback DROP TABLE fruit;

