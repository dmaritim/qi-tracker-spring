-- V1 declared these as NUMERIC(x,y), but the JPA entities use Double, which Hibernate
-- maps to double precision (float8). Align the columns so schema validation passes,
-- without touching the already-applied V1 migration.

ALTER TABLE indicators   ALTER COLUMN multiplier     TYPE DOUBLE PRECISION;
ALTER TABLE indicators   ALTER COLUMN target_value   TYPE DOUBLE PRECISION;
ALTER TABLE entries      ALTER COLUMN computed_value TYPE DOUBLE PRECISION;
ALTER TABLE entry_values ALTER COLUMN value          TYPE DOUBLE PRECISION;
