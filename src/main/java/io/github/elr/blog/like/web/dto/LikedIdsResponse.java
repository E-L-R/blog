package io.github.elr.blog.like.web.dto;

import java.util.List;
import java.util.UUID;

/** The answer to a liked-state lookup: the subset of the requested IDs that the caller has liked. */
public record LikedIdsResponse(List<UUID> likedIds) {
}
