package com.instaclone.messaging.controller;

import com.instaclone.common.pagination.CursorPage;
import com.instaclone.common.pagination.PageParams;
import com.instaclone.common.util.SecurityUtils;
import com.instaclone.messaging.dto.ConversationResponse;
import com.instaclone.messaging.dto.CreateConversationRequest;
import com.instaclone.messaging.dto.MessageRequestCountResponse;
import com.instaclone.messaging.dto.MessageResponse;
import com.instaclone.messaging.dto.SendMessageRequest;
import com.instaclone.messaging.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    @GetMapping("/requests")
    public CursorPage<ConversationResponse> listRequests(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @AuthenticationPrincipal Jwt jwt) {
        return messageService.listRequests(SecurityUtils.currentUserId(jwt), cursor, PageParams.clamp(limit));
    }

    @GetMapping("/requests/count")
    public MessageRequestCountResponse countRequests(@AuthenticationPrincipal Jwt jwt) {
        return messageService.countRequests(SecurityUtils.currentUserId(jwt));
    }

    @PostMapping("/{id}/accept")
    public ConversationResponse acceptRequest(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return messageService.acceptRequest(id, SecurityUtils.currentUserId(jwt));
    }

    @DeleteMapping("/{id}/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void declineRequest(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        messageService.declineRequest(id, SecurityUtils.currentUserId(jwt));
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
