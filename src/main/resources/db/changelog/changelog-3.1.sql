--liquibase formatted sql

--changeset mario:6 labels:approval-default context:approval-default
--comment: Require booking approval by default; lending is a favour between neighbours, not a first-come marketplace

ALTER TABLE item ALTER COLUMN requires_approval SET DEFAULT TRUE;
UPDATE item SET requires_approval = TRUE WHERE requires_approval = FALSE;

--rollback ALTER TABLE item ALTER COLUMN requires_approval SET DEFAULT FALSE;
