package com.openbanking.authentication.config.datasource;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:${spring.redis.host:localhost}}")
    private String host;
    @Value("${spring.data.redis.port:${spring.redis.port:6379}}")
    private int port;
    @Value("${spring.data.redis.username:${spring.redis.username:}}")
    private String username;
    @Value("${spring.data.redis.password:${spring.redis.password:}}")
    private String password;
    @Value("${spring.data.redis.ssl.enabled:${spring.redis.ssl.enabled:false}}")
    private boolean sslEnabled;
    @Value("${spring.data.redis.pool.max-total:${spring.redis.pool.max-total:8}}")
    private int maxTotal;
    @Value("${spring.data.redis.pool.max-idle:${spring.redis.pool.max-idle:8}}")
    private int maxIdle;
    @Value("${spring.data.redis.pool.min-idle:${spring.redis.pool.min-idle:0}}")
    private int minIdle;
    @Value("${spring.data.redis.pool.max-wait:${spring.redis.pool.max-wait:3000}}")
    private int maxWait;
    @Value("${spring.data.redis.pool.max-timeout:${spring.redis.pool.max-timeout:5000}}")
    private int maxTimeout;

    @Bean
    public RedisConnectionFactory connectionFactory(RedisConfiguration redisConfiguration) {
        // Pool configuration
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setTestOnBorrow(true); // Validate before use
        poolConfig.setTestWhileIdle(true); // Validate idle connections
        poolConfig.setTestOnReturn(false); // Optional
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMaxWait(Duration.ofMillis(maxWait));

        // Jedis client configuration

        JedisClientConfiguration.JedisClientConfigurationBuilder clientConfigBuilder =
                JedisClientConfiguration.builder()
                        .connectTimeout(Duration.ofMillis(maxTimeout))
                        .readTimeout(Duration.ofMillis(maxTimeout));

        if (sslEnabled) {
            clientConfigBuilder.useSsl();
        }

        JedisClientConfiguration clientConfig = clientConfigBuilder
                .usePooling()
                .poolConfig(poolConfig)
                .build();


        return new JedisConnectionFactory((RedisStandaloneConfiguration) redisConfiguration, clientConfig);
    }

    @Bean(name = "redisConfiguration")
    public RedisConfiguration redisConfiguration() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(host);
        config.setPort(port);
        if (StringUtils.isNotBlank(username)) {
            config.setUsername(username);
        }
        if (StringUtils.isNotBlank(password)) {
            config.setPassword(RedisPassword.of(password));
        } else {
            config.setPassword(RedisPassword.none());
        }
        return config;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}