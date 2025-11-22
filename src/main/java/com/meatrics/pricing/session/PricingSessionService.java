package com.meatrics.pricing.session;

import com.meatrics.pricing.customer.Customer;
import com.meatrics.pricing.customer.CustomerRepository;
import com.meatrics.pricing.product.GroupedLineItem;
import com.meatrics.pricing.rule.PricingRule;
import com.meatrics.pricing.rule.PricingRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing pricing sessions and their line items
 */
@Service
public class PricingSessionService {

    private static final Logger log = LoggerFactory.getLogger(PricingSessionService.class);

    private final PricingSessionRepository pricingSessionRepository;
    private final PricingSessionLineItemRepository lineItemRepository;
    private final CustomerRepository customerRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final AppliedRuleSnapshotRepository appliedRuleSnapshotRepository;

    public PricingSessionService(PricingSessionRepository pricingSessionRepository,
                                 PricingSessionLineItemRepository lineItemRepository,
                                 CustomerRepository customerRepository,
                                 PricingRuleRepository pricingRuleRepository,
                                 AppliedRuleSnapshotRepository appliedRuleSnapshotRepository) {
        this.pricingSessionRepository = pricingSessionRepository;
        this.lineItemRepository = lineItemRepository;
        this.customerRepository = customerRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.appliedRuleSnapshotRepository = appliedRuleSnapshotRepository;
    }

    /**
     * Get all sessions ordered by last modified date
     */
    public List<PricingSession> getAllSessions() {
        return pricingSessionRepository.findAll();
    }

    /**
     * Save session with all line items transactionally
     * If session name exists, update it (delete old line items, insert new ones)
     */
    @Transactional
    public PricingSession saveSession(String sessionName, List<GroupedLineItem> lineItems) {
        if (sessionName == null || sessionName.trim().isEmpty()) {
            throw new IllegalArgumentException("Session name cannot be empty");
        }

        if (lineItems == null || lineItems.isEmpty()) {
            throw new IllegalArgumentException("Cannot save session with no line items");
        }

        // Check if session already exists
        Optional<PricingSession> existingSession = pricingSessionRepository.findBySessionName(sessionName);

        PricingSession session;
        if (existingSession.isPresent()) {
            // Update existing session
            session = existingSession.get();
            session.setLastModifiedDate(LocalDateTime.now());

            // Delete old line items
            lineItemRepository.deleteBySessionId(session.getId());
        } else {
            // Create new session
            session = new PricingSession();
            session.setSessionName(sessionName);
            session.setStatus(SessionStatus.IN_PROGRESS);
        }

        // Save session (insert or update)
        session = pricingSessionRepository.save(session);

        // Convert GroupedLineItems to PricingSessionLineItems
        List<PricingSessionLineItem> sessionLineItems = lineItems.stream()
                .map(item -> convertToSessionLineItem(item))
                .collect(Collectors.toList());

        // Save all line items
        lineItemRepository.saveAll(session.getId(), sessionLineItems);

        // Reload line items to get generated IDs for saving snapshots
        List<PricingSessionLineItem> savedLineItems = lineItemRepository.findBySessionId(session.getId());

        // Create a mapping from product/customer to saved line item (for matching snapshots)
        java.util.Map<String, PricingSessionLineItem> lineItemMap = savedLineItems.stream()
                .collect(Collectors.toMap(
                        item -> item.getCustomerCode() + "|" + item.getProductCode(),
                        item -> item
                ));

        // Save rule snapshots for each line item
        for (GroupedLineItem originalItem : lineItems) {
            String key = originalItem.getCustomerCode() + "|" + originalItem.getProductCode();
            PricingSessionLineItem savedItem = lineItemMap.get(key);

            if (savedItem != null && !originalItem.getAppliedRuleSnapshots().isEmpty()) {
                appliedRuleSnapshotRepository.saveAll(savedItem.getId(), originalItem.getAppliedRuleSnapshots());
                log.debug("Saved {} rule snapshots for product {} customer {}",
                        originalItem.getAppliedRuleSnapshots().size(),
                        originalItem.getProductCode(),
                        originalItem.getCustomerCode());
            }
        }

        return session;
    }

