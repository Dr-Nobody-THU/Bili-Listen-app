package com.bililisten.prototype.core;

import java.util.Map;

public interface ViewDataSource {
    Map<String, Object> fetchView(String bvid);
}

