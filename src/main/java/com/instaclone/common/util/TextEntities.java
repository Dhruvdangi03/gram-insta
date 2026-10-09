package com.instaclone.common.util;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts @mentions and #hashtags from user-written text (captions, comments). The frontend's
 * RichText component applies the same rules to decide what to link, so a change here needs the
 * matching change in frontend/src/components/RichText.
 */
public final class TextEntities {

    /** Caps what one piece of text can trigger, so a caption can't be used to spam notifications. */
    public static final int MAX_MENTIONS = 10;
    public static final int MAX_HASHTAGS = 30;

    // Username rules match RegisterRequest: letters, digits, '.' and '_', 3-30 chars. Not preceded
    // by a word character, '@' or '.', so email addresses (a@b.com) are never read as mentions.
    private static final Pattern MENTION = Pattern.compile("(?<![\\w@.])@([A-Za-z0-9._]{3,30})");
    private static final Pattern HASHTAG = Pattern.compile("(?<![\\p{L}\\p{N}_&#])#([\\p{L}\\p{N}_]{1,50})");

    private TextEntities() {}

    /** Distinct mentioned usernames in order of appearance, as typed (usernames are case-sensitive). */
    public static Set<String> extractMentions(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null) {
            return result;
        }
        Matcher m = MENTION.matcher(text);
        while (m.find() && result.size() < MAX_MENTIONS) {
            String username = stripTrailingDots(m.group(1));
            if (username.length() >= 3) {
                result.add(username);
            }
        }
        return result;
    }

    /** Distinct lowercase tags without the '#', skipping purely numeric ones like "#1". */
    public static Set<String> extractHashtags(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null) {
            return result;
        }
        Matcher m = HASHTAG.matcher(text);
        while (m.find() && result.size() < MAX_HASHTAGS) {
            String tag = m.group(1);
            if (tag.chars().anyMatch(Character::isLetter)) {
                result.add(tag.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    // "thanks @bob." — the final '.' is sentence punctuation, not part of the username.
    private static String stripTrailingDots(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '.') {
            end--;
        }
        return s.substring(0, end);
    }
}
