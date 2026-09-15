-- NR-176: consolidate the two separate per-use-case prompt template columns
-- (prompt_template from V21, classification_prompt_template from V53) into a
-- single JSON map column, so future prompt templates (suggestion,
-- zero_result_recovery, judgment) don't each need their own migration/column.
ALTER TABLE llm_config ADD COLUMN prompt_templates TEXT;

UPDATE llm_config
SET prompt_templates = jsonb_strip_nulls(
        jsonb_build_object(
            'rewrite', prompt_template,
            'classification', classification_prompt_template
        )
    )::text
WHERE prompt_template IS NOT NULL OR classification_prompt_template IS NOT NULL;

ALTER TABLE llm_config DROP COLUMN prompt_template;
ALTER TABLE llm_config DROP COLUMN classification_prompt_template;
