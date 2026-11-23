package com.pizza.service;

import com.pizza.model.*;
import com.pizza.service.promo.PromoCodeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private PricingService pricingService;
    private PizzaSize small, medium, large;
    private ToppingCategory basicCat, premiumCat;
    private Topping pepperoni, bacon;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(new PromoCodeRegistry());

        small = new PizzaSize("Small", new BigDecimal("5.00"));
        medium = new PizzaSize("Medium", new BigDecimal("7.00"));
        large = new PizzaSize("Large", new BigDecimal("12.00"));

        basicCat = new ToppingCategory("Basic", new BigDecimal("0.08"));
        premiumCat = new ToppingCategory("Premium", new BigDecimal("0.11"));

        pepperoni = new Topping("Pepperoni", basicCat);
        bacon = new Topping("Bacon", premiumCat);
    }

    private PricingResult price(List<LineItem> items) {
        return pricingService.calculatePricing(items, null, null, null);
    }

    private PricingResult price(List<LineItem> items, String promo) {
        return pricingService.calculatePricing(items, promo, null, null);
    }

    private PricingResult price(List<LineItem> items, String promo, TipMode tipMode, BigDecimal tipValue) {
        return pricingService.calculatePricing(items, promo, tipMode, tipValue);
    }

    // --- Base pricing ---

    @Nested
    @DisplayName("Base pricing")
    class BasePricing {

        @Test
        @DisplayName("Single small pizza, no toppings")
        void singleSmallNoToppings() {
            LineItem item = new LineItem(small, 1);
            PricingResult r = price(List.of(item));

            assertEquals(bd("5.00"), r.subtotal());
            assertEquals(bd("0.00"), r.discount());
            // tax = 5.00 * 0.065 = 0.325 → 0.33
            assertEquals(bd("0.33"), r.tax());
            assertEquals(bd("5.33"), r.total());
        }

        @Test
        @DisplayName("Single large pizza, no toppings")
        void singleLargeNoToppings() {
            LineItem item = new LineItem(large, 1);
            PricingResult r = price(List.of(item));

            assertEquals(bd("12.00"), r.subtotal());
            // tax = 12.00 * 0.065 = 0.78
            assertEquals(bd("0.78"), r.tax());
            assertEquals(bd("12.78"), r.total());
        }

        @Test
        @DisplayName("Multiple pizzas via quantity")
        void multipleQuantity() {
            LineItem item = new LineItem(medium, 3);
            PricingResult r = price(List.of(item));

            // 7.00 * 3 = 21.00
            assertEquals(bd("21.00"), r.subtotal());
            // tax = 21.00 * 0.065 = 1.365 → 1.37
            assertEquals(bd("1.37"), r.tax());
            assertEquals(bd("22.37"), r.total());
        }

        @Test
        @DisplayName("Empty order")
        void emptyOrder() {
            PricingResult r = price(List.of());

            assertEquals(bd("0.00"), r.subtotal());
            assertEquals(bd("0.00"), r.tax());
            assertEquals(bd("0.00"), r.total());
        }
    }

    // --- Topping pricing ---

    @Nested
    @DisplayName("Topping pricing")
    class ToppingPricing {

        @Test
        @DisplayName("Basic topping: 8% of base price")
        void basicTopping() {
            LineItem item = new LineItem(large, 1);
            item.addTopping(pepperoni);
            PricingResult r = price(List.of(item));

            // base = 12.00, topping = 12.00 * 0.08 = 0.96
            // unit = 12.96, line = 12.96
            assertEquals(bd("12.96"), r.subtotal());
        }

        @Test
        @DisplayName("Premium topping: 11% of base price")
        void premiumTopping() {
            LineItem item = new LineItem(large, 1);
            item.addTopping(bacon);
            PricingResult r = price(List.of(item));

            // base = 12.00, topping = 12.00 * 0.11 = 1.32
            // unit = 13.32, line = 13.32
            assertEquals(bd("13.32"), r.subtotal());
        }

        @Test
        @DisplayName("Mixed basic + premium toppings")
        void mixedToppings() {
            LineItem item = new LineItem(large, 1);
            item.addTopping(pepperoni);
            item.addTopping(bacon);
            PricingResult r = price(List.of(item));

            // base = 12.00, basic = 0.96, premium = 1.32
            // unit = 14.28, line = 14.28
            assertEquals(bd("14.28"), r.subtotal());
        }

        @Test
        @DisplayName("Toppings scale with quantity")
        void toppingsWithQuantity() {
            LineItem item = new LineItem(small, 2);
            item.addTopping(pepperoni);
            PricingResult r = price(List.of(item));

            // base = 5.00, topping = 5.00 * 0.08 = 0.40
            // unit = 5.40, line = 5.40 * 2 = 10.80
            assertEquals(bd("10.80"), r.subtotal());
        }
    }

    // --- Multi-line-item ---

    @Nested
    @DisplayName("Multi-line-item orders")
    class MultiLineItem {

        @Test
        @DisplayName("Two different line items sum correctly")
        void twoLineItems() {
            LineItem item1 = new LineItem(large, 1);
            item1.addTopping(pepperoni);
            item1.addTopping(bacon);

            LineItem item2 = new LineItem(small, 2);

            PricingResult r = price(List.of(item1, item2));

            // item1: 12.00 + 0.96 + 1.32 = 14.28
            // item2: 5.00 * 2 = 10.00
            // subtotal = 24.28
            assertEquals(bd("24.28"), r.subtotal());
        }
    }

    // --- Promo codes ---

    @Nested
    @DisplayName("Promo codes")
    class PromoCodes {

        @Test
        @DisplayName("ILOVEPIZZA: 10% discount")
        void ilovepizza() {
            LineItem item = new LineItem(large, 2);
            PricingResult r = price(List.of(item), "ILOVEPIZZA");

            // subtotal = 24.00, discount = 24.00 * 0.10 = 2.40
            assertEquals(bd("24.00"), r.subtotal());
            assertEquals(bd("2.40"), r.discount());
            assertEquals("ILOVEPIZZA", r.appliedPromoCode());
            // taxable = 21.60, tax = 21.60 * 0.065 = 1.404 → 1.40
            assertEquals(bd("21.60"), r.taxableAmount());
            assertEquals(bd("1.40"), r.tax());
            assertEquals(bd("23.00"), r.total());
        }

        @Test
        @DisplayName("SAVE5: $5 off when subtotal > $20")
        void save5Over20() {
            LineItem item = new LineItem(large, 2);
            PricingResult r = price(List.of(item), "SAVE5");

            // subtotal = 24.00, discount = 5.00
            assertEquals(bd("24.00"), r.subtotal());
            assertEquals(bd("5.00"), r.discount());
            assertEquals("SAVE5", r.appliedPromoCode());
            // taxable = 19.00, tax = 19.00 * 0.065 = 1.235 → 1.24
            assertEquals(bd("19.00"), r.taxableAmount());
            assertEquals(bd("1.24"), r.tax());
            assertEquals(bd("20.24"), r.total());
        }

        @Test
        @DisplayName("SAVE5: not applicable when subtotal <= $20")
        void save5Under20() {
            LineItem item = new LineItem(small, 1);
            PricingResult r = price(List.of(item), "SAVE5");

            // subtotal = 5.00, SAVE5 requires > 20, so no discount
            assertEquals(bd("5.00"), r.subtotal());
            assertEquals(bd("0.00"), r.discount());
            assertNull(r.appliedPromoCode());
        }

        @Test
        @DisplayName("Invalid promo code is ignored")
        void invalidPromo() {
            LineItem item = new LineItem(large, 1);
            PricingResult r = price(List.of(item), "FAKECODE");

            assertEquals(bd("0.00"), r.discount());
            assertNull(r.appliedPromoCode());
        }

        @Test
        @DisplayName("Case-insensitive promo codes")
        void caseInsensitive() {
            LineItem item = new LineItem(large, 2);
            PricingResult r = price(List.of(item), "ilovepizza");

            assertEquals(bd("2.40"), r.discount());
            assertEquals("ILOVEPIZZA", r.appliedPromoCode());
        }
    }

    // --- Tip ---

    @Nested
    @DisplayName("Tip calculation")
    class TipCalculation {

        @Test
        @DisplayName("Tip as dollar amount")
        void tipDollar() {
            LineItem item = new LineItem(large, 1);
            PricingResult r = price(List.of(item), null, TipMode.DOLLAR, bd("5.00"));

            assertEquals(bd("5.00"), r.tip());
            // 12.00 + 0.78 + 5.00 = 17.78
            assertEquals(bd("17.78"), r.total());
        }

        @Test
        @DisplayName("Tip as percentage of (subtotal + tax)")
        void tipPercentage() {
            LineItem item = new LineItem(large, 1);
            PricingResult r = price(List.of(item), null, TipMode.PERCENTAGE, bd("20"));

            // base = (12.00 + 0.78) * 0.20 = 12.78 * 0.20 = 2.556 → 2.56
            assertEquals(bd("2.56"), r.tip());
            // 12.00 + 0.78 + 2.56 = 15.34
            assertEquals(bd("15.34"), r.total());
        }

        @Test
        @DisplayName("No tip when tip mode is null")
        void noTip() {
            LineItem item = new LineItem(large, 1);
            PricingResult r = price(List.of(item), null, null, null);

            assertEquals(bd("0.00"), r.tip());
        }

        @Test
        @DisplayName("Negative tip throws IllegalArgumentException")
        void negativeTipRejected() {
            LineItem item = new LineItem(large, 1);
            assertThrows(IllegalArgumentException.class,
                    () -> price(List.of(item), null, TipMode.DOLLAR, bd("-1.00")));
        }
    }

    // --- Rounding ---

    @Nested
    @DisplayName("Rounding")
    class Rounding {

        @Test
        @DisplayName("All amounts are whole cents (scale 2)")
        void allAmountsWholeCents() {
            LineItem item = new LineItem(medium, 3);
            item.addTopping(pepperoni);
            item.addTopping(bacon);

            PricingResult r = price(List.of(item), "ILOVEPIZZA", TipMode.PERCENTAGE, bd("18"));

            assertEquals(2, r.subtotal().scale());
            assertEquals(2, r.discount().scale());
            assertEquals(2, r.tax().scale());
            assertEquals(2, r.tip().scale());
            assertEquals(2, r.total().scale());
        }

        @Test
        @DisplayName("Total equals sum of displayed components")
        void totalEqualsSumOfComponents() {
            LineItem item = new LineItem(medium, 3);
            item.addTopping(pepperoni);
            item.addTopping(bacon);

            PricingResult r = price(List.of(item), "ILOVEPIZZA", TipMode.DOLLAR, bd("3.50"));

            BigDecimal expected = r.taxableAmount().add(r.tax()).add(r.tip());
            assertEquals(expected, r.total());
        }
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
