package io.github.elr.blog.post;

/**
 * Whether a post's TL;DR matches its current content.
 * <ul>
 *   <li>{@code PENDING}: a TL;DR for the current content is being generated. The previous
 *       TL;DR, if any, is still returned, and clients can show it as "updating".</li>
 *   <li>{@code GENERATED}: the TL;DR was generated from the current content.</li>
 *   <li>{@code FAILED}: generating it for the current content failed for good. The next
 *       content edit tries again.</li>
 * </ul>
 */
public enum TldrStatus {
    PENDING, GENERATED, FAILED
}
