CREATE TABLE pdsa_cycles (
    id                  BIGSERIAL PRIMARY KEY,
    project_id          BIGINT       NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    process_area_id     BIGINT       REFERENCES process_areas(id) ON DELETE SET NULL,
    title               VARCHAR(300) NOT NULL,
    plan_text           TEXT,
    prediction_text     TEXT,
    do_text             TEXT,
    study_text          TEXT,
    act_decision        VARCHAR(20)  NOT NULL DEFAULT 'in_progress', -- in_progress | adopt | adapt | abandon
    act_text            TEXT,
    start_date          DATE         NOT NULL,
    end_date            DATE,
    created_by          BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);

-- A cycle can target more than one indicator (e.g. a single workflow change that's
-- expected to move both a time-to-triage indicator and a triage-accuracy indicator).
CREATE TABLE pdsa_cycle_indicators (
    id              BIGSERIAL PRIMARY KEY,
    cycle_id        BIGINT NOT NULL REFERENCES pdsa_cycles(id) ON DELETE CASCADE,
    indicator_id    BIGINT NOT NULL REFERENCES indicators(id) ON DELETE CASCADE,
    UNIQUE(cycle_id, indicator_id)
);

CREATE INDEX idx_pdsa_cycles_project ON pdsa_cycles(project_id);
CREATE INDEX idx_pdsa_cycle_indicators_cycle ON pdsa_cycle_indicators(cycle_id);
CREATE INDEX idx_pdsa_cycle_indicators_indicator ON pdsa_cycle_indicators(indicator_id);