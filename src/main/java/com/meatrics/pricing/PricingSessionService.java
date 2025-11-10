package com.meatrics.pricing;

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

    private final PricingSessionRepository pricingSessionRepository;
    private final PricingSessionLineItemRepository lineItemRepository;
    private final CustomerRepository customerRepository;

    public PricingSessionService(PricingSessionRepository pricingSessionRepository,
                                 PricingSessionLineItemRepository lineItemRepository,
                                 CustomerRepository customerRepository) {
        this.pricingSessionRepository = pricingSessionRepository;
        this.lineItemRepository = lineItemRepository;
        this.customerRepository = customerRepository;
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
            session.setStatus("IN_PROGRESS");
        }

        // Save session (insert or update)
        session = pricingSessionRepository.save(session);

        // Convert GroupedLineItems to PricingSessionLineItems
        List<PricingSessionLineItem> sessionLineItems = lineItems.stream()
                .map(item -> convertToSessionLineItem(item))
                .collect(Collectors.toList());

        // Save all line items
        lineItemRepository.saveAll(session.getId(), sessionLineItems);

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
        lineItem.setIncomingCost(item.getIncomingCost());
        lineItem.setPrimaryGroup(item.getPrimaryGroup());

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
        groupedItem.setIncomingCost(item.getIncomingCost());
        groupedItem.setPrimaryGroup(item.getPrimaryGroup());

        return groupedItem;
    }
}
