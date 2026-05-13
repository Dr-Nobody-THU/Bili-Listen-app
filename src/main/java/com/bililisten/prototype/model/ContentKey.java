package com.bililisten.prototype.model;

import com.bililisten.prototype.core.BilibiliIds;

public record ContentKey(String bvid, long cid) {
    public ContentKey {
        if (!BilibiliIds.isValidBvid(bvid)) {
            throw new IllegalArgumentException("Invalid BV id: " + bvid);
        }
        if (cid <= 0L) {
            throw new IllegalArgumentException("cid must be > 0.");
        }
    }

    public String asStoreKey() {
        return bvid + ":" + cid;
    }
}

