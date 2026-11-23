package com.pizza.service.promo;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Registry of all available promo codes.
 * To add a new promo: implement PromoCodeStrategy and register it here.
 */
@Component
public class PromoCodeRegistry {

    private final Map<String, PromoCodeStrategy> strategies = new LinkedHashMap<>();

    public PromoCodeRegistry() {
        register(new ILovePizzaPromo());
        register(new Save5Promo());
    }

    public void register(PromoCodeStrategy strategy) {
        strategies.put(strategy.getCode().toUpperCase(), strategy);
    }

    public Optional<PromoCodeStrategy> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(strategies.get(code.trim().toUpperCase()));
    }

    public Collection<PromoCodeStrategy> getAll() {
        return Collections.unmodifiableCollection(strategies.values());
    }
}
