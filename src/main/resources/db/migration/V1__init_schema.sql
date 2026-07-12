-- V1__init_schema.sql
-- Core schema for QI Tracker: users, projects, process areas, indicators (with
-- numerator/denominator data elements), entries, project membership, and
-- password reset tokens.

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200)        NOT NULL,
    email           VARCHAR(255)        NOT NULL UNIQUE,
    password_hash   VARCHAR(255)        NOT NULL,
    role            VARCHAR(20)         NOT NULL DEFAULT 'MEMBER', -- ADMIN | MEMBER
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT              NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(120)        NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ         NOT NULL,
    used            BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT now()
);
CREATE INDEX idx_reset_tokens_user ON password_reset_tokens(user_id);

CREATE TABLE projects (
    id                     BIGSERIAL PRIMARY KEY,
    name                   VARCHAR(300)     NOT NULL,
    objectives             TEXT,
    start_date             DATE,
    duration_val           VARCHAR(20),
    duration_unit          VARCHAR(20)      NOT NULL DEFAULT 'months', -- weeks | months | years
    baseline               TEXT,
    success_definition     TEXT,
    reporting_frequency    VARCHAR(20)      NOT NULL DEFAULT 'weekly', -- daily | weekly | monthly | quarterly
    creator_id             BIGINT           NOT NULL REFERENCES users(id),
    last_report_sent_at    TIMESTAMPTZ,
    created_at             TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- Users explicitly assigned to a project (beyond the creator, who always has full access
-- via projects.creator_id and doesn't need a row here). Members can log entries, use the
-- Manage view, and receive the project's scheduled report emails.
CREATE TABLE project_members (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    added_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE(project_id, user_id)
);
CREATE INDEX idx_members_project ON project_members(project_id);
CREATE INDEX idx_members_user ON project_members(user_id);

CREATE TABLE process_areas (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT       NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_process_areas_project ON process_areas(project_id);

CREATE TABLE indicators (
    id                  BIGSERIAL PRIMARY KEY,
    project_id          BIGINT        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    process_area_id     BIGINT        REFERENCES process_areas(id) ON DELETE SET NULL,
    name                VARCHAR(300)  NOT NULL,
    optimal_description TEXT,
    multiplier          NUMERIC(14,4) NOT NULL DEFAULT 1,
    target_value        NUMERIC(14,4),
    unit                VARCHAR(50),
    direction           VARCHAR(10)   NOT NULL DEFAULT 'higher', -- higher | lower
    created_by          BIGINT        REFERENCES users(id),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_indicators_project ON indicators(project_id);
CREATE INDEX idx_indicators_process_area ON indicators(process_area_id);

-- The data elements that make up an indicator's numerator or denominator.
CREATE TABLE indicator_elements (
    id              BIGSERIAL PRIMARY KEY,
    indicator_id    BIGINT        NOT NULL REFERENCES indicators(id) ON DELETE CASCADE,
    section         VARCHAR(12)   NOT NULL, -- NUMERATOR | DENOMINATOR
    name            VARCHAR(300)  NOT NULL,
    sign            VARCHAR(10)   NOT NULL DEFAULT 'ADD', -- ADD | SUBTRACT
    sort_order      INT           NOT NULL DEFAULT 0
);
CREATE INDEX idx_elements_indicator ON indicator_elements(indicator_id);

CREATE TABLE entries (
    id              BIGSERIAL PRIMARY KEY,
    indicator_id    BIGINT        NOT NULL REFERENCES indicators(id) ON DELETE CASCADE,
    entry_date      DATE          NOT NULL,
    computed_value  NUMERIC(18,4) NOT NULL,
    note            TEXT,
    created_by      BIGINT        REFERENCES users(id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_entries_indicator ON entries(indicator_id);
CREATE INDEX idx_entries_date ON entries(entry_date);

-- The raw per-data-element counts behind a single entry's computed value.
CREATE TABLE entry_values (
    id                    BIGSERIAL PRIMARY KEY,
    entry_id              BIGINT        NOT NULL REFERENCES entries(id) ON DELETE CASCADE,
    indicator_element_id  BIGINT        NOT NULL REFERENCES indicator_elements(id) ON DELETE CASCADE,
    value                 NUMERIC(18,4) NOT NULL DEFAULT 0
);
CREATE INDEX idx_entry_values_entry ON entry_values(entry_id);
