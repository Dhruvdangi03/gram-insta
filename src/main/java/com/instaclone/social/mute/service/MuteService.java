package com.instaclone.social.mute.service;

import com.instaclone.common.exception.BadRequestException;
import com.instaclone.common.exception.NotFoundException;
import com.instaclone.social.mute.entity.UserMute;
import com.instaclone.social.mute.repository.UserMuteRepository;
import com.instaclone.user.dto.UserSummary;
import com.instaclone.user.entity.User;
import com.instaclone.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A mute is one-sided and silent: the muted account's posts, reels and stories vanish from the
 * muter's home feed, reels and story tray, but the follow edge, profile access and messaging are
 * untouched and the muted user is never told. */
@Service
public class MuteService {

    private final UserMuteRepository muteRepository;
    private final UserRepository userRepository;

    public MuteService(UserMuteRepository muteRepository, UserRepository userRepository) {
        this.muteRepository = muteRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void mute(Long muterId, String username) {
        User target = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        if (target.getId().equals(muterId)) {
            throw new BadRequestException("You cannot mute yourself");
        }
        if (muteRepository.existsByMuterIdAndMutedId(muterId, target.getId())) {
            return; // idempotent
        }
        UserMute mute = new UserMute();
        mute.setMuter(userRepository.getReferenceById(muterId));
        mute.setMuted(target);
        mute.setCreatedAt(Instant.now());
        muteRepository.save(mute);
    }

    @Transactional
    public void unmute(Long muterId, String username) {
        User target = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        muteRepository.deleteByPair(muterId, target.getId());
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listMuted(Long muterId, int limit) {
        return muteRepository.findMutedUsers(muterId, limit).stream().map(UserSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public boolean hasMuted(Long muterId, Long targetId) {
        return muteRepository.existsByMuterIdAndMutedId(muterId, targetId);
    }

    @Transactional(readOnly = true)
    public List<Long> mutedUserIds(Long muterId) {
        return muteRepository.findMutedIds(muterId);
    }
}
