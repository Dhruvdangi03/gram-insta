package com.instaclone.messaging.controller;

import com.instaclone.common.exception.BadRequestException;
import com.instaclone.common.exception.ForbiddenException;
import com.instaclone.common.exception.NotFoundException;
import com.instaclone.messaging.dto.SendMessageRequest;
import com.instaclone.messaging.service.MessageService;
import java.security.Principal;
import java.util.Map;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/** Live-delivery transport for messages — delegates straight to the same MessageService.sendMessage
 * the REST endpoint uses, so persistence and participant checks aren't duplicated. */
@Controller
public class MessageStompController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageStompController(MessageService messageService, SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/conversations/{id}/messages")
    public void sendMessage(@DestinationVariable Long id, @Payload SendMessageRequest request, Principal principal) {
        messageService.sendMessage(id, Long.valueOf(principal.getName()), request);
    }

    // GlobalExceptionHandler is @RestControllerAdvice-scoped (Spring MVC only) and has no effect
    // on @MessageMapping handlers — without this, the same domain exceptions sendMessage throws
    // over REST as clean 4xx ProblemDetail responses would instead fall through to Spring's
    // default, unstructured STOMP error handling on this transport.
    @MessageExceptionHandler({ForbiddenException.class, NotFoundException.class, BadRequestException.class})
    public void handleError(Exception e, Principal principal) {
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", Map.of("error", e.getMessage()));
    }
}
