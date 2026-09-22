package com.food.delivery.auth.service;

import com.food.delivery.auth.config.KafkaTopics;
import com.food.delivery.auth.dto.JwtResponse;
import com.food.delivery.auth.dto.LoginRequest;
import com.food.delivery.auth.dto.RegisterRequest;
import com.food.delivery.auth.exception.AccountDisabledException;
import com.food.delivery.auth.exception.AuthenticationException;
import com.food.delivery.auth.exception.UserAlreadyExistsException;
import com.food.delivery.auth.repository.RoleRepository;
import com.food.delivery.auth.repository.UserRepository;
import com.food.delivery.auth.security.TokenService;
import com.food.delivery.auth.util.UserFactory;
import com.food.delivery.common.model.Role;
import com.food.delivery.common.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;
    private final PasswordEncoder encoder;
    private final OutboxService outboxService;
    private final KafkaTopics topics;

    @Transactional
    public JwtResponse register(RegisterRequest req) {
        log.info("UserRegistered username = {} email = {}", req.username(), req.email());

        if (userRepository.findByUsername(req.username()).isPresent()) {
            throw new UserAlreadyExistsException(req.username());
        }
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new UserAlreadyExistsException(req.email());
        }

        var role = roleRepository.findByName(Role.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role not found in database"));

        var user = UserFactory.createUser(req, Set.of(role));
        user.setPasswordHash(encoder.encode(req.password()));
        userRepository.save(user);

        var event = UserFactory.createUserRegistered(user);
        outboxService.publish(topics.getUserRegistered(), user.getId().toString(), event);

        var issuedToken = tokenService.issue(user);
        log.info("User registered successfully userId={}", user.getId());
        return new JwtResponse(issuedToken.token(), issuedToken.expiresAtEpochSeconds(), "Bearer");
    }

    public JwtResponse login(LoginRequest req) {
        log.info("UserLogin username={}", req.username());

        var user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new AuthenticationException(req.username()));

        if (!user.isEnabled()) {
            throw new AccountDisabledException(user.getUsername());
        }

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new AuthenticationException(user.getUsername());
        }

        var issuedToken = tokenService.issue(user);
        return new JwtResponse(issuedToken.token(), issuedToken.expiresAtEpochSeconds(), "Bearer");
    }
}
