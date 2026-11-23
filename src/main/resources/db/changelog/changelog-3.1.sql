--liquibase formatted sql

--changeset mario:6 labels:approval-default context:approval-default
--comment: Require booking approval by default; lending is a favour between neighbours, not a first-come marketplace

ALTER TABLE item ALTER COLUMN requires_approval SET DEFAULT TRUE;

--rollback ALTER TABLE item ALTER COLUMN requires_approval SET DEFAULT FALSE;

--changeset mario:7 labels:approval-default-backfill context:approval-default
--comment: Retroactive policy change, not a default. Items posted before 2026-11-23 all read requires_approval = FALSE because that was the column default, not because their owners chose it, so they ask for approval from now on; an owner who wants the old first-come behaviour switches it off on the item. Deliberately without a rollback: this is a data change, and any automatic revert would also flip the items whose owners really did choose approval.

UPDATE item SET requires_approval = TRUE WHERE requires_approval = FALSE;
