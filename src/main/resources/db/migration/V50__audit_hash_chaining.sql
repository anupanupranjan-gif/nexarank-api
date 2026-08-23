-- NR-155: SHA-256 row chaining for audit_events, closing the ADR-06 gap.
--
-- ADR-06 committed to "append-only Postgres + SHA-256 row chaining" as the
-- substitute for a deferred blockchain audit trail. Only the append-only half
-- was ever built (NR-70 verified no update/delete API exists) - immutability
-- was enforced purely at the application layer. A direct DB write (a rogue
-- admin, a compromised credential, a bug in some future migration) would go
-- completely undetected. Row chaining closes that: each row hashes its own
-- content together with the previous row's hash (per tenant, since queries,
-- retention, and access are all tenant-scoped already), so any out-of-band
-- mutation breaks the chain from that point forward and is detectable by
-- recomputing hashes - see AuditChainService.verifyChain().
--
-- seq is a separate monotonic column rather than ordering by created_at:
-- createdAt has millisecond resolution and two events in the same
-- millisecond would make "the previous row" ambiguous. seq is assigned
-- atomically by Postgres and can't collide.
ALTER TABLE audit_events ADD COLUMN seq BIGSERIAL;
ALTER TABLE audit_events ADD COLUMN prev_hash VARCHAR(64);
ALTER TABLE audit_events ADD COLUMN row_hash VARCHAR(64);

CREATE INDEX idx_audit_events_tenant_seq ON audit_events (tenant_id, seq DESC);

-- Existing rows predate chaining. We can't retroactively compute a
-- meaningful hash for them (we have no cryptographic assurance about their
-- state before this migration ran), so they're marked as a pre-chain
-- boundary rather than backfilled with a fabricated hash. The first
-- genuinely chained row for each tenant links to whatever value sits in
-- that tenant's last existing row - real verification is meaningful from
-- that point forward.
UPDATE audit_events SET row_hash = 'PRE_CHAIN_GENESIS' WHERE row_hash IS NULL;
ALTER TABLE audit_events ALTER COLUMN row_hash SET NOT NULL;
