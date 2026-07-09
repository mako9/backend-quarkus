--liquibase formatted sql

--changeset mario:4 labels:booking-flow context:booking-flow
--comment: Add booking status, approval flag, and notification table

ALTER TABLE item ADD COLUMN requires_approval BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE item_booking ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
ALTER TABLE item_booking ADD COLUMN cancelled_by VARCHAR(20);
ALTER TABLE item_booking ADD COLUMN cancelled_at TIMESTAMPTZ;

CREATE TABLE "notification" (
    uuid CHAR(36) PRIMARY KEY NOT NULL,
    user_uuid CHAR(36) NOT NULL,
    type VARCHAR(50) NOT NULL,
    booking_uuid CHAR(36),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT "fc_notification__user_uuid" FOREIGN KEY (user_uuid) REFERENCES "user"(uuid) ON DELETE CASCADE,
    CONSTRAINT "fc_notification__booking_uuid" FOREIGN KEY (booking_uuid) REFERENCES item_booking(uuid) ON DELETE SET NULL
);
--rollback ALTER TABLE item DROP COLUMN requires_approval; ALTER TABLE item_booking DROP COLUMN status; ALTER TABLE item_booking DROP COLUMN cancelled_by; ALTER TABLE item_booking DROP COLUMN cancelled_at; DROP TABLE notification;
