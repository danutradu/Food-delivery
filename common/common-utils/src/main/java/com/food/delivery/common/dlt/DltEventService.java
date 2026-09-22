package com.food.delivery.common.dlt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DltEventService {

    private final DltEventRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID park(String serviceName, String dltTopic, String sourceTopic, String originalKey,
                     Integer originalPartition, Long originalOffset, Object event) {
        if (sourceTopic == null || sourceTopic.isBlank()) {
            throw new IllegalArgumentException("Original source topic is required for DLT replay");
        }

        var record = new DltEventEntity();
        record.setId(UUID.randomUUID());
        record.setServiceName(serviceName);
        record.setSourceTopic(sourceTopic);
        record.setDltTopic(dltTopic);
        record.setOriginalKey(originalKey);
        record.setOriginalPartition(originalPartition);
        record.setOriginalOffset(originalOffset);
        record.setEventType(event == null ? "null" : event.getClass().getName());
        if (!(event instanceof SpecificRecord specificRecord)) {
            throw new IllegalArgumentException("Only Avro SpecificRecord events can be parked for replay");
        }
        record.setPayloadBytes(encode(specificRecord));
        record.setPayloadEncoding("AVRO_BINARY");
        record.setSchemaFullName(specificRecord.getSchema().getFullName());
        record.setStatus(DltEventStatus.PARKED);
        record.setParkedAt(Instant.now());
        repository.save(record);

        log.error("Kafka event parked in DLT recovery store service={} topic={} eventType={} dltEventId={}",
                serviceName, record.getSourceTopic(), record.getEventType(), record.getId());
        return record.getId();
    }

    private byte[] encode(SpecificRecord event) {
        try (var output = new ByteArrayOutputStream()) {
            BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder(output, null);
            new SpecificDatumWriter<SpecificRecord>(event.getSchema()).write(event, encoder);
            encoder.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize Avro event for DLT recovery", exception);
        }
    }
}
