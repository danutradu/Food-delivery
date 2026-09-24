package com.food.delivery.common.dlt.dto;

import com.food.delivery.common.dlt.DltEventStatus;

import java.util.UUID;

public record DltReplayResponse(UUID id, DltEventStatus status, String sourceTopic) {}
