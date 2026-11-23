package com.pizza.service.promo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * ILOVEPIZZA: 10% discount on the total order before tax.
 */
public class ILovePizzaPromo implements PromoCodeStrategy {

    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.10");

    @Override
    public String getCode() {
        return "ILOVEPIZZA";
    }

    @Override
    public boolean isApplicable(BigDecimal subtotal) {
        return subtotal.compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        return subtotal.multiply(DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public Set<String> conflictsWith() {
        return Set.of("SAVE5");
    }
}
