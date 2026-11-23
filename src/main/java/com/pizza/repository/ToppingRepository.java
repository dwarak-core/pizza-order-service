package com.pizza.repository;

import com.pizza.model.Topping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface ToppingRepository extends JpaRepository<Topping, Long> {
    Optional<Topping> findByNameIgnoreCase(String name);
    Set<Topping> findByIdIn(Set<Long> ids);
}

