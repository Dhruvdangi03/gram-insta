package com.instaclone.social.restrict.service;

import com.instaclone.common.exception.BadRequestException;
import com.instaclone.common.exception.NotFoundException;
import com.instaclone.messaging.service.MessageService;
import com.instaclone.social.restrict.entity.UserRestriction;
import com.instaclone.social.restrict.repository.UserRestrictionRepository;
import com.instaclone.user.dto.UserSummary;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A restriction is one-sided and silent: the restricted account's new comments on the restrictor's
 * posts need the restrictor's approval to become public, and its messages go to the restrictor's
 * message requests. The restricted user is never told. */
@Service
public class RestrictService {

    private final UserRestrictionRepository restrictionRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;

    public RestrictService(
            UserRestrictionRepository restrictionRepository, UserRepository userRepository, MessageService messageService) {
        this.restrictionRepository = restrictionRepository;
        this.userRepository = userRepository;
        this.messageService = messageService;
    }

    @Transactional
    public void restrict(Long restrictorId, String username) {
        User target = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        if (target.getId().equals(restrictorId)) {
            throw new BadRequestException("You cannot restrict yourself");
        }
        if (restrictionRepository.existsByRestrictorIdAndRestrictedId(restrictorId, target.getId())) {
            return; // idempotent
        }
        UserRestriction restriction = new UserRestriction();
        restriction.setRestrictor(userRepository.getReferenceById(restrictorId));
        restriction.setRestricted(target);
        restriction.setCreatedAt(Instant.now());
        restrictionRepository.save(restriction);
        messageService.onRestrictionChanged(restrictorId, target.getId(), true);
    }

    @Transactional
    public void unrestrict(Long restrictorId, String username) {
        User target = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        if (!restrictionRepository.existsByRestrictorIdAndRestrictedId(restrictorId, target.getId())) {
            return;
        }
        restrictionRepository.deleteByPair(restrictorId, target.getId());
        messageService.onRestrictionChanged(restrictorId, target.getId(), false);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listRestricted(Long restrictorId, int limit) {
        return restrictionRepository.findRestrictedUsers(restrictorId, limit).stream()
                .map(UserSummary::from)
                .toList();
    }
}
