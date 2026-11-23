package com.pizza.repository;

import com.pizza.model.PizzaSize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PizzaSizeRepository extends JpaRepository<PizzaSize, Long> {
    Optional<PizzaSize> findByNameIgnoreCase(String name);
}

