package com.learnwithashfaq.artemis.repository;

import com.learnwithashfaq.artemis.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * OrderRepository — Spring Data JPA repository for OrderEntity.
 *
 * ═══════════════════════════════════════════════════════════════
 * WHAT IS JpaRepository?
 * ═══════════════════════════════════════════════════════════════
 *
 * By extending JpaRepository, Spring auto-generates these methods for FREE:
 *
 *   save(entity)       → INSERT or UPDATE
 *   findById(id)       → SELECT by primary key
 *   findAll()          → SELECT all rows
 *   deleteById(id)     → DELETE by primary key
 *   count()            → COUNT rows
 *   existsById(id)     → Check if row exists
 *
 * You don't write ANY SQL or implementation code — Spring generates it all!
 *
 * ═══════════════════════════════════════════════════════════════
 * CUSTOM QUERY METHOD:
 * ═══════════════════════════════════════════════════════════════
 *
 * findByOrderId(String orderId)
 *   → Spring parses the method name and generates:
 *     SELECT * FROM orders WHERE order_id = ?
 *
 * This is called "derived query methods" — one of Spring Data's
 * most powerful features. The method name IS the query.
 */
@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    /**
     * Find an order by its business order ID (e.g., "ORD-A1B2C3D4").
     * Returns Optional because the order might not exist yet.
     *
     * Used by:
     *   - OrderProcessingService to check if order already exists (idempotency)
     *   - Integration tests to verify orders were saved correctly
     */
    Optional<OrderEntity> findByOrderId(String orderId);

    void deleteAll();
}
