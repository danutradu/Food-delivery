package com.food.delivery.catalog.controller;

import com.food.delivery.catalog.dto.MenuItemResponse;
import com.food.delivery.catalog.dto.MenuItemUpsert;
import com.food.delivery.catalog.dto.RestaurantResponse;
import com.food.delivery.catalog.dto.RestaurantUpsert;
import com.food.delivery.catalog.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class CatalogController {

    private final CatalogService catalogService;

    private UUID getUserId(Authentication auth) {
        return UUID.fromString(((Jwt) auth.getPrincipal()).getSubject());
    }

    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
    @PostMapping("/restaurants")
    public RestaurantResponse createRestaurant(@Valid @RequestBody RestaurantUpsert req, Authentication auth) {
        return catalogService.createRestaurant(req, getUserId(auth));
    }

    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
    @PostMapping("/restaurants/{id}/menu/items")
    public MenuItemResponse createMenuItem(@PathVariable("id") UUID restaurantId, @Valid @RequestBody MenuItemUpsert req, Authentication auth) {
        return catalogService.createMenuItem(restaurantId, req, getUserId(auth));
    }

    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER','ADMIN')")
    @PutMapping("/restaurants/{id}/menu/items/{itemId}")
    public MenuItemResponse updateMenuItem(@PathVariable("id") UUID restaurantId, @PathVariable UUID itemId, @Valid @RequestBody MenuItemUpsert req, Authentication auth) {
        return catalogService.updateMenuItem(restaurantId, itemId, req, getUserId(auth));
    }

    @GetMapping("/restaurants")
    public Page<RestaurantResponse> getRestaurants(@PageableDefault(size = 20) Pageable pageable) {
        return catalogService.getAllRestaurants(pageable);
    }

    @GetMapping("/restaurants/{id}")
    public RestaurantResponse getRestaurant(@PathVariable UUID id) {
        return catalogService.getRestaurant(id);
    }

    @GetMapping("/restaurants/{id}/menu")
    public Page<MenuItemResponse> getMenu(@PathVariable UUID id, @PageableDefault(size = 50) Pageable pageable) {
        return catalogService.getMenu(id, pageable);
    }

    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    @DeleteMapping("/restaurants/{restaurantId}/menu/items/{itemId}")
    public void deleteMenuItem(@PathVariable UUID restaurantId, @PathVariable UUID itemId, Authentication auth) {
        catalogService.deleteMenuItem(restaurantId, itemId, getUserId(auth));
    }

    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    @PutMapping("/restaurants/{restaurantId}/menu/items/{itemId}/availability")
    public MenuItemResponse setAvailability(@PathVariable UUID restaurantId, @PathVariable UUID itemId, @RequestParam boolean available, Authentication auth) {
        return catalogService.setMenuItemAvailability(restaurantId, itemId, available, getUserId(auth));
    }

    @PreAuthorize("hasAnyRole('RESTAURANT_OWNER', 'ADMIN')")
    @PutMapping("/restaurants/{id}/status")
    public RestaurantResponse setRestaurantStatus(@PathVariable UUID id, @RequestParam boolean open, Authentication auth) {
        return catalogService.setRestaurantStatus(id, open, getUserId(auth));
    }
}
