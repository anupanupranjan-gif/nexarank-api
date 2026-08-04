-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V46: NR-58 — LLM search quality auto-scoring for judgment sets.
--
-- Expands judgments.grade from the existing 0-3 scale to a real 0-4 scale
-- (BAD=0, FAIR=1, GOOD=2, EXCELLENT=3, PERFECT=4) matching the ticket's
-- 5-point PERFECT/EXCELLENT/GOOD/FAIR/BAD ask, rather than collapsing two
-- LLM labels onto the same existing grade value. Every pre-existing judgment
-- (0-3) is still valid under the wider 0-4 constraint - no backfill needed.
--
-- New columns support the LLM-authored / human-reviewed workflow:
--   source       - HUMAN (existing manual judging, unchanged default) | LLM
--   status       - APPROVED (existing rows, and any human-entered judgment,
--                  need no review) | PENDING_REVIEW (fresh LLM output)
--   llm_grade    - the LLM's original suggested grade, retained even after a
--                  human overrides `grade` - needed to compute agreement rate
--   reviewed_by / reviewed_at - who/when a human accepted or overrode an
--                  LLM judgment (distinct from judged_by/judged_at, which
--                  keep meaning "who/when authored the current grade value")

ALTER TABLE judgments DROP CONSTRAINT judgments_grade_check;
ALTER TABLE judgments ADD CONSTRAINT judgments_grade_check CHECK (grade >= 0 AND grade <= 4);

ALTER TABLE judgments ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'HUMAN';
ALTER TABLE judgments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE judgments ADD COLUMN llm_grade INT;
ALTER TABLE judgments ADD COLUMN reviewed_by VARCHAR(100);
ALTER TABLE judgments ADD COLUMN reviewed_at TIMESTAMPTZ;
