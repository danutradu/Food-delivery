package com.food.delivery.common.dlt;

import fd.order.OrderCreatedV1;
import fd.order.OrderItem;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DltEventPayloadDecoderTest {

    private final DltEventPayloadDecoder decoder = new DltEventPayloadDecoder();

    @Test
    void decodeAndConvertToReadablePayload_returnsAvroFieldsAndNestedValues() {
        var eventId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var restaurantId = UUID.randomUUID();
        var menuItemId = UUID.randomUUID();
        var occurredAt = Instant.parse("2026-09-14T09:00:00Z");
        var event = new OrderCreatedV1(eventId, occurredAt, orderId, customerId, restaurantId,
                new BigDecimal("12.00"), List.of(new OrderItem(menuItemId, "Pizza", new BigDecimal("12.00"), 1)));

        var entity = new DltEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventType(OrderCreatedV1.class.getName());
        entity.setPayloadBytes(encode(event));

        var decoded = decoder.decode(entity);
        var payload = (Map<?, ?>) decoder.toReadablePayload(decoded);

        assertThat(payload.get("eventId")).isEqualTo(eventId);
        assertThat(payload.get("occurredAt")).isEqualTo(occurredAt);
        assertThat(payload.get("orderId")).isEqualTo(orderId);
        assertThat(payload.get("customerId")).isEqualTo(customerId);
        assertThat(payload.get("restaurantId")).isEqualTo(restaurantId);
        assertThat(payload.get("total")).isEqualTo(new BigDecimal("12.00"));
        assertThat(payload.get("items")).isInstanceOf(List.class);
        assertThat(((List<?>) payload.get("items")).getFirst())
                .isEqualTo(Map.of("menuItemId", menuItemId, "name", "Pizza", "unitPrice", new BigDecimal("12.00"), "quantity", 1));
    }

    @Test
    void toReadablePayload_encodesBinaryValuesAsBase64() {
        var readable = decoder.toReadablePayload(ByteBuffer.wrap(new byte[]{1, 2, 3}));

        assertThat(readable).isEqualTo("AQID");
    }

    @Test
    void decode_invalidPayload_throwsPayloadException() {
        var entity = new DltEventEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventType(OrderCreatedV1.class.getName());
        entity.setPayloadBytes(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> decoder.decode(entity))
                .isInstanceOf(DltEventPayloadDecoder.DltEventPayloadException.class);
    }

    private byte[] encode(OrderCreatedV1 event) {
        try {
            var output = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(output, null);
            new SpecificDatumWriter<OrderCreatedV1>(event.getSchema()).write(event, encoder);
            encoder.flush();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError("Could not encode test event", exception);
        }
    }
}
