package com.meatrics.pricing.customer;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.meatrics.generated.Tables.CUSTOMERS;

/**
 * Repository for customer data access
 */
@Repository
public class CustomerRepository {

    private final DSLContext dsl;

    public CustomerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Save or update a customer (UPSERT based on customer_code)
     */
    public void save(Customer customer) {
        if (customer.getCustomerId() == null) {
            // Insert new customer
            dsl.insertInto(CUSTOMERS)
                    .set(CUSTOMERS.CUSTOMER_CODE, customer.getCustomerCode())
                    .set(CUSTOMERS.CUSTOMER_NAME, customer.getCustomerName())
                    .set(CUSTOMERS.CUSTOMER_RATING, customer.getCustomerRating())
                    .set(CUSTOMERS.ENTITY_TYPE, customer.getEntityType() != null ? customer.getEntityType() : "COMPANY")
                    .set(CUSTOMERS.PARENT_ID, customer.getParentId())
                    .set(CUSTOMERS.NOTES, customer.getNotes())
                    .onDuplicateKeyUpdate()
                    .set(CUSTOMERS.CUSTOMER_NAME, customer.getCustomerName())
                    // Note: entity_type and parent_id are NOT updated on duplicate
                    // These should only be managed through the UI, not through imports
                    .set(CUSTOMERS.MODIFIED_DATE, LocalDateTime.now())
                    .execute();
        } else {
            // Update existing customer
            dsl.update(CUSTOMERS)
                    .set(CUSTOMERS.CUSTOMER_NAME, customer.getCustomerName())
                    .set(CUSTOMERS.CUSTOMER_RATING, customer.getCustomerRating())
                    .set(CUSTOMERS.ENTITY_TYPE, customer.getEntityType() != null ? customer.getEntityType() : "COMPANY")
                    .set(CUSTOMERS.PARENT_ID, customer.getParentId())
                    .set(CUSTOMERS.NOTES, customer.getNotes())
                    .set(CUSTOMERS.MODIFIED_DATE, LocalDateTime.now())
                    .where(CUSTOMERS.CUSTOMER_ID.eq(customer.getCustomerId()))
                    .execute();
        }
    }

    /**
     * Find customer by customer code
     */
    public Optional<Customer> findByCustomerCode(String customerCode) {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.CUSTOMER_CODE.eq(customerCode))
                .fetchOptional(this::mapToCustomer);
    }

    /**
     * Find customer by customer name
     */
    public Optional<Customer> findByCustomerName(String customerName) {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.CUSTOMER_NAME.eq(customerName))
                .fetchOptional(this::mapToCustomer);
    }

    /**
     * Get all customers
     */
    public List<Customer> findAll() {
        return dsl.selectFrom(CUSTOMERS)
                .orderBy(CUSTOMERS.CUSTOMER_NAME)
                .fetch(this::mapToCustomer);
    }

    private Customer mapToCustomer(org.jooq.Record record) {
        Customer customer = new Customer();
        customer.setCustomerId(record.get(CUSTOMERS.CUSTOMER_ID));
        customer.setCustomerCode(record.get(CUSTOMERS.CUSTOMER_CODE));
        customer.setCustomerName(record.get(CUSTOMERS.CUSTOMER_NAME));
        customer.setCustomerRating(record.get(CUSTOMERS.CUSTOMER_RATING));
        customer.setEntityType(record.get(CUSTOMERS.ENTITY_TYPE));
        customer.setParentId(record.get(CUSTOMERS.PARENT_ID));
        customer.setNotes(record.get(CUSTOMERS.NOTES));
        customer.setCreatedDate(record.get(CUSTOMERS.CREATED_DATE));
        customer.setModifiedDate(record.get(CUSTOMERS.MODIFIED_DATE));
        return customer;
    }

    /**
     * Find customer by code - convenience method for pricing engine
     */
    public Customer findByCode(String customerCode) {
        return findByCustomerCode(customerCode).orElse(null);
    }

    /**
     * Find customer by ID
     */
    public Optional<Customer> findById(Long customerId) {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.CUSTOMER_ID.eq(customerId))
                .fetchOptional(this::mapToCustomer);
    }

    /**
     * Find all groups
     */
    public List<Customer> findAllGroups() {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.ENTITY_TYPE.eq("GROUP"))
                .orderBy(CUSTOMERS.CUSTOMER_NAME)
                .fetch(this::mapToCustomer);
    }

    /**
     * Find all standalone companies (not in any group)
     */
    public List<Customer> findAllStandaloneCompanies() {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.ENTITY_TYPE.eq("COMPANY")
                        .and(CUSTOMERS.PARENT_ID.isNull()))
                .orderBy(CUSTOMERS.CUSTOMER_NAME)
                .fetch(this::mapToCustomer);
    }

    /**
     * Find all companies that belong to a specific group
     */
    public List<Customer> findCompaniesByGroupId(Long groupId) {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.PARENT_ID.eq(groupId))
                .orderBy(CUSTOMERS.CUSTOMER_NAME)
                .fetch(this::mapToCustomer);
    }

    /**
     * Find all selectable entities for pricing sessions
     * (Groups + standalone companies, excluding companies in groups)
     * Groups are listed first, then standalone companies
     */
    public List<Customer> findAllSelectableForPricing() {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.ENTITY_TYPE.eq("GROUP")
                        .or(CUSTOMERS.ENTITY_TYPE.eq("COMPANY")
                                .and(CUSTOMERS.PARENT_ID.isNull())))
                .orderBy(CUSTOMERS.ENTITY_TYPE.desc(), CUSTOMERS.CUSTOMER_NAME)
                .fetch(this::mapToCustomer);
    }

    /**
     * Delete a customer
     */
    public void delete(Long customerId) {
        dsl.deleteFrom(CUSTOMERS)
                .where(CUSTOMERS.CUSTOMER_ID.eq(customerId))
                .execute();
    }
}
