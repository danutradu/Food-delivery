package com.food.delivery.common.dlt;

import com.food.delivery.common.dlt.dto.DltEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DltEventQueryService {

    private final DltEventRepository repository;
    private final DltEventPayloadDecoder payloadDecoder;

    @Transactional(readOnly = true)
    public Page<DltEventEntity> list(DltEventStatus status, String serviceName,
                                     String sourceTopic, Pageable pageable) {
        return repository.findAllByFilters(status, serviceName, sourceTopic, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<DltEventResponse> findResponseById(UUID id) {
        return repository.findById(id)
                .map(event -> {
                    try {
                        return DltEventResponse.withPayload(event,
                                payloadDecoder.toReadablePayload(payloadDecoder.decode(event)));
                    } catch (DltEventPayloadDecoder.DltEventPayloadException exception) {
                        return DltEventResponse.withPayloadDecodeFailure(
                                event, "Unable to decode stored Avro payload");
                    }
                });
    }
}
