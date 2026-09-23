package com.food.delivery.cart.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.food.delivery.cart.dto.MenuItemDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupLoader implements ApplicationRunner {

    @Value("${catalog.service.url}")
    private String catalogServiceUrl;

    private final MenuItemCacheService menuItemCacheService;
    private final TaskScheduler taskScheduler;
    private WebClient webClient;

    private volatile boolean cacheInitialized = false;
    private ScheduledFuture<?> retryTask;

    private static final int PAGE_SIZE = 1000;

    private static final ParameterizedTypeReference<PageResponse<RestaurantDto>> RESTAURANT_PAGE =
            new ParameterizedTypeReference<>() {
            };

    private static final ParameterizedTypeReference<PageResponse<MenuItemDto>> MENU_ITEM_PAGE =
            new ParameterizedTypeReference<>() {
            };

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder().baseUrl(catalogServiceUrl).build();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        initializeCache()
                .doOnError(error -> {
                    log.warn("Initial cache load failed, starting retry scheduler: {}", error.getMessage());
                    startRetryScheduler();
                })
                .doOnSuccess(result -> {
                    cacheInitialized = true;
                    log.info("Menu item cache initialization completed");
                })
                .subscribe();
    }

    private void startRetryScheduler() {
        if (retryTask == null) {
            retryTask = taskScheduler.scheduleWithFixedDelay(
                    this::retryInitializeCache,
                    Duration.ofSeconds(30)
            );
        }
    }

    private Mono<Void> initializeCache() {
        log.info("Initializing menu item cache from catalog-service...");

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/restaurants").queryParam("size", PAGE_SIZE).build())
                .retrieve()
                .bodyToMono(RESTAURANT_PAGE)
                .timeout(Duration.ofSeconds(5))
                .flatMapIterable(page -> page.content() != null ? page.content() : List.of())
                .flatMap(restaurant ->
                        webClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/restaurants/{restaurantId}/menu")
                                        .queryParam("size", PAGE_SIZE)
                                        .build(restaurant.id()))
                                .retrieve()
                                .bodyToMono(MENU_ITEM_PAGE)
                                .timeout(Duration.ofSeconds(5))
                                .doOnNext(page -> {
                                    if (page.content() == null) {
                                        return;
                                    }
                                    for (var item : page.content()) {
                                        menuItemCacheService.updateMenuItem(
                                                restaurant.id(),
                                                item.id(),
                                                item.name(),
                                                item.price(),
                                                item.available()
                                        );
                                    }
                                })
                )
                .then();
    }

    private void retryInitializeCache() {
        if (!cacheInitialized) {
            log.info("Cache not initialized, retrying...");
            initializeCache()
                    .doOnSuccess(result -> {
                        cacheInitialized = true;
                        retryTask.cancel(false);
                        log.info("Menu item cache initialization completed, stopped retry scheduler");
                    })
                    .doOnError(error -> log.warn("Retry failed: {}", error.getMessage()))
                    .subscribe();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageResponse<T>(List<T> content) {}

    public record RestaurantDto(UUID id, String name) {}

}
