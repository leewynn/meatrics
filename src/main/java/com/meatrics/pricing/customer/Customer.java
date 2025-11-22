package com.meatrics.pricing.customer;

import java.time.LocalDateTime;

/**
 * Customer entity representing the customers table
 * Supports both individual companies and groups
 */
public class Customer {
    private Long customerId;
    private String customerCode;
    private String customerName;
    private String customerRating;
    private String entityType; // 'GROUP' or 'COMPANY'
    private Long parentId; // Reference to parent group (if this is a company in a group)
    private String notes;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;

    public Customer() {
    }

    // Getters and setters
    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerRating() {
        return customerRating;
    }

    public void setCustomerRating(String customerRating) {
        this.customerRating = customerRating;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(LocalDateTime modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    /**
     * Check if this customer is a group
     */
    public boolean isGroup() {
        return "GROUP".equals(entityType);
    }

    /**
     * Check if this customer belongs to a group
     */
    public boolean belongsToGroup() {
        return parentId != null;
    }

    /**
     * Check if this customer is a standalone company
     */
    public boolean isStandaloneCompany() {
        return "COMPANY".equals(entityType) && parentId == null;
    }
}
