package com.food.delivery.common.dlt.dto;

import com.food.delivery.common.dlt.DltEventEntity;
import com.food.delivery.common.dlt.DltEventStatus;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public record DltEventResponse(
        UUID id,
        String serviceName,
        String sourceTopic,
        String dltTopic,
        String originalKey,
        Integer originalPartition,
        Long originalOffset,
        String eventType,
        String payloadEncoding,
        String schemaFullName,
        DltEventStatus status,
        Instant parkedAt,
        Object payload,
        String payloadBase64,
        String decodeError) {

    public static DltEventResponse from(DltEventEntity event) {
        return new DltEventResponse(event.getId(), event.getServiceName(), event.getSourceTopic(),
                event.getDltTopic(), event.getOriginalKey(), event.getOriginalPartition(),
                event.getOriginalOffset(), event.getEventType(), event.getPayloadEncoding(),
                event.getSchemaFullName(), event.getStatus(), event.getParkedAt(), null, null, null);
    }

    public static DltEventResponse withPayload(DltEventEntity event, Object payload) {
        return new DltEventResponse(event.getId(), event.getServiceName(), event.getSourceTopic(),
                event.getDltTopic(), event.getOriginalKey(), event.getOriginalPartition(),
                event.getOriginalOffset(), event.getEventType(), event.getPayloadEncoding(),
                event.getSchemaFullName(), event.getStatus(), event.getParkedAt(), payload,
                Base64.getEncoder().encodeToString(event.getPayloadBytes()), null);
    }

    public static DltEventResponse withPayloadDecodeFailure(DltEventEntity event, String decodeError) {
        return new DltEventResponse(event.getId(), event.getServiceName(), event.getSourceTopic(),
                event.getDltTopic(), event.getOriginalKey(), event.getOriginalPartition(),
                event.getOriginalOffset(), event.getEventType(), event.getPayloadEncoding(),
                event.getSchemaFullName(), event.getStatus(), event.getParkedAt(), null,
                Base64.getEncoder().encodeToString(event.getPayloadBytes()), decodeError);
    }
}
