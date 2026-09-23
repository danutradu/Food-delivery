package com.food.delivery.ops.util;

import com.food.delivery.ops.model.KitchenTicketEntity;
import fd.restaurant.OrderReadyForPickupV1;
import fd.restaurant.RestaurantAcceptedV1;
import fd.restaurant.RestaurantRejectedV1;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class OpsEventFactory {

    public RestaurantAcceptedV1 createRestaurantAccepted(KitchenTicketEntity ticket, int etaMinutes) {
        return new RestaurantAcceptedV1(
                UUID.randomUUID(),
                Instant.now(),
                ticket.getOrderId(),
                ticket.getRestaurantId(),
                etaMinutes
        );
    }

    public RestaurantRejectedV1 createRestaurantRejected(KitchenTicketEntity ticket, String reason) {
        return new RestaurantRejectedV1(
                UUID.randomUUID(),
                Instant.now(),
                ticket.getOrderId(),
                ticket.getRestaurantId(),
                reason
        );
    }

    public OrderReadyForPickupV1 createOrderReady(KitchenTicketEntity ticket) {
        return new OrderReadyForPickupV1(
                UUID.randomUUID(),
                Instant.now(),
                ticket.getOrderId(),
                ticket.getRestaurantId()
        );
    }
}
