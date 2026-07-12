-- No column type/constraint change needed — reporting_frequency is a plain VARCHAR(20)
-- with no CHECK constraint, so it already accepts 'biweekly' as a value. This migration
-- just keeps the column's documentation accurate for anyone reading the schema directly.
COMMENT ON COLUMN projects.reporting_frequency IS 'daily | weekly | biweekly | monthly | quarterly';
