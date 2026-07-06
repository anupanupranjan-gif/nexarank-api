-- NR-68: APPROVED no longer means "serving live traffic" — LIVE does.
-- Auto-promote rules that are currently approved and enabled (i.e. actually
-- serving today) so search behavior does not change when this ships.
UPDATE merch_rules SET status = 'LIVE' WHERE status = 'APPROVED' AND enabled = true;
