package com.food.delivery.auth.service;

import com.food.delivery.auth.config.KafkaTopics;
import com.food.delivery.auth.dto.IssuedToken;
import com.food.delivery.auth.dto.LoginRequest;
import com.food.delivery.auth.dto.RegisterRequest;
import com.food.delivery.auth.exception.AccountDisabledException;
import com.food.delivery.auth.exception.AuthenticationException;
import com.food.delivery.auth.exception.UserAlreadyExistsException;
import com.food.delivery.auth.model.RoleEntity;
import com.food.delivery.auth.model.UserEntity;
import com.food.delivery.auth.repository.RoleRepository;
import com.food.delivery.auth.repository.UserRepository;
import com.food.delivery.auth.security.TokenService;
import com.food.delivery.common.model.Role;
import com.food.delivery.common.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    RoleRepository roleRepository;

    @Mock
    TokenService tokenService;

    @Mock
    PasswordEncoder encoder;

    @Mock
    OutboxService outboxService;

    @Mock
    KafkaTopics topics;

    @InjectMocks
    AuthService authService;

    private RoleEntity customerRole;

    @BeforeEach
    void setUp() {
        customerRole = new RoleEntity();
        customerRole.setId(1L);
        customerRole.setName(Role.CUSTOMER);
    }

    @Test
    void registerSuccess() {
        var req = new RegisterRequest("alice", "alice@example.com", "pass");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(Role.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(encoder.encode("pass")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tokenService.issue(any())).thenReturn(new IssuedToken("jwt-token", 9999L));
        when(topics.getUserRegistered()).thenReturn("fd.user.registered.v1");

        var response = authService.register(req);

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(outboxService).publish(anyString(), anyString(), any());
    }

    @Test
    void registerDuplicateUsernameThrows() {
        var req = new RegisterRequest("alice", "alice@example.com", "pass");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(new UserEntity()));

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("alice");
    }

    @Test
    void registerDuplicateEmailThrows() {
        var req = new RegisterRequest("alice", "alice@example.com", "pass");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(new UserEntity()));

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("alice@example.com");
    }

    @Test
    void register_missingCustomerRole_failsBeforeCreatingUser() {
        var req = new RegisterRequest("alice", "alice@example.com", "pass");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(Role.CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CUSTOMER role");

        verify(userRepository, never()).save(any());
        verify(outboxService, never()).publish(any(), any(), any());
    }

    @Test
    void loginSuccess() {
        var user = buildUser("alice", true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(encoder.matches("pass", "hashed")).thenReturn(true);
        when(tokenService.issue(user)).thenReturn(new IssuedToken("jwt-token", 9999L));

        var response = authService.login(new LoginRequest("alice", "pass"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
    }

    @Test
    void loginUserNotFoundThrows() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "pass")))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void loginWrongPasswordThrows() {
        var user = buildUser("alice", true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(AuthenticationException.class)
                .hasMessageContaining("alice");
    }

    @Test
    void loginDisabledAccountThrows() {
        var user = buildUser("alice", false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "pass")))
                .isInstanceOf(AccountDisabledException.class)
                .hasMessageContaining("alice");
    }

    private UserEntity buildUser(String username, boolean enabled) {
        var user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("hashed");
        user.setEnabled(enabled);
        return user;
    }
}
