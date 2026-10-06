package com.instaclone.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserVisibilityTest {

    @Test
    void publicAccountIsVisibleToAnyone() {
        User author = author(1L, false);
        assertThat(author.isVisibleTo(user(2L), false)).isTrue();
    }

    @Test
    void publicAccountIsVisibleToAnonymousViewer() {
        User author = author(1L, false);
        assertThat(author.isVisibleTo(null, false)).isTrue();
    }

    @Test
    void privateAccountIsHiddenFromNonFollower() {
        User author = author(1L, true);
        assertThat(author.isVisibleTo(user(2L), false)).isFalse();
    }

    @Test
    void privateAccountIsHiddenFromAnonymousViewer() {
        User author = author(1L, true);
        assertThat(author.isVisibleTo(null, false)).isFalse();
    }

    @Test
    void privateAccountIsVisibleToAcceptedFollower() {
        User author = author(1L, true);
        assertThat(author.isVisibleTo(user(2L), true)).isTrue();
    }

    @Test
    void privateAccountIsAlwaysVisibleToItself() {
        User owner = author(1L, true);
        assertThat(owner.isVisibleTo(owner, false)).isTrue();
    }

    private static User author(Long id, boolean isPrivate) {
        User user = new User();
        user.setId(id);
        user.setPrivate(isPrivate);
        return user;
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
