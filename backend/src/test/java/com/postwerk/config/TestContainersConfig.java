package com.postwerk.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17")
                .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("postwerk")
                .withUsername("postwerk")
                .withPassword("postwerk")
                .withReuse(true);
    }

    @Bean
    @ServiceConnection
    public RedisContainer redisContainer() {
        return new RedisContainer(DockerImageName.parse("redis:7-alpine"))
                .withReuse(true);
    }
}
