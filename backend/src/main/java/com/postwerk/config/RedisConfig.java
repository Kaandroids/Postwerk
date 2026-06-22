package com.postwerk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.Map;

/**
 * Redis cache and session configuration.
 *
 * <p>Configures a {@link RedisTemplate} with JSON serialization for values
 * and string serialization for keys. Used for caching and session storage
 * across the application.</p>
 *
 * @since 1.0
 */
@Configuration
@EnableCaching
public class RedisConfig {

    private GenericJackson2JsonRedisSerializer jsonRedisSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        // Restrict polymorphic deserialization to known-safe base types only.
        // Stored values (WizardSession, cached Plan, etc.) live under com.postwerk.*;
        // the JDK prefixes cover the collection/date/string/number members they contain.
        om.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.postwerk.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.math.")
                        .build(),
                DefaultTyping.NON_FINAL
        );
        return new GenericJackson2JsonRedisSerializer(om);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        var serializer = jsonRedisSerializer();
        var template = new RedisTemplate<String, Object>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        var jsonSerializer = jsonRedisSerializer();
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(10));

        RedisCacheConfiguration userPlanConfig = defaultConfig.entryTtl(Duration.ofMinutes(5));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        "userPlans", userPlanConfig,
                        "orgPlans", userPlanConfig
                ))
                .build();
    }
}
