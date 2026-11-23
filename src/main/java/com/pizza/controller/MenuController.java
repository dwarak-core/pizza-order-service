package com.pizza.controller;

import com.pizza.dto.MenuResponse;
import com.pizza.repository.PizzaSizeRepository;
import com.pizza.repository.ToppingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final PizzaSizeRepository sizeRepository;
    private final ToppingRepository toppingRepository;

    public MenuController(PizzaSizeRepository sizeRepository, ToppingRepository toppingRepository) {
        this.sizeRepository = sizeRepository;
        this.toppingRepository = toppingRepository;
    }

    /** Returns available pizza sizes and toppings for the order builder. */
    @GetMapping
    public MenuResponse getMenu() {
        var sizes = sizeRepository.findAll().stream()
                .map(s -> new MenuResponse.SizeOption(s.getId(), s.getName(), s.getBasePrice()))
                .toList();

        var toppings = toppingRepository.findAll().stream()
                .map(t -> new MenuResponse.ToppingOption(
                        t.getId(), t.getName(),
                        t.getCategory().getName(),
                        t.getCategory().getPricePercentage()))
                .toList();

        return new MenuResponse(sizes, toppings);
    }
}
