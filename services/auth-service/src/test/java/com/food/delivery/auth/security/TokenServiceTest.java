package com.food.delivery.auth.security;

import com.food.delivery.auth.config.JwtProperties;
import com.food.delivery.auth.model.RoleEntity;
import com.food.delivery.auth.model.UserEntity;
import com.food.delivery.common.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    JwtEncoder jwtEncoder;

    TokenService tokenService;

    @BeforeEach
    void setUp() {
        var props = new JwtProperties("food-delivery", "food-delivery-client", 3600L);
        tokenService = new TokenService(jwtEncoder, props);
    }

    @Test
    void issueSetsCorrectClaims() {
        var role = new RoleEntity();
        role.setName(Role.CUSTOMER);

        var user = new UserEntity();
        var userId = UUID.randomUUID();
        user.setId(userId);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setRoles(Set.of(role));

        var fakeJwt = Jwt.withTokenValue("signed-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(Map.of(
                        "username", "alice",
                        "email", "alice@example.com",
                        "roles", List.of("CUSTOMER")
                )))
                .build();

        when(jwtEncoder.encode(any())).thenReturn(fakeJwt);

        var issued = tokenService.issue(user);

        assertThat(issued.token()).isEqualTo("signed-token");
        assertThat(issued.expiresAtEpochSeconds()).isGreaterThan(Instant.now().getEpochSecond());

        var captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(captor.capture());
        var claims = captor.getValue().getClaims();
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat((String) claims.getClaim("username")).isEqualTo("alice");
        assertThat((String) claims.getClaim("email")).isEqualTo("alice@example.com");
        assertThat(claims.<List<String>>getClaim("roles")).contains("CUSTOMER");

    }
}
