package com.inmobiliaria.operation_service.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager("dashboardCache");
    cacheManager.setCaffeine(
        Caffeine.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES) // Caché corta exigida en la tarea
            .maximumSize(100));
    return cacheManager;
  }
}
