package com.food.delivery.common.outbox;

import lombok.RequiredArgsConstructor;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;

    public void publish(String topic, String key, SpecificRecord event) {
        stageOutbox(topic, event.getClass().getName(), key, toAvroJson(event));
    }

    private void stageOutbox(String topic, String eventType, String key, String payloadJson) {
        var outboxEvent = new OutboxEventEntity();
        outboxEvent.setId(UUID.randomUUID());
        outboxEvent.setTopic(topic);
        outboxEvent.setEventType(eventType);
        outboxEvent.setKey(key);
        outboxEvent.setPayloadJson(payloadJson);

        outboxRepository.save(outboxEvent);
    }

    private String toAvroJson(SpecificRecord avroObject) {
        try {
            var writer = new SpecificDatumWriter<SpecificRecord>(avroObject.getSchema());
            var out = new ByteArrayOutputStream();
            var encoder = EncoderFactory.get().jsonEncoder(avroObject.getSchema(), out);
            writer.write(avroObject, encoder);
            encoder.flush();
            return out.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
