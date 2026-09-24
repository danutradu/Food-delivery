package com.food.delivery.auth.controller;

import com.food.delivery.auth.dto.CourierApplicationRejectionRequest;
import com.food.delivery.auth.dto.CourierApplicationRequest;
import com.food.delivery.auth.dto.CourierApplicationResponse;
import com.food.delivery.auth.dto.CourierDisableRequest;
import com.food.delivery.auth.model.CourierApplicationStatus;
import com.food.delivery.auth.service.CourierAdministrationService;
import com.food.delivery.auth.service.CourierApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CourierControllerTest {

    private final CourierApplicationService applicationService = mock(CourierApplicationService.class);
    private final CourierAdministrationService administrationService = mock(CourierAdministrationService.class);
    private final CourierApplicationController applicationController = new CourierApplicationController(applicationService);
    private final CourierAdministrationController administrationController = new CourierAdministrationController(administrationService);

    @Test
    void submitReturnsCreated() {
        var userId = UUID.randomUUID();
        var response = response();
        when(applicationService.submit(eq(userId), any())).thenReturn(response);

        var result = applicationController.submit(
                new CourierApplicationRequest("Jane Doe", "+40123456789", "Bicycle", "Bucharest"),
                authentication(userId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void rejectPassesReasonAndPermanentFlagToService() {
        var applicationId = UUID.randomUUID();
        var reviewerId = UUID.randomUUID();
        var request = new CourierApplicationRejectionRequest("Incomplete vehicle information", false);
        var response = response();
        when(applicationService.reject(applicationId, reviewerId, request)).thenReturn(response);

        var result = applicationController.reject(applicationId, request, authentication(reviewerId));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(applicationService).reject(applicationId, reviewerId, request);
    }

    @Test
    void disablePassesReasonFromRequestBody() {
        var userId = UUID.randomUUID();

        var result = administrationController.disable(userId, new CourierDisableRequest("Policy violation"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(administrationService).disable(userId, "Policy violation");
    }

    @Test
    void applicationEndpointsRequireExpectedRoles() throws Exception {
        assertThat(applicationController.getClass().getMethod("submit",
                        CourierApplicationRequest.class, Authentication.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('CUSTOMER')");
        assertThat(applicationController.getClass().getMethod("approve", UUID.class, Authentication.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(applicationController.getClass().getMethod("reject", UUID.class,
                        CourierApplicationRejectionRequest.class, Authentication.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
        assertThat(administrationController.getClass().getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }

    private Authentication authentication(UUID userId) {
        var authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(userId.toString());
        return authentication;
    }

    private CourierApplicationResponse response() {
        return new CourierApplicationResponse(UUID.randomUUID(), UUID.randomUUID(), "Jane Doe",
                "+40123456789", "Bicycle", "Bucharest", CourierApplicationStatus.PENDING, Instant.now(), null, null);
    }
}
