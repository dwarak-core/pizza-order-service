package com.pizza.repository;

import com.pizza.model.PizzaOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PizzaOrderRepository extends JpaRepository<PizzaOrder, Long> {
    Optional<PizzaOrder> findByIdempotencyKey(String idempotencyKey);
}

