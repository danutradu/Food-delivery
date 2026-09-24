package com.food.delivery.common.dlt;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

@Service
public class DltEventPayloadDecoder {

    public SpecificRecord decode(DltEventEntity event) {
        try {
            var eventType = Class.forName(event.getEventType());
            var specificRecord = (SpecificRecord) eventType.getDeclaredConstructor().newInstance();
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(event.getPayloadBytes(), null);
            return new SpecificDatumReader<SpecificRecord>(specificRecord.getSchema())
                    .read(null, decoder);
        } catch (ReflectiveOperationException | IOException | RuntimeException exception) {
            throw new DltEventPayloadException(event.getId(), exception);
        }
    }

    public Object toReadablePayload(Object value) {
        if (value instanceof SpecificRecord record) {
            var fields = new LinkedHashMap<String, Object>();
            record.getSchema().getFields().forEach(field ->
                    fields.put(field.name(), toReadablePayload(record.get(field.pos()))));
            return fields;
        }
        if (value instanceof ByteBuffer buffer) {
            var bytes = new byte[buffer.remaining()];
            buffer.duplicate().get(bytes);
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof CharSequence sequence) {
            return sequence.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Map<?, ?> map) {
            var readableMap = new LinkedHashMap<String, Object>();
            map.forEach((key, mapValue) ->
                    readableMap.put(String.valueOf(key), toReadablePayload(mapValue)));
            return readableMap;
        }
        if (value instanceof Iterable<?> iterable) {
            var readableValues = new ArrayList<>();
            iterable.forEach(item -> readableValues.add(toReadablePayload(item)));
            return readableValues;
        }
        return value;
    }

    public static class DltEventPayloadException extends RuntimeException {
        public DltEventPayloadException(UUID id, Throwable cause) {
            super("Could not decode DLT event payload: " + id, cause);
        }
    }
}
