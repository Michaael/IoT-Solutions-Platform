package com.ispf.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.cache.annotation.EnableCaching;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
@ConditionalOnProperty(prefix = "ispf.redis", name = "enabled", havingValue = "true")
public class RedisCacheConfig {

    @Bean(destroyMethod = "destroy")
    LettuceConnectionFactory redisConnectionFactory(IspfRedisProperties properties) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
                properties.getHost(),
                properties.getPort()
        );
        standalone.setDatabase(properties.getDatabase());
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            standalone.setPassword(RedisPassword.of(properties.getPassword()));
        }
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(properties.getTimeout())
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    CacheManager cacheManager(LettuceConnectionFactory connectionFactory, IspfRedisProperties properties, ObjectMapper objectMapper) {
        GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(objectMapper);
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(10));

        IspfRedisProperties.Cache cacheTtls = properties.getCache();
        Map<String, RedisCacheConfiguration> perCache = Map.of(
                "contextPack", defaults.entryTtl(cacheTtls.getContextPackTtl()),
                "platformBriefing", defaults.entryTtl(cacheTtls.getPlatformBriefingTtl()),
                "objectAcl", defaults.entryTtl(cacheTtls.getObjectAclTtl())
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
