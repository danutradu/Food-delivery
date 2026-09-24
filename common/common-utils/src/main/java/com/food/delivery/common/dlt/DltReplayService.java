package com.food.delivery.common.dlt;

import lombok.RequiredArgsConstructor;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DltReplayService {

    private final DltEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DltEventPayloadDecoder payloadDecoder;

    @Transactional
    public DltEventEntity replay(UUID id) {
        var record = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new DltEventNotFoundException(id));
        if (record.getStatus() != DltEventStatus.PARKED) {
            throw new DltEventStateException(id, record.getStatus());
        }

        SpecificRecord event;
        try {
            event = payloadDecoder.decode(record);
        } catch (DltEventPayloadDecoder.DltEventPayloadException exception) {
            throw new DltReplayException(id, exception);
        }
        try {
            kafkaTemplate.send(record.getSourceTopic(), record.getOriginalKey(), event)
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new DltReplayException(id, exception);
        }

        record.setStatus(DltEventStatus.REPLAYED);
        return repository.save(record);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class DltEventNotFoundException extends RuntimeException {
        public DltEventNotFoundException(UUID id) {
            super("DLT event not found: " + id);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DltEventStateException extends RuntimeException {
        public DltEventStateException(UUID id, DltEventStatus status) {
            super("DLT event " + id + " is not replayable from status " + status);
        }
    }

    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public static class DltReplayException extends RuntimeException {
        public DltReplayException(UUID id, Throwable cause) {
            super("Could not replay DLT event: " + id, cause);
        }
    }
}
