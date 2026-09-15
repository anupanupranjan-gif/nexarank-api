-- NR-174: LLM_QUERY_CLASSIFICATION's prompt template was a hardcoded Java
-- constant (LlmQueryClassificationStage.PROMPT_TEMPLATE) - unlike
-- LLM_QUERY_REWRITE's, which has always been admin-configurable via
-- llm_config.prompt_template. Adds the same mechanism for classification.
-- Nullable, same as prompt_template - a null/blank value falls back to the
-- default template baked into LlmConfig.DEFAULT_CLASSIFICATION_PROMPT_TEMPLATE.
ALTER TABLE llm_config ADD COLUMN classification_prompt_template TEXT;
