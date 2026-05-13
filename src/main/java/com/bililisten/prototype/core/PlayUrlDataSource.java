package com.bililisten.prototype.core;

import java.util.Map;

public interface PlayUrlDataSource {
    Map<String, Object> fetchPlayUrl(String bvid, long cid);
}

