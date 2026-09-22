package com.food.delivery.user.service;

import com.food.delivery.user.exception.UserProfileNotFoundException;
import com.food.delivery.user.model.UserProfileEntity;
import com.food.delivery.user.repository.UserProfileRepository;
import fd.user.UserRegisteredV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserProfileRepository userProfiles;

    @InjectMocks
    UserService userService;

    @Test
    void createUserProfile_savesProfile() {
        var userId = UUID.randomUUID();
        var event = new UserRegisteredV1(UUID.randomUUID(), Instant.now(), userId, "alice", "alice@example.com", List.of("CUSTOMER"));
        when(userProfiles.findById(userId)).thenReturn(Optional.empty());

        userService.createUserProfile(event);

        var captor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfiles).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void createUserProfile_idempotent_skipsIfExists() {
        var userId = UUID.randomUUID();
        var event = new UserRegisteredV1(UUID.randomUUID(), Instant.now(), userId, "alice", "alice@example.com", List.of("CUSTOMER"));
        var existing = new UserProfileEntity();
        existing.setUserId(userId);
        when(userProfiles.findById(userId)).thenReturn(Optional.of(existing));

        userService.createUserProfile(event);

        verify(userProfiles, never()).save(any());
    }

    @Test
    void getUserProfile_notFound_throws() {
        var userId = UUID.randomUUID();
        when(userProfiles.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile(userId))
                .isInstanceOf(UserProfileNotFoundException.class)
                .hasMessageContaining(userId.toString());
    }

    @Test
    void getUserProfile_existingProfile_returnsIt() {
        var userId = UUID.randomUUID();
        var existing = new UserProfileEntity();
        existing.setUserId(userId);
        existing.setUsername("alice");
        when(userProfiles.findById(userId)).thenReturn(Optional.of(existing));

        assertThat(userService.getUserProfile(userId)).isSameAs(existing);
    }
}
