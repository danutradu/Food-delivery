package com.food.delivery.common.dlt;

import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DltEventQueryServiceTest {

    private final DltEventRepository repository = mock(DltEventRepository.class);
    private final DltEventPayloadDecoder payloadDecoder = mock(DltEventPayloadDecoder.class);
    private final DltEventQueryService service = new DltEventQueryService(repository, payloadDecoder);

    @Test
    void list_delegatesFiltersAndPageableToRepository() {
        var pageable = PageRequest.of(1, 20);
        var page = new PageImpl<>(List.of(event()));
        when(repository.findAllByFilters(DltEventStatus.PARKED, "order-service", "order.events", pageable))
                .thenReturn(page);

        assertThat(service.list(DltEventStatus.PARKED, "order-service", "order.events", pageable))
                .isSameAs(page);
        verify(repository).findAllByFilters(DltEventStatus.PARKED, "order-service", "order.events", pageable);
    }

    @Test
    void findResponseById_decodesAndIncludesReadablePayload() {
        var event = event();
        var record = mock(SpecificRecord.class);
        var readablePayload = Map.of("orderId", "order-123", "total", new BigDecimal("12.00"));
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(payloadDecoder.decode(event)).thenReturn(record);
        when(payloadDecoder.toReadablePayload(record)).thenReturn(readablePayload);

        var response = service.findResponseById(event.getId());

        assertThat(response).isPresent();
        assertThat(response.orElseThrow().payload()).isEqualTo(readablePayload);
        assertThat(response.orElseThrow().payloadBase64()).isEqualTo("AQI=");
        verify(payloadDecoder).decode(event);
        verify(payloadDecoder).toReadablePayload(record);
    }

    @Test
    void findResponseById_missingEvent_returnsEmpty() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.findResponseById(id)).isEmpty();
    }

    @Test
    void findResponseById_decodeFailure_returnsRawPayloadAndError() {
        var event = event();
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(payloadDecoder.decode(event)).thenThrow(
                new DltEventPayloadDecoder.DltEventPayloadException(event.getId(), new IllegalArgumentException()));

        var response = service.findResponseById(event.getId()).orElseThrow();

        assertThat(response.payload()).isNull();
        assertThat(response.payloadBase64()).isEqualTo("AQI=");
        assertThat(response.decodeError()).isEqualTo("Unable to decode stored Avro payload");
    }

    private DltEventEntity event() {
        var event = new DltEventEntity();
        event.setId(UUID.randomUUID());
        event.setServiceName("order-service");
        event.setSourceTopic("order.events");
        event.setDltTopic("order.events.DLT");
        event.setEventType("fd.order.OrderCreatedV1");
        event.setPayloadBytes(new byte[]{1, 2});
        event.setPayloadEncoding("AVRO_BINARY");
        event.setSchemaFullName("fd.order.OrderCreatedV1");
        event.setStatus(DltEventStatus.PARKED);
        return event;
    }
}
