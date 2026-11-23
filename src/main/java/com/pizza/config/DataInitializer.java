package com.pizza.config;

import com.pizza.model.PizzaSize;
import com.pizza.model.Topping;
import com.pizza.model.ToppingCategory;
import com.pizza.repository.PizzaSizeRepository;
import com.pizza.repository.ToppingCategoryRepository;
import com.pizza.repository.ToppingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the database with menu data on first startup.
 * Skips seeding if data already exists (safe for restarts with persistent SQLite).
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final PizzaSizeRepository sizeRepository;
    private final ToppingCategoryRepository categoryRepository;
    private final ToppingRepository toppingRepository;

    public DataInitializer(PizzaSizeRepository sizeRepository,
                           ToppingCategoryRepository categoryRepository,
                           ToppingRepository toppingRepository) {
        this.sizeRepository = sizeRepository;
        this.categoryRepository = categoryRepository;
        this.toppingRepository = toppingRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (sizeRepository.count() > 0) {
            log.info("Menu data already seeded — skipping initialization.");
            return;
        }

        log.info("Seeding menu data...");

        // Pizza sizes
        PizzaSize small = sizeRepository.save(new PizzaSize("Small", new BigDecimal("5.00")));
        PizzaSize medium = sizeRepository.save(new PizzaSize("Medium", new BigDecimal("7.00")));
        PizzaSize large = sizeRepository.save(new PizzaSize("Large", new BigDecimal("12.00")));
        log.info("Seeded {} pizza sizes.", 3);

        // Topping categories
        ToppingCategory basic = categoryRepository.save(
                new ToppingCategory("Basic", new BigDecimal("0.08")));
        ToppingCategory premium = categoryRepository.save(
                new ToppingCategory("Premium", new BigDecimal("0.11")));
        log.info("Seeded {} topping categories.", 2);

        // Basic toppings
        toppingRepository.saveAll(List.of(
                new Topping("Pepperoni", basic),
                new Topping("Sausage", basic),
                new Topping("Extra Cheese", basic),
                new Topping("Onions", basic),
                new Topping("Black Olives", basic),
                new Topping("Green Peppers", basic)
        ));

        // Premium toppings
        toppingRepository.saveAll(List.of(
                new Topping("Bacon", premium),
                new Topping("Chicken", premium),
                new Topping("Garlic", premium),
                new Topping("Artichoke Hearts", premium),
                new Topping("Sun-Dried Tomatoes", premium),
                new Topping("Pineapple", premium)
        ));

        log.info("Seeded 12 toppings (6 basic, 6 premium).");
        log.info("Menu data seeding complete.");
    }
}

