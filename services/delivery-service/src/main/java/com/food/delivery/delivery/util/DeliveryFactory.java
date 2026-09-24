package com.food.delivery.delivery.util;

import com.food.delivery.delivery.model.CourierAssignmentEntity;
import com.food.delivery.delivery.model.DeliveryEntity;
import com.food.delivery.delivery.model.DeliveryStatus;
import fd.delivery.CourierAssignedV1;
import fd.delivery.DeliveryRequestedV1;
import fd.delivery.OrderDeliveredV1;
import fd.delivery.OrderPickedUpV1;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;

@UtilityClass
public class DeliveryFactory {

    public DeliveryEntity createDelivery(DeliveryRequestedV1 request) {
        var delivery = new DeliveryEntity();
        delivery.setOrderId(request.getOrderId());
        delivery.setRestaurantId(request.getRestaurantId());
        delivery.setCustomerId(request.getCustomerId());
        delivery.setStatus(DeliveryStatus.PENDING_ASSIGNMENT);
        return delivery;
    }

    public CourierAssignedV1 createCourierAssigned(DeliveryEntity delivery, CourierAssignmentEntity assignment) {
        return new CourierAssignedV1(UUID.randomUUID(), Instant.now(), assignment.getId(), delivery.getOrderId(), assignment.getCourierId());
    }

    public OrderPickedUpV1 createOrderPickedUp(DeliveryEntity delivery, CourierAssignmentEntity assignment) {
        return new OrderPickedUpV1(UUID.randomUUID(), Instant.now(), delivery.getOrderId(), assignment.getCourierId());
    }

    public OrderDeliveredV1 createOrderDelivered(DeliveryEntity delivery, CourierAssignmentEntity assignment) {
        return new OrderDeliveredV1(UUID.randomUUID(), Instant.now(), delivery.getOrderId(), assignment.getCourierId());
    }
}
