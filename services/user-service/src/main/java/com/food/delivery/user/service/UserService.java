package com.food.delivery.user.service;

import com.food.delivery.user.exception.UserProfileNotFoundException;
import com.food.delivery.user.model.UserProfileEntity;
import com.food.delivery.user.repository.UserProfileRepository;
import fd.user.UserRegisteredV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserProfileRepository userProfiles;

    public void createUserProfile(UserRegisteredV1 event) {
        log.info("Creating user profile for userId={} username={}", event.getUserId(), event.getUsername());

        var existing = userProfiles.findById(event.getUserId());
        if (existing.isPresent()) {
            log.debug("User profile already exists userId={}", event.getUserId());
            return; // Idempotent - skip duplicate
        }

        var profile = new UserProfileEntity();
        profile.setUserId(event.getUserId());
        profile.setUsername(event.getUsername());
        profile.setEmail(event.getEmail());
        userProfiles.save(profile);
    }

    public UserProfileEntity getUserProfile(UUID userId) {
        return userProfiles.findById(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(userId));
    }
}
