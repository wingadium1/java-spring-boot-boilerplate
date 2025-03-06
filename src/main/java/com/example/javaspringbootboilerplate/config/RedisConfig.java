package com.example.javaspringbootboilerplate.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {
    @Value("${spring.data.redis.database}")
    private String database;

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private String port;

    @Value("${spring.data.redis.password}")
    private String password;

    @Value("${spring.data.redis.timeout}")
    private String timeout;

    @Bean
    @ConditionalOnMissingBean(name = "lettuceConnectionFactory")
    public LettuceConnectionFactory lettuceConnectionFactory() {
        ClientOptions clientOptions = ClientOptions.builder().disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS).autoReconnect(true).build();
        RedisStandaloneConfiguration clusterConfig = new RedisStandaloneConfiguration(host, Integer.parseInt(port));
        clusterConfig.setDatabase(0);
        clusterConfig.setPassword(password);
        ClientResources clientResources = DefaultClientResources.create();
        LettucePoolingClientConfiguration lettuceClientConfiguration = LettucePoolingClientConfiguration.builder()
                .clientOptions(clientOptions).clientResources(clientResources).build();
        return new LettuceConnectionFactory(clusterConfig, lettuceClientConfiguration);
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTxTemplate")
    public RedisTemplate<String, Object> redisTxTemplate(RedisConnectionFactory lettuceConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(lettuceConnectionFactory);
        template.setEnableTransactionSupport(true);
        ObjectMapper objectMapper = new ObjectMapper();
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(objectMapper,Object.class);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.setValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        template.setEnableTransactionSupport(false);
        return template;
    }
}
