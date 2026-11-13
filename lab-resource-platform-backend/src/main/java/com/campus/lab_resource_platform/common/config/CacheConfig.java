package com.campus.lab_resource_platform.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(List.of(
                "utilization-summary",
                "utilization-heatmap",
                "utilization-benchmarks",
                "sharing-discovery",
                "sharing-analytics"
        ));
        return cacheManager;
    }
}

