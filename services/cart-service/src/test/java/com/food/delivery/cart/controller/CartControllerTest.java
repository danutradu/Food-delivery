package com.food.delivery.cart.controller;

import com.food.delivery.cart.config.SecurityConfig;
import com.food.delivery.cart.dto.CheckoutResponse;
import com.food.delivery.cart.exception.EmptyCartException;
import com.food.delivery.cart.exception.GlobalExceptionHandler;
import com.food.delivery.cart.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@ContextConfiguration(classes = {
        CartController.class,
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class CartControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CartService cartService;

    @Test
    void checkout_returnsAcceptedWithProcessingResponse() throws Exception {
        var customerId = UUID.randomUUID();
        var cartId = UUID.randomUUID();
        when(cartService.checkout(customerId, cartId.toString()))
                .thenReturn(new CheckoutResponse(cartId));

        mockMvc.perform(post("/cart/checkout")
                        .header("Idempotency-Key", cartId.toString())
                        .with(jwt().jwt(jwt -> jwt.subject(customerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.cartId").value(cartId.toString()));
    }

    @Test
    void checkout_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/cart/checkout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkout_withoutIdempotencyKey_returnsProblemDetail() throws Exception {
        mockMvc.perform(post("/cart/checkout")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void checkout_emptyCart_returnsProblemDetail() throws Exception {
        var customerId = UUID.randomUUID();
        var cartId = UUID.randomUUID();
        when(cartService.checkout(customerId, cartId.toString())).thenThrow(new EmptyCartException(customerId));

        mockMvc.perform(post("/cart/checkout")
                        .header("Idempotency-Key", cartId.toString())
                        .with(jwt().jwt(jwt -> jwt.subject(customerId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
