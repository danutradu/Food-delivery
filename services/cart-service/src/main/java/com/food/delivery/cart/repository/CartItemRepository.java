package com.food.delivery.cart.repository;

import com.food.delivery.cart.model.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {

    @Modifying
    @Query("UPDATE CartItemEntity i SET i.name = :name, i.unitPrice = :price WHERE i.menuItemId = :menuItemId")
    int updateByMenuItemId(@Param("menuItemId") UUID menuItemId,
                           @Param("name") String name,
                           @Param("price") BigDecimal price);
}
