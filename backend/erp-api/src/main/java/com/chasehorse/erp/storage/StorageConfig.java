package com.chasehorse.erp.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.chasehorse.erp.config.AppProperties;

@Configuration
public class StorageConfig {
    @Bean
    StorageService storageService(AppProperties props, RestClient.Builder builder) {
        return props.supabase().hasServiceKey()
                ? new SupabaseStorageService(props, builder)
                : new UnconfiguredStorageService();
    }
}
