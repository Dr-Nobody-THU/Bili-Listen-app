package com.bililisten.prototype.core;

import com.bililisten.prototype.model.VideoReference;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BilibiliLinkParser {
    private static final Pattern BVID_PATTERN = Pattern.compile("(BV[0-9A-Za-z]{10})");
    private static final Pattern PAGE_PATTERN = Pattern.compile("(^|[?&#])p=(\\d+)");

    public VideoReference parse(String input) {
        String raw = Objects.requireNonNull(input, "input").trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Input must not be blank.");
        }

        Matcher bvidMatcher = BVID_PATTERN.matcher(raw);
        if (!bvidMatcher.find()) {
            throw new IllegalArgumentException("Input does not contain a BV id: " + input);
        }

        String bvid = bvidMatcher.group(1);
        int page = 1;

        Matcher pageMatcher = PAGE_PATTERN.matcher(raw);
        if (pageMatcher.find()) {
            page = Math.max(1, Integer.parseInt(pageMatcher.group(2)));
        }

        return new VideoReference(bvid, page);
    }
}

