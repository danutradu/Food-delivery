package com.food.delivery.order.service;

import com.food.delivery.common.outbox.OutboxService;
import com.food.delivery.order.config.KafkaTopics;
import com.food.delivery.order.exception.OrderNotFoundException;
import com.food.delivery.order.model.OrderEntity;
import com.food.delivery.order.model.OrderStatus;
import com.food.delivery.order.repository.OrderRepository;
import fd.cart.CartCheckedOutV1;
import fd.cart.CartItem;
import fd.delivery.OrderDeliveredV1;
import fd.delivery.OrderPickedUpV1;
import fd.payment.FeeChargedV1;
import fd.payment.FeeFailedV1;
import fd.payment.PaymentAuthorizedV1;
import fd.payment.PaymentFailedV1;
import fd.restaurant.OrderReadyForPickupV1;
import fd.restaurant.RestaurantAcceptedV1;
import fd.restaurant.RestaurantRejectedV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    OutboxService outboxService;

    @Mock
    KafkaTopics topics;

    @InjectMocks
    OrderService orderService;

    private UUID orderId;
    private OrderEntity order;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        order = new OrderEntity();
        order.setId(orderId);
        order.setCartId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setRestaurantId(UUID.randomUUID());
        order.setTotal(new BigDecimal("12.00"));
        order.setStatus(OrderStatus.PENDING);
        order.setItems(new ArrayList<>());
    }

    @Test
    void getOrderByCartId_ownedOrder_returnsResponse() {
        when(orderRepository.findByCartId(order.getCartId())).thenReturn(Optional.of(order));

        var response = orderService.getOrderByCartId(order.getCartId(), order.getCustomerId());

        assertThat(response.orderId()).isEqualTo(orderId);
    }

    @Test
    void getOrderByCartId_otherCustomer_hidesOrder() {
        when(orderRepository.findByCartId(order.getCartId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderByCartId(order.getCartId(), UUID.randomUUID()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getOrderByCartId_missingOrder_throws() {
        var cartId = UUID.randomUUID();
        when(orderRepository.findByCartId(cartId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderByCartId(cartId, UUID.randomUUID()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void createOrderFromCart_createsOrderAndPublishesTwoEvents() {
        var cartId = UUID.randomUUID();
        var event = new CartCheckedOutV1(UUID.randomUUID(), Instant.now(), cartId,
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12.00"),
                List.of(new CartItem(UUID.randomUUID(), "Margherita", new BigDecimal("12.00"), 1)));

        when(orderRepository.findByCartId(cartId)).thenReturn(Optional.empty());
        when(orderRepository.save(any())).thenAnswer(i -> {
            var saved = (OrderEntity) i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(topics.getOrderCreated()).thenReturn("fd.order.created.v1");
        when(topics.getPaymentRequested()).thenReturn("fd.payment.requested.v1");

        orderService.createOrderFromCart(event);

        verify(outboxService, times(2)).publish(anyString(), anyString(), any());
    }

    @Test
    void createOrderFromCart_idempotent_skipsIfOrderExists() {
        var cartId = UUID.randomUUID();
        var event = new CartCheckedOutV1(UUID.randomUUID(), Instant.now(), cartId,
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12.00"), List.of());

        when(orderRepository.findByCartId(cartId)).thenReturn(Optional.of(order));

        orderService.createOrderFromCart(event);

        verify(orderRepository, never()).save(any());
        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void processPaymentAuthorized_pendingOrder_transitionsToPaymentAuthorized() {
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getRestaurantAcceptanceRequested()).thenReturn("fd.restaurant.acceptance-requested.v1");

        var event = new PaymentAuthorizedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("12.00"), "AUTH-123");
        orderService.processPaymentAuthorized(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_AUTHORIZED);
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void processPaymentAuthorized_nonPendingOrder_skips() {
        order.setStatus(OrderStatus.PAYMENT_AUTHORIZED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        var event = new PaymentAuthorizedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("12.00"), "AUTH-123");
        orderService.processPaymentAuthorized(event);

        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void processPaymentAuthorized_cancelledOrder_requestsRefundWithoutAdvancingOrder() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(topics.getRefundRequested()).thenReturn("fd.payment.refund-requested.v1");

        var event = new PaymentAuthorizedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("12.00"), "AUTH-123");
        orderService.processPaymentAuthorized(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxService).publish(anyString(), eq(orderId.toString()), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void processPaymentFailed_pendingOrder_transitionsToPaymentFailed() {
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var event = new PaymentFailedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("12.00"), "Insufficient funds");
        orderService.processPaymentFailed(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    void processRestaurantAccepted_transitionsToRestaurantAccepted() {
        order.setStatus(OrderStatus.PAYMENT_AUTHORIZED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getDeliveryRequested()).thenReturn("fd.delivery.requested.v1");

        var event = new RestaurantAcceptedV1(UUID.randomUUID(), Instant.now(), orderId, order.getRestaurantId(), 20);
        orderService.processRestaurantAccepted(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESTAURANT_ACCEPTED);
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void processRestaurantAccepted_wrongStatus_doesNotRequestDelivery() {
        order.setStatus(OrderStatus.RESTAURANT_ACCEPTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        var event = new RestaurantAcceptedV1(UUID.randomUUID(), Instant.now(), orderId, order.getRestaurantId(), 20);
        orderService.processRestaurantAccepted(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESTAURANT_ACCEPTED);
        verify(outboxService, never()).publish(anyString(), anyString(), any());
    }

    @Test
    void processRestaurantRejected_transitionsToRestaurantRejected() {
        order.setStatus(OrderStatus.PAYMENT_AUTHORIZED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var event = new RestaurantRejectedV1(UUID.randomUUID(), Instant.now(), orderId, order.getRestaurantId(), "Out of stock");
        orderService.processRestaurantRejected(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESTAURANT_REJECTED);
    }

    @Test
    void processOrderReady_transitionsToReadyForPickup() {
        order.setStatus(OrderStatus.RESTAURANT_ACCEPTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var event = new OrderReadyForPickupV1(UUID.randomUUID(), Instant.now(), orderId, order.getRestaurantId());
        orderService.processOrderReady(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
    }

    @Test
    void processOrderPickedUp_transitionsToOutForDelivery() {
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var event = new OrderPickedUpV1(UUID.randomUUID(), Instant.now(), orderId, UUID.randomUUID());
        orderService.processOrderPickedUp(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void processOrderDelivered_transitionsToDelivered() {
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var event = new OrderDeliveredV1(UUID.randomUUID(), Instant.now(), orderId, UUID.randomUUID());
        orderService.processOrderDelivered(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void cancelOrder_pendingStatus_immediatelyCancelledWithRefund() {
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getOrderCancelled()).thenReturn("fd.order.cancelled.v1");
        when(topics.getRefundRequested()).thenReturn("fd.payment.refund-requested.v1");

        orderService.cancelOrder(orderId, order.getCustomerId(), "Changed my mind");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxService, times(2)).publish(anyString(), anyString(), any()); // cancelled + refund
    }

    @Test
    void cancelOrder_paymentAuthorizedStatus_immediatelyCancelledWithRefund() {
        order.setStatus(OrderStatus.PAYMENT_AUTHORIZED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getOrderCancelled()).thenReturn("fd.order.cancelled.v1");
        when(topics.getRefundRequested()).thenReturn("fd.payment.refund-requested.v1");

        orderService.cancelOrder(orderId, order.getCustomerId(), "Changed my mind");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_restaurantAcceptedStatus_lateCancellationWithFee() {
        order.setStatus(OrderStatus.RESTAURANT_ACCEPTED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getFeeRequested()).thenReturn("fd.payment.fee-requested.v1");

        orderService.cancelOrder(orderId, order.getCustomerId(), "Changed my mind");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLING);
        verify(outboxService, times(1)).publish(anyString(), anyString(), any()); // only fee event
    }

    @Test
    void cancelOrder_readyForPickupStatus_lateCancellationWithDeliveryFee() {
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getFeeRequested()).thenReturn("fd.payment.fee-requested.v1");

        orderService.cancelOrder(orderId, order.getCustomerId(), "Changed my mind");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLING);
    }

    @Test
    void cancelOrder_deliveredStatus_throws() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId, order.getCustomerId(), "Too late"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelOrder_outForDelivery_requestsDeliveryFee() {
        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getFeeRequested()).thenReturn("fd.payment.fee-requested.v1");

        orderService.cancelOrder(orderId, order.getCustomerId(), "Too late");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLING);
        verify(outboxService).publish(anyString(), eq(orderId.toString()), any());
    }

    @Test
    void cancelOrder_wrongCustomer_throws() {
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        var wrongCustomerId = UUID.randomUUID();
        assertThatThrownBy(() -> orderService.cancelOrder(orderId, wrongCustomerId, "Cancel"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void processFeeCharged_transitionsToCancelled() {
        order.setStatus(OrderStatus.CANCELLING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getOrderCancelled()).thenReturn("fd.order.cancelled.v1");

        var event = new FeeChargedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("5.00"), "Cancellation fee");
        orderService.processFeeCharged(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void processFeeFailed_marksCancellationPaymentFailedAndPublishesCancellation() {
        order.setStatus(OrderStatus.CANCELLING);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(topics.getOrderCancelled()).thenReturn("fd.order.cancelled.v1");

        var event = new FeeFailedV1(UUID.randomUUID(), Instant.now(), orderId, new BigDecimal("5.00"),
                "Cancellation fee", "Payment unavailable");
        orderService.processFeeFailed(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLATION_PAYMENT_FAILED);
        verify(outboxService).publish(anyString(), eq(orderId.toString()), any());
    }

    @Test
    void getOrder_wrongCustomer_throws() {
        order.setCustomerId(UUID.randomUUID());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(orderId, UUID.randomUUID()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void findOrder_notFound_throws() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    void getAllOrders_returnsOrderResponseDtos() {
        var pageable = Pageable.ofSize(100);
        when(orderRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        var result = orderService.getAllOrders(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).orderId()).isEqualTo(order.getId());
        assertThat(result.getContent().get(0).total()).isEqualTo(order.getTotal());
        assertThat(result.getContent().get(0).status()).isEqualTo(order.getStatus());
    }
}
