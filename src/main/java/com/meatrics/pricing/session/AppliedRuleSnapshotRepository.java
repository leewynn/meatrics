package com.meatrics.pricing.session;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for pricing session applied rules audit trail
 */
@Repository
public class AppliedRuleSnapshotRepository {

    private final DSLContext dsl;

    public AppliedRuleSnapshotRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Find all applied rule snapshots for a specific line item
     * @param sessionLineItemId The line item ID
     * @return List of snapshots ordered by application order
     */
    public List<AppliedRuleSnapshot> findBySessionLineItemId(Long sessionLineItemId) {
        String sql = "SELECT id, session_line_item_id, rule_id, rule_name, " +
                "pricing_method, pricing_value, application_order, input_price, output_price, " +
                "applied_at " +
                "FROM pricing_session_applied_rules " +
                "WHERE session_line_item_id = ? " +
                "ORDER BY application_order ASC";

        return dsl.fetch(sql, sessionLineItemId)
                .map(this::mapToAppliedRuleSnapshot);
    }

    /**
     * Find all applied rule snapshots for a specific session
     * @param sessionId The session ID
     * @return List of snapshots
     */
    public List<AppliedRuleSnapshot> findBySessionId(Long sessionId) {
        String sql = "SELECT ars.id, ars.session_line_item_id, ars.rule_id, ars.rule_name, " +
                "ars.pricing_method, ars.pricing_value, ars.application_order, " +
                "ars.input_price, ars.output_price, ars.applied_at " +
                "FROM pricing_session_applied_rules ars " +
                "INNER JOIN pricing_session_line_items psli ON ars.session_line_item_id = psli.id " +
                "WHERE psli.session_id = ? " +
                "ORDER BY psli.id, ars.application_order ASC";

        return dsl.fetch(sql, sessionId)
                .map(this::mapToAppliedRuleSnapshot);
    }

    /**
     * Save all applied rule snapshots for a line item (batch insert)
     * @param sessionLineItemId The line item ID
     * @param snapshots List of rule snapshots to save
     */
    public void saveAll(Long sessionLineItemId, List<AppliedRuleSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO pricing_session_applied_rules " +
                "(session_line_item_id, rule_id, rule_name, pricing_method, " +
                "pricing_value, application_order, input_price, output_price) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        dsl.batch(
            snapshots.stream()
                .map(snapshot -> dsl.query(sql,
                    sessionLineItemId,
                    snapshot.getRuleId(),
                    snapshot.getRuleName(),
                    snapshot.getPricingMethod(),
                    snapshot.getPricingValue(),
                    snapshot.getApplicationOrder(),
                    snapshot.getInputPrice(),
                    snapshot.getOutputPrice()
                ))
                .toList()
        ).execute();
    }

    /**
     * Delete all applied rule snapshots for a specific line item
     * @param sessionLineItemId The line item ID
     */
    public void deleteBySessionLineItemId(Long sessionLineItemId) {
        String sql = "DELETE FROM pricing_session_applied_rules WHERE session_line_item_id = ?";
        dsl.execute(sql, sessionLineItemId);
    }

    /**
     * Delete all applied rule snapshots for a session
     * @param sessionId The session ID
     */
    public void deleteBySessionId(Long sessionId) {
        String sql = "DELETE FROM pricing_session_applied_rules " +
                "WHERE session_line_item_id IN " +
                "(SELECT id FROM pricing_session_line_items WHERE session_id = ?)";
        dsl.execute(sql, sessionId);
    }

    /**
     * Map database record to AppliedRuleSnapshot entity
     */
    private AppliedRuleSnapshot mapToAppliedRuleSnapshot(Record record) {
        AppliedRuleSnapshot snapshot = new AppliedRuleSnapshot();
        snapshot.setId(record.get("id", Long.class));
        snapshot.setSessionLineItemId(record.get("session_line_item_id", Long.class));
        snapshot.setRuleId(record.get("rule_id", Long.class));
        snapshot.setRuleName(record.get("rule_name", String.class));
        snapshot.setPricingMethod(record.get("pricing_method", String.class));
        snapshot.setPricingValue(record.get("pricing_value", java.math.BigDecimal.class));
        snapshot.setApplicationOrder(record.get("application_order", Integer.class));
        snapshot.setInputPrice(record.get("input_price", java.math.BigDecimal.class));
        snapshot.setOutputPrice(record.get("output_price", java.math.BigDecimal.class));
        snapshot.setAppliedAt(record.get("applied_at", java.time.LocalDateTime.class));
        return snapshot;
    }
}
