package com.instaclone.messaging;

import com.instaclone.common.CursorPage;
import com.instaclone.common.PageParams;
import com.instaclone.common.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final MessageService messageService;

    public ConversationController(MessageService messageService) {
        this.messageService = messageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createConversation(
            @Valid @RequestBody CreateConversationRequest request, @AuthenticationPrincipal Jwt jwt) {
        return messageService.getOrCreateConversation(SecurityUtils.currentUserId(jwt), request);
    }

    @GetMapping
    public CursorPage<ConversationResponse> listConversations(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return messageService.listConversations(SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @PathVariable Long id, @Valid @RequestBody SendMessageRequest request, @AuthenticationPrincipal Jwt jwt) {
        return messageService.sendMessage(id, SecurityUtils.currentUserId(jwt), request);
    }

    @GetMapping("/{id}/messages")
    public CursorPage<MessageResponse> getHistory(
            @PathVariable Long id,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return messageService.getHistory(id, SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }
}
