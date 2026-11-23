package com.pizza.model;

/**
 * How the customer specifies the tip.
 */
public enum TipMode {
    /** Tip is a flat dollar amount. */
    DOLLAR,
    /** Tip is a percentage of the total bill (subtotal after discount + tax). */
    PERCENTAGE
}

