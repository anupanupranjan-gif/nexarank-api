-- Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
-- V27: Business signals from ERP/PIM/OMS feeds
--
-- Signal types:
--   MARGIN_LOW   — low margin product, bury candidate
--   OUT_OF_STOCK — out of stock, bury candidate
--   PROMOTED     — brand agreement / promotion, boost candidate
--   SEASONAL     — seasonal relevance window (validFrom/validTo define the window)

CREATE TABLE business_signals (
    id          VARCHAR(36)  PRIMARY KEY,
    tenant_id   VARCHAR(100) NOT NULL,
    project_id  VARCHAR(100) NOT NULL,
    product_id  VARCHAR(200) NOT NULL,
    signal_type VARCHAR(50)  NOT NULL,
    value       VARCHAR(500),             -- optional metadata (e.g. margin %, stock count)
    valid_from  TIMESTAMPTZ,              -- null = always active
    valid_to    TIMESTAMPTZ,              -- null = no expiry
    source      VARCHAR(200),             -- e.g. "ERP", "PIM", "manual"
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_business_signal UNIQUE (tenant_id, project_id, product_id, signal_type)
);

CREATE INDEX idx_business_signals_tenant_project
    ON business_signals (tenant_id, project_id);

CREATE INDEX idx_business_signals_product
    ON business_signals (tenant_id, project_id, product_id);
