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
    CONSTRAINT "uc_user__mail" UNIQUE (mail)
);
--rollback DROP TABLE user;

--changeset mario:2 labels:community-table context:create-community-table
--comment: Add community table
CREATE TABLE "community" (
    uuid CHAR(36) PRIMARY KEY NOT NULL,
    "name" VARCHAR(100) NOT NULL,
    street VARCHAR(255),
    house_number VARCHAR(10),
    postal_code VARCHAR(5),
    city VARCHAR(100),
    admin_uuid CHAR(36) NOT NULL,
    radius INTEGER NOT NULL,
    latitude DECIMAL NOT NULL,
    longitude DECIMAL NOT NULL,
    CONSTRAINT "uc_community__name" UNIQUE ("name"),
    CONSTRAINT "fc_community__user_uuid" FOREIGN KEY (admin_uuid) REFERENCES "user"(uuid)
);
--rollback DROP TABLE community;

CREATE TABLE "user_community_relation" (
    user_uuid CHAR(36) NOT NULL,
    community_uuid CHAR(36) NOT NULL,
    PRIMARY KEY (user_uuid, community_uuid),
    CONSTRAINT "fc_user_community_relation__user_uuid" FOREIGN KEY (user_uuid) REFERENCES "user"(uuid),
    CONSTRAINT "fc_user_community_relation__community_uuid" FOREIGN KEY (community_uuid) REFERENCES "community"(uuid)
);
--rollback DROP TABLE user_community_relation;

