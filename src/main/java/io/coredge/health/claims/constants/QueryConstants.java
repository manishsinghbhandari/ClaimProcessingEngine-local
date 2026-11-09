package io.coredge.health.claims.constants;

public interface QueryConstants {
    public final static String findClaimDetailsNative = "SELECT c.code, COALESCE(c.net_value, 0.00) , c.claim_id, cl.status FROM claim_items c JOIN claims cl ON c.claim_id = cl.claim_id WHERE c.claim_id IN (:claimIds) AND cl.status = :status";
}
