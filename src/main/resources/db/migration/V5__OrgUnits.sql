CREATE TABLE org_units (
    uuid            VARCHAR(36)  PRIMARY KEY,
    name            VARCHAR(300) NOT NULL,
    short_name      VARCHAR(100),
    code            VARCHAR(50),
    parent_uuid     VARCHAR(36)  REFERENCES org_units(uuid) ON DELETE SET NULL,
    level           INT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_org_units_parent ON org_units(parent_uuid);

-- Seed a default org unit so there's always at least one valid value to point at —
-- both for existing projects being backfilled below, and as a fallback in a fresh install
-- before anyone has built out a real hierarchy.
INSERT INTO org_units (uuid, name, short_name, code, parent_uuid, level)
VALUES ('00000000-0000-0000-0000-000000000000', 'Unspecified', 'Unspecified', 'UNSPEC', NULL, 1);

-- Associate every project with an org unit. Added nullable first so existing rows don't
-- fail the migration, backfilled to the default above, then locked to NOT NULL.
ALTER TABLE projects ADD COLUMN org_unit_uuid VARCHAR(36) REFERENCES org_units(uuid);
UPDATE projects SET org_unit_uuid = '00000000-0000-0000-0000-000000000000' WHERE org_unit_uuid IS NULL;
ALTER TABLE projects ALTER COLUMN org_unit_uuid SET NOT NULL;

CREATE INDEX idx_projects_org_unit ON projects(org_unit_uuid);