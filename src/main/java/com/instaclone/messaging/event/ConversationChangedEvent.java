package com.instaclone.messaging.event;

import java.util.List;

/** Published when a conversation is created/accepted/declined; relayed so clients refetch their inbox and request count. */
public record ConversationChangedEvent(List<Long> userIds) {}
