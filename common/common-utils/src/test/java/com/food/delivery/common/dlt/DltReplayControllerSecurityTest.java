package com.food.delivery.common.dlt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DltReplayController.class)
@ContextConfiguration(classes = {
        DltReplayController.class,
        DltReplayControllerSecurityTest.TestSecurityConfig.class
})
class DltReplayControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DltReplayService replayService;

    @MockitoBean
    private DltEventQueryService queryService;

    @Test
    void list_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/dlt-events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withNonAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/admin/dlt-events")
                        .with(user("operator").roles("OPERATOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_withAdmin_returnsOk() throws Exception {
        when(queryService.list(isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<DltEventEntity>(List.of()));

        mockMvc.perform(get("/admin/dlt-events")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Configuration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }
    }
}
