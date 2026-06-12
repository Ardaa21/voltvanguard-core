package com.voltvanguard.core.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis configuration with per-cache TTL settings.
 *
 * <p>All values are serialized as JSON (not Java binary) so they remain
 * human-readable in Redis and survive class renames without cache poisoning.</p>
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${voltvanguard.cache.vehicle-ttl-seconds:300}")
    private long vehicleTtlSeconds;

    @Value("${voltvanguard.cache.station-ttl-seconds:120}")
    private long stationTtlSeconds;

    @Value("${voltvanguard.cache.task-ttl-seconds:60}")
    private long taskTtlSeconds;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper()));
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(redisObjectMapper()));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer(redisObjectMapper())
                )
            )
            .disableCachingNullValues()
            .entryTtl(Duration.ofMinutes(5));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("vehicles", defaultConfig.entryTtl(Duration.ofSeconds(vehicleTtlSeconds)));
        cacheConfigs.put("stations", defaultConfig.entryTtl(Duration.ofSeconds(stationTtlSeconds)));
        cacheConfigs.put("tasks",    defaultConfig.entryTtl(Duration.ofSeconds(taskTtlSeconds)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }

    /**
     * ObjectMapper configured for Redis serialization.
     * Activates polymorphic type handling so deserialization works
     * without knowing the concrete type at compile time.
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
            LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }
}
