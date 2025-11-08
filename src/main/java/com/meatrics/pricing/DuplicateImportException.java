package com.meatrics.pricing;

import java.util.List;

/**
 * Exception thrown when duplicate records are detected during import.
 * This exception is thrown BEFORE any data is inserted into the database,
 * ensuring that no partial imports occur.
 */
public class DuplicateImportException extends RuntimeException {

    private final List<String> duplicateDetails;
    private final int duplicateCount;

    /**
     * Constructs a new DuplicateImportException with the specified message and duplicate details
     *
     * @param message A descriptive message about the duplicate import
     * @param duplicateDetails List of strings describing each duplicate record found
     */
    public DuplicateImportException(String message, List<String> duplicateDetails) {
        super(message);
        this.duplicateDetails = duplicateDetails;
        this.duplicateCount = duplicateDetails != null ? duplicateDetails.size() : 0;
    }

    /**
     * Get the list of duplicate record descriptions
     *
     * @return List of duplicate details
     */
    public List<String> getDuplicateDetails() {
        return duplicateDetails;
    }

    /**
     * Get the number of duplicate records found
     *
     * @return Count of duplicates
     */
    public int getDuplicateCount() {
        return duplicateCount;
    }
}
