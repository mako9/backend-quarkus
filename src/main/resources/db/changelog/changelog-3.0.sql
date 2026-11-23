--liquibase formatted sql

--changeset mario:5 labels:complete-booking context:complete-booking
--comment: Add RETURNED booking status, return tracking columns, BOOKING_RETURNED notification type

ALTER TABLE item_booking ADD COLUMN returned_by VARCHAR(20);
ALTER TABLE item_booking ADD COLUMN returned_at TIMESTAMPTZ;

--rollback ALTER TABLE item_booking DROP COLUMN returned_by; ALTER TABLE item_booking DROP COLUMN returned_at;
