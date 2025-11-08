package com.meatrics.base.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Main;

/**
 * Abstract base class for views that contain grids with column visibility management.
 * Provides common functionality for saving/restoring column visibility preferences
 * to/from browser localStorage.
 */
public abstract class AbstractGridView extends Main {

    /**
     * Get the storage prefix for this view's column visibility settings.
     * Each view should return a unique prefix to avoid conflicts.
     *
     * @return Storage prefix string (e.g., "PricingDataView-column-")
     */
    protected abstract String getStoragePrefix();

    /**
     * Save column visibility setting to browser localStorage.
     *
     * @param columnKey The key identifying the column
     * @param visible Whether the column is visible
     */
    protected void saveColumnVisibility(String columnKey, boolean visible) {
        getElement().executeJs(
            "localStorage.setItem($0, $1)",
            getStoragePrefix() + columnKey,
            String.valueOf(visible)
        );
    }

    /**
     * Restore individual column visibility from localStorage.
     * If the stored value is "false", the checkbox will be set to false,
     * which triggers its listener to hide the column and re-save the setting.
     *
     * @param columnKey The key identifying the column
     * @param checkbox The checkbox controlling column visibility
     */
    protected void restoreColumn(String columnKey, Checkbox checkbox) {
        UI ui = UI.getCurrent();
        getElement().executeJs(
            "return localStorage.getItem($0)",
            getStoragePrefix() + columnKey
        ).then(String.class, value -> {
            if ("false".equals(value)) {
                ui.access(() -> checkbox.setValue(false)); // This triggers the listener which updates grid and saves
            }
            // If true or null, keep default (true)
        });
    }
}
