package com.food.delivery.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher implements SmartLifecycle {

    private final OutboxClaimService outboxClaimService;
    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final OutboxEventUpdater outboxEventUpdater;

    @Value("${outbox.max-retries:5}")
    private int maxRetries;

    @Value("${outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${outbox.publish-delay-ms:2000}")
    public void publishBatch() {
        try {
            var batch = outboxClaimService.claimBatch(batchSize);
            if (batch.isEmpty()) {
                return;
            }

            log.debug("Publishing {} outbox events", batch.size());
            batch.forEach(this::processEvent);
        } catch (Exception e) {
            log.error("Error in outbox publisher batch processing", e);
        }
    }

    private void processEvent(OutboxEventEntity event) {
        try {
            var avro = deserializeEvent(event);
            var producerRecord = new ProducerRecord<String, SpecificRecord>(event.getTopic(), event.getKey(), avro);
            producerRecord.headers().add("eventType", avro.getClass().getSimpleName().getBytes(StandardCharsets.UTF_8));
            producerRecord.headers().add("eventId", event.getId().toString().getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(producerRecord).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish outbox event id={}", event.getId(), ex);
                    outboxEventUpdater.markFailed(event.getId(), event.getClaimedAt(), ex, maxRetries);
                } else {
                    outboxEventUpdater.markPublished(event.getId(), event.getClaimedAt());
                }
            });
        } catch (Exception ex) {
            log.error("Failed to prepare outbox event id={}", event.getId(), ex);
            outboxEventUpdater.markFailed(event.getId(), event.getClaimedAt(), ex, maxRetries);
        }
    }

    @SuppressWarnings("unchecked")
    private SpecificRecord deserializeEvent(OutboxEventEntity event) throws Exception {
        var clazz = Class.forName(event.getEventType());
        if (!SpecificRecord.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("Event type " + event.getEventType() + " is not a SpecificRecord");
        }
        return deserialize((Class<? extends SpecificRecord>) clazz, event.getPayloadJson());
    }

    private <T extends SpecificRecord> T deserialize(Class<T> clazz, String payloadJson) throws Exception {
        var reader = new SpecificDatumReader<T>(clazz);
        var instance = clazz.getDeclaredConstructor().newInstance();
        var decoder = DecoderFactory.get().jsonDecoder(instance.getSchema(), payloadJson);
        return reader.read(null, decoder);
    }

    private volatile boolean running;

    @Override
    public void start() {
        this.running = true;
    }

    @Override
    public void stop() {
        try {
            kafkaTemplate.flush();
            log.info("Flushed Kafka producer during shutdown");
        } catch (Exception e) {
            log.warn("Failed to flush Kafka producer during shutdown", e);
        }
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
