package com.instaclone.messaging.event;

import com.instaclone.messaging.dto.MessageResponse;
import java.util.List;

/** Published after a message's owning transaction commits; MessagePushPublisher relays it over STOMP. */
public record MessageSentEvent(MessageResponse response, List<Long> recipientIds) {}
