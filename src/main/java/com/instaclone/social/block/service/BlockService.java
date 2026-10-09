package com.instaclone.social.block.service;

import com.instaclone.common.exception.BadRequestException;
import com.instaclone.common.exception.NotFoundException;
import com.instaclone.notification.service.FollowRequestNotificationCleaner;
import com.instaclone.social.block.entity.UserBlock;
import com.instaclone.social.block.repository.UserBlockRepository;
import com.instaclone.social.follow.repository.FollowRepository;
import com.instaclone.user.dto.UserSummary;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A block is symmetric in effect: once either side blocks the other, neither can follow, view
 * content of, comment on, like, or message the other, and each drops out of the other's search,
 * suggestions, explore and reels. Enforcement lives at those call sites (they ask this service or
 * ProfileVisibilityService); this class owns creating/removing the relation and cleaning up the
 * follow edges and pending follow-request notifications a block makes meaningless.
 */
@Service
public class BlockService {

    private final UserBlockRepository blockRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FollowRequestNotificationCleaner followRequestNotifications;

    public BlockService(
            UserBlockRepository blockRepository,
            UserRepository userRepository,
            FollowRepository followRepository,
            FollowRequestNotificationCleaner followRequestNotifications) {
        this.blockRepository = blockRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.followRequestNotifications = followRequestNotifications;
    }

    @Transactional
    public void block(Long blockerId, String username) {
        User target = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        if (target.getId().equals(blockerId)) {
            throw new BadRequestException("You cannot block yourself");
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, target.getId())) {
            return; // idempotent
        }

        UserBlock block = new UserBlock();
        block.setBlocker(userRepository.getReferenceById(blockerId));
        block.setBlocked(target);
        block.setCreatedAt(Instant.now());
        blockRepository.save(block);

        followRepository.deleteBetween(blockerId, target.getId());
        followRequestNotifications.clear(blockerId, target.getId());
        followRequestNotifications.clear(target.getId(), blockerId);
    }

    @Transactional
    public void unblock(Long blockerId, String username) {
        User target = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        blockRepository.deleteByPair(blockerId, target.getId());
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listBlocked(Long blockerId, int limit) {
        return blockRepository.findBlockedUsers(blockerId, limit).stream()
                .map(UserSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(Long a, Long b) {
        return !a.equals(b) && blockRepository.existsEitherWay(a, b);
    }

    @Transactional(readOnly = true)
    public boolean hasBlocked(Long blockerId, Long targetId) {
        return blockRepository.existsByBlockerIdAndBlockedId(blockerId, targetId);
    }

    /** Ids on either side of a block with this user — for NOT IN exclusions in discovery queries. */
    @Transactional(readOnly = true)
    public List<Long> blockRelatedUserIds(Long userId) {
        return blockRepository.findBlockRelatedUserIds(userId);
    }
}