    /**
     * Load session and convert line items to GroupedLineItem objects
     */
    @Transactional(readOnly = true)
    public List<GroupedLineItem> loadSession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID cannot be null");
        }

        // Verify session exists
        Optional<PricingSession> session = pricingSessionRepository.findById(sessionId);
        if (!session.isPresent()) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        // Load line items
        List<PricingSessionLineItem> lineItems = lineItemRepository.findBySessionId(sessionId);

        // Convert to GroupedLineItems
        return lineItems.stream()
                .map(this::convertToGroupedLineItem)
                .collect(Collectors.toList());
    }

    /**
     * Delete session (line items cascade)
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID cannot be null");
        }

        // Delete line items first (explicit delete, even though FK might cascade)
        lineItemRepository.deleteBySessionId(sessionId);

        // Delete session
        pricingSessionRepository.deleteById(sessionId);
    }

    /**
     * Check if session name exists
     */
    public boolean sessionNameExists(String sessionName) {
        if (sessionName == null || sessionName.trim().isEmpty()) {
            return false;
        }
        return pricingSessionRepository.existsBySessionName(sessionName);
    }

    /**
     * Mark session as FINALIZED
     * Finalized sessions can be used to generate customer price lists
     */
    @Transactional
    public void finalizeSession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID cannot be null");
        }

        Optional<PricingSession> sessionOpt = pricingSessionRepository.findById(sessionId);
        if (!sessionOpt.isPresent()) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        PricingSession session = sessionOpt.get();
        session.setStatus(SessionStatus.FINALIZED);
        session.setLastModifiedDate(LocalDateTime.now());
        pricingSessionRepository.save(session);

        log.info("Session finalized: {} (ID: {})", session.getSessionName(), sessionId);
    }

    /**
     * Mark session as IN_PROGRESS (unfinalize)
     * Allows users to make changes to a previously finalized session
     */
    @Transactional
    public void unfinalizeSession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID cannot be null");
        }

        Optional<PricingSession> sessionOpt = pricingSessionRepository.findById(sessionId);
        if (!sessionOpt.isPresent()) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        PricingSession session = sessionOpt.get();
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setLastModifiedDate(LocalDateTime.now());
        pricingSessionRepository.save(session);

        log.info("Session unfinalized: {} (ID: {})", session.getSessionName(), sessionId);
    }

    /**
     * Get all finalized sessions
     */
    public List<PricingSession> getFinalizedSessions() {
        return pricingSessionRepository.findByStatus(SessionStatus.FINALIZED);
    }

    /**
     * Convert GroupedLineItem to PricingSessionLineItem
     */
    private PricingSessionLineItem convertToSessionLineItem(GroupedLineItem item) {
        PricingSessionLineItem lineItem = new PricingSessionLineItem();
        lineItem.setCustomerCode(item.getCustomerCode());
        lineItem.setCustomerName(item.getCustomerName());

        // Get customer rating from item if available, otherwise from repository
        String customerRating = item.getCustomerRating();
        if (customerRating == null && item.getCustomerCode() != null) {
            customerRating = customerRepository.findByCustomerCode(item.getCustomerCode())
                    .map(Customer::getCustomerRating)
                    .orElse(null);
        }
        lineItem.setCustomerRating(customerRating);

        lineItem.setProductCode(item.getProductCode());
        lineItem.setProductDescription(item.getProductDescription());
        lineItem.setTotalQuantity(item.getTotalQuantity());
        lineItem.setTotalAmount(item.getTotalAmount());
        lineItem.setOriginalAmount(item.getOriginalAmount() != null ? item.getOriginalAmount() : item.getTotalAmount());
        lineItem.setTotalCost(item.getTotalCost());
        lineItem.setAmountModified(item.isAmountModified());

        // Historical pricing data (critical for MAINTAIN_GP_PERCENT rule)
        lineItem.setLastCost(item.getLastCost());
        lineItem.setLastUnitSellPrice(item.getLastUnitSellPrice());
        lineItem.setLastAmount(item.getLastAmount());
        lineItem.setLastGrossProfit(item.getLastGrossProfit());
        lineItem.setIncomingCost(item.getIncomingCost());
        lineItem.setPrimaryGroup(item.getPrimaryGroup());

        // New pricing data (calculated by rules or manually set)
        lineItem.setNewUnitSellPrice(item.getNewUnitSellPrice());
        lineItem.setNewAmount(item.getNewAmount());
        lineItem.setNewGrossProfit(item.getNewGrossProfit());

        // Manual override flag
        lineItem.setManualOverride(item.isManualOverride());

        return lineItem;
    }

    /**
     * Convert PricingSessionLineItem to GroupedLineItem
     */
    private GroupedLineItem convertToGroupedLineItem(PricingSessionLineItem item) {
        GroupedLineItem groupedItem = new GroupedLineItem();
        groupedItem.setCustomerCode(item.getCustomerCode());
        groupedItem.setCustomerName(item.getCustomerName());
        groupedItem.setCustomerRating(item.getCustomerRating());
        groupedItem.setProductCode(item.getProductCode());
        groupedItem.setProductDescription(item.getProductDescription());
        groupedItem.setTotalQuantity(item.getTotalQuantity());
        groupedItem.setTotalAmount(item.getTotalAmount());
        groupedItem.setOriginalAmount(item.getOriginalAmount());
        groupedItem.setTotalCost(item.getTotalCost());
        groupedItem.setAmountModified(item.getAmountModified() != null ? item.getAmountModified() : false);

        // Historical pricing data (critical for MAINTAIN_GP_PERCENT rule)
        groupedItem.setLastCost(item.getLastCost());
        groupedItem.setLastUnitSellPrice(item.getLastUnitSellPrice());
        groupedItem.setLastAmount(item.getLastAmount());
        groupedItem.setLastGrossProfit(item.getLastGrossProfit());
        groupedItem.setIncomingCost(item.getIncomingCost());
        groupedItem.setPrimaryGroup(item.getPrimaryGroup());

        // New pricing data (calculated by rules or manually set)
        groupedItem.setNewUnitSellPrice(item.getNewUnitSellPrice());
        groupedItem.setNewAmount(item.getNewAmount());
        groupedItem.setNewGrossProfit(item.getNewGrossProfit());

        // Load applied rule snapshots from database
        if (item.getId() != null) {
            List<AppliedRuleSnapshot> snapshots = appliedRuleSnapshotRepository.findBySessionLineItemId(item.getId());

            if (!snapshots.isEmpty()) {
                // Store the snapshots directly
                groupedItem.setAppliedRuleSnapshots(snapshots);

                // Reconstruct PricingRule objects from snapshots
                List<PricingRule> restoredRules = new java.util.ArrayList<>();
                List<java.math.BigDecimal> intermediateResults = new java.util.ArrayList<>();

                // Add starting cost as first intermediate result
                intermediateResults.add(item.getIncomingCost());

                for (AppliedRuleSnapshot snapshot : snapshots) {
                    // Try to fetch current rule from database (might have changed)
                    PricingRule rule = null;
                    if (snapshot.getRuleId() != null) {
                        PricingRule fullRule = pricingRuleRepository.findById(snapshot.getRuleId());
                        if (fullRule != null) {
                            rule = fullRule;
                            log.debug("Restored rule {} from database", snapshot.getRuleName());
                        }
                    }

                    // If rule not found, reconstruct from snapshot
                    if (rule == null) {
                        rule = new PricingRule();
                        rule.setId(snapshot.getRuleId());
                        rule.setRuleName(snapshot.getRuleName());
                        rule.setPricingMethod(snapshot.getPricingMethod());
                        rule.setPricingValue(snapshot.getPricingValue());
                        log.info("Reconstructed rule {} from snapshot (original rule not found)", snapshot.getRuleName());
                    }

                    restoredRules.add(rule);
                    intermediateResults.add(snapshot.getOutputPrice());
                }

                groupedItem.setAppliedRules(restoredRules);
                groupedItem.setIntermediateResults(intermediateResults);

                log.info("Loaded {} rule snapshots for product {} customer {}",
                        snapshots.size(), item.getProductCode(), item.getCustomerCode());
            }
        }

        // Manual override flag
        groupedItem.setManualOverride(item.getManualOverride() != null ? item.getManualOverride() : false);

        return groupedItem;
    }
}
