package com.pizza.service.promo;

import java.math.BigDecimal;
import java.util.Set;

/**
 * SAVE5: $5 off the order if the order total before tax is over $20.
 * Cannot be combined with ILOVEPIZZA.
 */
public class Save5Promo implements PromoCodeStrategy {

    private static final BigDecimal DISCOUNT = new BigDecimal("5.00");
    private static final BigDecimal MIN_SUBTOTAL = new BigDecimal("20.00");

    @Override
    public String getCode() {
        return "SAVE5";
    }

    @Override
    public boolean isApplicable(BigDecimal subtotal) {
        // "takes $5 off the order if the order total before tax is over $20"
        return subtotal.compareTo(MIN_SUBTOTAL) > 0;
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        return DISCOUNT;
    }

    @Override
    public Set<String> conflictsWith() {
        return Set.of("ILOVEPIZZA");
    }
}
