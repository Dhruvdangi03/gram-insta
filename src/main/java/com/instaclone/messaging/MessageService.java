package com.instaclone.messaging;

import com.instaclone.common.BadRequestException;
import com.instaclone.common.Cursor;
import com.instaclone.common.CursorPage;
import com.instaclone.common.ForbiddenException;
import com.instaclone.common.NotFoundException;
import com.instaclone.user.User;
import com.instaclone.user.UserRepository;
import com.instaclone.user.UserSummary;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One send path for both transports: the REST endpoint (offline-delivery fallback) and the STOMP
 * destination (live delivery) both call sendMessage — persistence, validation, and participant
 * checks live here exactly once. The live push itself is deferred to after commit (see
 * MessageSentEvent/MessagePushPublisher) — never called directly from here — for the same reason
 * the notification pipeline defers its Redis publish: a push must never race an uncommitted or
 * rolled-back row.
 */
@Service
public class MessageService {

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int MAX_MEDIA_URL_LENGTH = 2048;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MessageService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ConversationResponse getOrCreateConversation(Long creatorId, CreateConversationRequest request) {
        User creator = userRepository.findById(creatorId).orElseThrow(() -> new NotFoundException("User not found"));

        List<User> found = userRepository.findAllByUsernameIn(request.participantUsernames());
        Set<String> foundUsernames = found.stream().map(User::getUsername).collect(Collectors.toSet());
        List<String> missing = request.participantUsernames().stream()
                .filter(u -> !foundUsernames.contains(u))
                .toList();
        if (!missing.isEmpty()) {
            throw new NotFoundException("User not found: " + String.join(", ", missing));
        }
        List<User> others = found.stream().filter(u -> !u.getId().equals(creatorId)).distinct().toList();
        if (others.isEmpty()) {
            throw new BadRequestException("A conversation needs at least one other participant");
        }
        // boolean anyBlocked = others.stream()
        //         .anyMatch(u -> moderationService.isBlockedEitherDirection(creatorId, u.getId()));
        // if (anyBlocked) {
        //     throw new ForbiddenException("You can't start a conversation with a blocked account");
        // }

        Conversation conversation;
        if (others.size() == 1) {
            Long otherId = others.get(0).getId();
            // Serializes concurrent get-or-create calls for this pair so two callers can't both
            // pass the lookup below before either has committed its INSERT — see the repository
            // method's Javadoc. Held for the rest of this transaction, released on commit/rollback.
            conversationRepository.acquireOneToOneConversationLock(Math.min(creatorId, otherId), Math.max(creatorId, otherId));
            conversation = conversationRepository
                    .findOneToOneConversation(creatorId, otherId)
                    .orElseGet(() -> createConversation(creator, others, false));
        } else {
            conversation = createConversation(creator, others, true);
        }
        return toResponse(conversation);
    }

    @Transactional(readOnly = true)
    public CursorPage<ConversationResponse> listConversations(Long userId, String cursor, int limit) {
        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Conversation> rows = decoded == null
                ? conversationRepository.findFirstPageByParticipantId(userId, limit + 1)
                : conversationRepository.findPageByParticipantIdAfterCursor(
                        userId, decoded.createdAt(), decoded.id(), limit + 1);

        CursorPage<Conversation> page =
                CursorPage.of(rows, limit, c -> new Cursor(lastActivity(c), c.getId()).encode());
        List<ConversationResponse> items = page.items().stream().map(this::toResponse).toList();
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    @Transactional
    public MessageResponse sendMessage(Long conversationId, Long senderId, SendMessageRequest request) {
        validate(request);
        // Checked before loading the conversation so a nonexistent id and a real conversation the
        // caller isn't part of both resolve to 403 here, matching getHistory below — previously
        // this method 404'd on a bad id before ever checking membership, letting a caller
        // distinguish "exists, I'm just not in it" from "doesn't exist" that the sibling GET
        // endpoint doesn't allow.
        assertParticipant(conversationId, senderId);
        Conversation conversation =
                conversationRepository.findById(conversationId).orElseThrow(() -> new NotFoundException("Conversation not found"));
        User sender = userRepository.findById(senderId).orElseThrow(() -> new NotFoundException("User not found"));

        // getOrCreateConversation only blocks a NEW conversation from being created between blocked
        // parties — without this check, a block already has zero effect once a conversation exists.
        // boolean anyBlocked = conversation.getParticipants().stream()
        //         .filter(p -> !p.getId().equals(senderId))
        //         .anyMatch(p -> moderationService.isBlockedEitherDirection(senderId, p.getId()));
        // if (anyBlocked) {
        //     throw new ForbiddenException("You can't send messages in a conversation with a blocked account");
        // }

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.content());
        message.setMediaUrl(request.mediaUrl());
        message.setCreatedAt(Instant.now());
        message = messageRepository.save(message);

        conversation.setLastMessageAt(message.getCreatedAt());
        conversationRepository.save(conversation);

        MessageResponse response = toResponse(message, UserSummary.from(sender));
        // Every participant, including the sender — a STOMP-originated sender otherwise never
        // learns their own message's server-assigned id/createdAt (the REST path returns it
        // directly in the response body; STOMP has no equivalent unless it's pushed back here).
        List<Long> recipientIds = conversation.getParticipants().stream().map(User::getId).toList();
        eventPublisher.publishEvent(new MessageSentEvent(response, recipientIds));
        return response;
    }

