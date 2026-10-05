package com.instaclone.media;

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
 * Bootstraps the Redis Streams consumer group and the background listener container that reads
 * from it. Runs in-process, inside the same Spring Boot app as everything else — Media only
 * becomes a separately deployed service in Phase 5.
 */
@Configuration
public class MediaStreamConfig {

    public static final String STREAM_KEY = "media-uploads";
    public static final String CONSUMER_GROUP = "transcode-workers";
    private static final String CONSUMER_NAME = "transcode-worker-1";

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> mediaStreamContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            MediaUploadConsumer consumer,
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
