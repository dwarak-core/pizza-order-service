package com.pizza.service;

import com.pizza.model.*;
import com.pizza.service.promo.PromoCodeRegistry;
import com.pizza.service.promo.PromoCodeStrategy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Single source of truth for all pricing calculations.
 * Uses BigDecimal with HALF_UP rounding at every displayed amount.
 */
@Service
public class PricingService {

    private static final BigDecimal TAX_RATE = new BigDecimal("0.065");
    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final PromoCodeRegistry promoCodeRegistry;

    public PricingService(PromoCodeRegistry promoCodeRegistry) {
        this.promoCodeRegistry = promoCodeRegistry;
    }

    /**
     * Calculates the full pricing breakdown for an order.
     *
     * @param lineItems the pizza line items
     * @param promoCode optional promo code (may be null)
     * @param tipMode   optional tip mode (may be null)
     * @param tipValue  optional tip value (may be null)
     * @return full pricing breakdown
     */
    public PricingResult calculatePricing(List<LineItem> lineItems, String promoCode,
                                          TipMode tipMode, BigDecimal tipValue) {
        // 1. Calculate each line item
        List<PricingResult.LineItemPricing> lineItemPricings = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (LineItem item : lineItems) {
            BigDecimal basePrice = item.getSize().getBasePrice();
            BigDecimal toppingsPrice = BigDecimal.ZERO;
            List<String> toppingNames = new ArrayList<>();

            for (Topping topping : item.getToppings()) {
                BigDecimal toppingCost = basePrice
                        .multiply(topping.getCategory().getPricePercentage())
                        .setScale(SCALE, ROUNDING);
                toppingsPrice = toppingsPrice.add(toppingCost);
                toppingNames.add(topping.getName());
            }

            BigDecimal unitPrice = basePrice.add(toppingsPrice);
            BigDecimal lineTotal = unitPrice
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(SCALE, ROUNDING);

            lineItemPricings.add(new PricingResult.LineItemPricing(
                    item.getSize().getName(),
                    item.getQuantity(),
                    toppingNames,
                    unitPrice.setScale(SCALE, ROUNDING),
                    toppingsPrice.setScale(SCALE, ROUNDING),
                    lineTotal
            ));

            subtotal = subtotal.add(lineTotal);
        }

        // 2. Apply promo code
        BigDecimal discount = BigDecimal.ZERO;
        String appliedPromo = null;

        if (promoCode != null && !promoCode.isBlank()) {
            Optional<PromoCodeStrategy> strategy = promoCodeRegistry.findByCode(promoCode);
            if (strategy.isPresent() && strategy.get().isApplicable(subtotal)) {
                discount = strategy.get().calculateDiscount(subtotal);
                appliedPromo = strategy.get().getCode();
            }
        }

        BigDecimal taxableAmount = subtotal.subtract(discount).setScale(SCALE, ROUNDING);
        if (taxableAmount.compareTo(BigDecimal.ZERO) < 0) {
            taxableAmount = BigDecimal.ZERO.setScale(SCALE);
        }

        // 3. Tax
        BigDecimal tax = taxableAmount.multiply(TAX_RATE).setScale(SCALE, ROUNDING);

        // 4. Tip
        BigDecimal tip = calculateTip(taxableAmount, tax, tipMode, tipValue);

        // 5. Total
        BigDecimal total = taxableAmount.add(tax).add(tip);

        return new PricingResult(
                subtotal.setScale(SCALE, ROUNDING),
                discount.setScale(SCALE, ROUNDING),
                appliedPromo,
                taxableAmount,
                tax,
                tip,
                total.setScale(SCALE, ROUNDING),
                lineItemPricings
        );
    }

    private BigDecimal calculateTip(BigDecimal taxableAmount, BigDecimal tax,
                                     TipMode tipMode, BigDecimal tipValue) {
        if (tipMode == null || tipValue == null) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        if (tipValue.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }

        return switch (tipMode) {
            case DOLLAR -> tipValue.setScale(SCALE, ROUNDING);
            case PERCENTAGE -> {
                BigDecimal base = taxableAmount.add(tax);
                yield base.multiply(tipValue.divide(new BigDecimal("100"), 10, ROUNDING))
                        .setScale(SCALE, ROUNDING);
            }
        };
    }

    /**
     * Validates a promo code and returns an error message if invalid.
     */
    public Optional<String> validatePromoCode(String promoCode, BigDecimal subtotal) {
        if (promoCode == null || promoCode.isBlank()) {
            return Optional.empty();
        }
        Optional<PromoCodeStrategy> strategy = promoCodeRegistry.findByCode(promoCode);
        if (strategy.isEmpty()) {
            return Optional.of("Invalid promo code: " + promoCode);
        }
        if (!strategy.get().isApplicable(subtotal)) {
            return Optional.of("Promo code " + promoCode +
                    " is not applicable (order subtotal must be over $20)");
        }
        return Optional.empty();
    }
}
