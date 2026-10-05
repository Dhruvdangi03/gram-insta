package com.instaclone.hashtag;

import com.instaclone.post.Post;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared by PostService and ReelService — both are places a caption can be entered, and this used
 * to be the kind of thing that got copy-pasted between them (see the Phase 3 review's "duplicated
 * self-notification guard" finding); one method here instead.
 */
@Service
public class HashtagService {

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#(\\w+)", Pattern.UNICODE_CHARACTER_CLASS);

    private final HashtagRepository hashtagRepository;
    private final HashtagCreator hashtagCreator;

    public HashtagService(HashtagRepository hashtagRepository, HashtagCreator hashtagCreator) {
        this.hashtagRepository = hashtagRepository;
        this.hashtagCreator = hashtagCreator;
    }

    /** Parses #tags out of the caption, get-or-creates each one, and attaches them to the post. */
    @Transactional
    public Set<String> parseAndAttach(Post post, String caption) {
        if (caption == null || caption.isBlank()) {
            return Set.of();
        }
        Set<String> tags = new LinkedHashSet<>();
        Matcher matcher = HASHTAG_PATTERN.matcher(caption);
        while (matcher.find()) {
            tags.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        if (tags.isEmpty()) {
            return Set.of();
        }
        post.setHashtags(tags.stream().map(this::getOrCreate).collect(Collectors.toSet()));
        return tags;
    }

    // Unlike the 1:1-conversation race Phase 3 hit, a hashtag's uniqueness IS a single-column
    // constraint (hashtags.tag), so a lock isn't needed. The insert attempt runs in HashtagCreator's
    // own REQUIRES_NEW transaction so a losing race only aborts that isolated transaction — retrying
    // the lookup here runs on this method's own (still-valid) transaction instead of the poisoned one.
    private Hashtag getOrCreate(String tag) {
        return hashtagRepository.findByTag(tag).orElseGet(() -> {
            try {
                return hashtagCreator.create(tag);
            } catch (DataIntegrityViolationException e) {
                return hashtagRepository.findByTag(tag).orElseThrow(() -> e);
            }
        });
    }
}
