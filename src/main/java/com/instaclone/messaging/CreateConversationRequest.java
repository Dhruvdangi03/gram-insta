package com.instaclone.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** The caller is always a participant automatically; list who else should be in the conversation. */
public record CreateConversationRequest(
        @NotEmpty @Size(max = 50) Set<@NotBlank String> participantUsernames) {}
