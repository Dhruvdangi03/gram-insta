package com.instaclone.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotently creates a Redis Stream's consumer group (XGROUP CREATE ... MKSTREAM), shared by
 * every *StreamConfig in this codebase (media, notification) — the one piece of that
 * pattern with zero variation between pipelines. Everything else (when the publish happens
 * relative to the transaction, what the consumer actually does) genuinely differs per pipeline
 * and deliberately stays separate rather than being forced into one generic abstraction.
 */
@Component
public class RedisStreamGroupBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamGroupBootstrapper.class);

    public void ensureConsumerGroup(StringRedisTemplate redisTemplate, String streamKey, String consumerGroup) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), consumerGroup);
        } catch (RedisSystemException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains("BUSYGROUP")) {
                log.debug("Consumer group '{}' already exists on stream '{}'", consumerGroup, streamKey);
            } else {
                throw e;
            }
        }
    }
}
