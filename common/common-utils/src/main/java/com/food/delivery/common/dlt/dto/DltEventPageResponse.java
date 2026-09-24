package com.food.delivery.common.dlt.dto;

import com.food.delivery.common.dlt.DltEventEntity;
import org.springframework.data.domain.Page;

import java.util.List;

public record DltEventPageResponse(
        List<DltEventResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static DltEventPageResponse from(Page<DltEventEntity> events) {
        return new DltEventPageResponse(
                events.map(DltEventResponse::from).getContent(),
                events.getNumber(),
                events.getSize(),
                events.getTotalElements(),
                events.getTotalPages(),
                events.isFirst(),
                events.isLast());
    }
}
