package com.bililisten.prototype.model;

import com.bililisten.prototype.core.BilibiliIds;

public record VideoReference(String bvid, int page) {
    public VideoReference {
        if (!BilibiliIds.isValidBvid(bvid)) {
            throw new IllegalArgumentException("Invalid BV id: " + bvid);
        }
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1.");
        }
    }
}

