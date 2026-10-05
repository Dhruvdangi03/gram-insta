package com.instaclone.post;

import jakarta.validation.constraints.Size;

public record UpdatePostRequest(@Size(max = 2200) String caption, @Size(max = 255) String location) {}
