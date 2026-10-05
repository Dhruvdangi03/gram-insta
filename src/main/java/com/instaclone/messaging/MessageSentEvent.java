package com.instaclone.messaging;

import java.util.List;

/** Published after a message's owning transaction commits; MessagePushPublisher relays it over STOMP. */
record MessageSentEvent(MessageResponse response, List<Long> recipientIds) {}