    @Transactional(readOnly = true)
    public CursorPage<MessageResponse> getHistory(Long conversationId, Long viewerId, String cursor, int limit) {
        assertParticipant(conversationId, viewerId);

        Cursor decoded = cursor == null ? null : Cursor.decode(cursor);
        List<Message> rows = decoded == null
                ? messageRepository.findFirstPageByConversationId(conversationId, limit + 1)
                : messageRepository.findPageByConversationIdAfterCursor(
                        conversationId, decoded.createdAt(), decoded.id(), limit + 1);

        CursorPage<Message> page = CursorPage.of(rows, limit, m -> new Cursor(m.getCreatedAt(), m.getId()).encode());

        Set<Long> senderIds = page.items().stream().map(m -> m.getSender().getId()).collect(Collectors.toSet());
        Map<Long, UserSummary> sendersById = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, UserSummary::from));

        List<MessageResponse> items = page.items().stream()
                .map(m -> toResponse(m, sendersById.get(m.getSender().getId())))
                .toList();
        return new CursorPage<>(items, page.nextCursor(), page.hasMore());
    }

    private void validate(SendMessageRequest request) {
        if (blank(request.content()) && blank(request.mediaUrl())) {
            throw new BadRequestException("A message needs content or a mediaUrl");
        }
        // Enforced here rather than relying solely on SendMessageRequest's @Size, since STOMP's
        // @Payload isn't bean-validated the way @Valid @RequestBody is on the REST path — this is
        // the one choke point both transports go through, so it's enforced for both regardless.
        if (request.content() != null && request.content().length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException("content must be at most " + MAX_CONTENT_LENGTH + " characters");
        }
        if (request.mediaUrl() != null && request.mediaUrl().length() > MAX_MEDIA_URL_LENGTH) {
            throw new BadRequestException("mediaUrl must be at most " + MAX_MEDIA_URL_LENGTH + " characters");
        }
    }

    private Conversation createConversation(User creator, List<User> others, boolean group) {
        Conversation conversation = new Conversation();
        conversation.setGroup(group);
        conversation.setCreatedAt(Instant.now());
        Set<User> participants = new HashSet<>(others);
        participants.add(creator);
        conversation.setParticipants(participants);
        return conversationRepository.save(conversation);
    }

    private void assertParticipant(Long conversationId, Long userId) {
        if (!conversationRepository.existsByIdAndParticipantsId(conversationId, userId)) {
            throw new ForbiddenException("You are not a participant in this conversation");
        }
    }

    private Instant lastActivity(Conversation conversation) {
        return conversation.getLastMessageAt() != null ? conversation.getLastMessageAt() : conversation.getCreatedAt();
    }

    private ConversationResponse toResponse(Conversation conversation) {
        List<UserSummary> participants = conversation.getParticipants().stream()
                .map(UserSummary::from)
                .sorted(Comparator.comparing(UserSummary::username))
                .toList();
        return new ConversationResponse(conversation.getId(), conversation.isGroup(), participants, conversation.getCreatedAt());
    }

    private MessageResponse toResponse(Message message, UserSummary sender) {
        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                sender,
                message.getContent(),
                message.getMediaUrl(),
                message.getCreatedAt());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}