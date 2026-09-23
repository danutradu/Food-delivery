package com.food.delivery.delivery.util;

import com.food.delivery.delivery.model.DeliveryEntity;
import com.food.delivery.delivery.model.DeliveryStatus;
import fd.delivery.DeliveryRequestedV1;
import lombok.experimental.UtilityClass;

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
}
