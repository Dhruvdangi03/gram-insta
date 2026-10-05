package com.instaclone.notification;

import com.instaclone.common.RedisStreamGroupBootstrapper;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

/**
 * Same shape as com.instaclone.media.MediaStreamConfig — a second Redis Stream/consumer group for
 * a second concern, not a new architecture. Runs in-process, inside the same Spring Boot app.
 */
@Configuration
public class NotificationStreamConfig {

    public static final String STREAM_KEY = "notifications";
    public static final String CONSUMER_GROUP = "notification-workers";
    private static final String CONSUMER_NAME = "notification-worker-1";

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> notificationStreamContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            NotificationConsumer consumer,
            RedisStreamGroupBootstrapper bootstrapper) {
        bootstrapper.ensureConsumerGroup(redisTemplate, STREAM_KEY, CONSUMER_GROUP);

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .build();
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                consumer);

        return container;
    }
}
