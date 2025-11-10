package com.meatrics.pricing;

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
                    .set(CUSTOMERS.NOTES, customer.getNotes())
                    .onDuplicateKeyUpdate()
                    .set(CUSTOMERS.CUSTOMER_NAME, customer.getCustomerName())
                    .set(CUSTOMERS.MODIFIED_DATE, LocalDateTime.now())
                    .execute();
        } else {
            // Update existing customer
            dsl.update(CUSTOMERS)
                    .set(CUSTOMERS.CUSTOMER_NAME, customer.getCustomerName())
                    .set(CUSTOMERS.CUSTOMER_RATING, customer.getCustomerRating())
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
}
