package com.food.delivery.common.dlt;

import com.food.delivery.common.dlt.dto.DltEventPageResponse;
import com.food.delivery.common.dlt.dto.DltEventResponse;
import com.food.delivery.common.dlt.dto.DltReplayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/dlt-events")
@RequiredArgsConstructor
public class DltReplayController {

    private final DltReplayService replayService;
    private final DltEventQueryService queryService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DltEventPageResponse list(
            @RequestParam(required = false) DltEventStatus status,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String sourceTopic,
            Pageable pageable) {
        return DltEventPageResponse.from(queryService.list(status, serviceName, sourceTopic, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DltEventResponse> get(@PathVariable UUID id) {
        return queryService.findResponseById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DltReplayResponse> replay(@PathVariable UUID id) {
        var event = replayService.replay(id);
        return ResponseEntity.ok(new DltReplayResponse(event.getId(), event.getStatus(), event.getSourceTopic()));
    }
}
