package com.pizza.service.promo;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Strategy interface for promo code logic.
 * Adding a new promo code means implementing this interface
 * and registering it in PromoCodeRegistry — no changes to core pricing.
 */
public interface PromoCodeStrategy {
    /** The promo code string (case-insensitive). */
    String getCode();

    /** Whether this promo can be applied given the order subtotal. */
    boolean isApplicable(BigDecimal subtotal);

    /** Calculate the discount amount for the given subtotal. */
    BigDecimal calculateDiscount(BigDecimal subtotal);

    /** Codes of other promos this one cannot be combined with. */
    Set<String> conflictsWith();
}
