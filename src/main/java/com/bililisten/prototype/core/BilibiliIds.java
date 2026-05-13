package com.bililisten.prototype.core;

import java.util.regex.Pattern;

public final class BilibiliIds {
    private static final Pattern BVID_PATTERN = Pattern.compile("BV[0-9A-Za-z]{10}");

    private BilibiliIds() {
    }

    public static boolean isValidBvid(String value) {
        return value != null && BVID_PATTERN.matcher(value).matches();
    }
}

