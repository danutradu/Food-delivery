package com.food.delivery.common.dlt;

import com.food.delivery.common.dlt.dto.DltEventResponse;
import com.food.delivery.common.dlt.dto.DltReplayResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DltReplayControllerTest {

    @Mock
    DltReplayService replayService;
    @Mock
    DltEventQueryService queryService;
    @InjectMocks
    DltReplayController controller;

    @Test
    void list_delegatesFiltersAndMapsResponse() {
        var event = event();
        var pageable = PageRequest.of(0, 10);
        when(queryService.list(DltEventStatus.PARKED, "order-service", "order.events", pageable))
                .thenReturn(new PageImpl<>(List.of(event), pageable, 1));

        var response = controller.list(DltEventStatus.PARKED, "order-service", "order.events", pageable);

        assertThat(response.content()).singleElement()
                .extracting(DltEventResponse::id)
                .isEqualTo(event.getId());
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(1);
        verify(queryService).list(DltEventStatus.PARKED, "order-service", "order.events", pageable);
    }

    @Test
    void get_existingEvent_returnsResponse() {
        var event = event();
        var readablePayload = Map.of("orderId", "order-123");
        var response = DltEventResponse.withPayload(event, readablePayload);
        when(queryService.findResponseById(event.getId())).thenReturn(Optional.of(response));

        var result = controller.get(event.getId());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void get_missingEvent_returnsNotFound() {
        var id = UUID.randomUUID();
        when(queryService.findResponseById(id)).thenReturn(Optional.empty());

        var result = controller.get(id);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void replay_delegatesToReplayService() {
        var event = event();
        when(replayService.replay(event.getId())).thenReturn(event);

        var result = controller.replay(event.getId());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(
                new DltReplayResponse(event.getId(), DltEventStatus.PARKED, event.getSourceTopic()));
        verify(replayService).replay(event.getId());
    }

    @Test
    void endpoints_requireAdminRole() throws NoSuchMethodException {
        var annotation = DltReplayController.class
                .getMethod("list", DltEventStatus.class, String.class, String.class,
                        Pageable.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
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
