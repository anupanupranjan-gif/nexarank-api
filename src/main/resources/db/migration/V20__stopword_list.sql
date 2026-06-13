-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V20: Stopword list table — per tenant/project, manageable from the UI
--
-- Scoped per tenant/project so each customer can maintain their own list.
-- Seeded with conservative eCommerce defaults for all existing tenant/project pairs.

CREATE TABLE stopword_list (
    id          BIGSERIAL    PRIMARY KEY,
    tenant_id   VARCHAR(100) NOT NULL,
    project_id  VARCHAR(100) NOT NULL,
    word        VARCHAR(100) NOT NULL,
    created_by  VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_stopword UNIQUE (tenant_id, project_id, word)
);

CREATE INDEX idx_stopword_tenant_project
    ON stopword_list (tenant_id, project_id);

-- Seed conservative eCommerce defaults for all existing tenant/project pairs.
-- Function words only — intentionally avoids commercial intent words like
-- "best", "cheap", "good" which carry search signal.
INSERT INTO stopword_list (tenant_id, project_id, word)
SELECT
    p.tenant_id,
    p.id AS project_id,
    s.word
FROM projects p
CROSS JOIN (VALUES
    ('a'), ('an'), ('the'), ('and'), ('or'), ('but'),
    ('in'), ('on'), ('at'), ('to'), ('for'), ('of'),
    ('with'), ('by'), ('from'), ('is'), ('it'), ('its'),
    ('this'), ('that'), ('these'), ('those'),
    ('i'), ('my'), ('me'), ('we'), ('our'),
    ('you'), ('your'), ('he'), ('she'), ('his'), ('her'),
    ('they'), ('their'), ('what'), ('which'), ('who'), ('how'),
    ('do'), ('does'), ('did'), ('can'), ('could'), ('will'),
    ('would'), ('should'), ('have'), ('has'), ('had'),
    ('be'), ('been'), ('am'), ('are'), ('was'), ('were'),
    ('some'), ('any'), ('all'), ('no'), ('not'), ('so'),
    ('if'), ('as'), ('than'), ('then'), ('just')
) AS s(word)
ON CONFLICT (tenant_id, project_id, word) DO NOTHING;
