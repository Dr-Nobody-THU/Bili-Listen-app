package com.bililisten.prototype.core;

import com.bililisten.prototype.model.ResolvedQueue;
import com.bililisten.prototype.model.VideoReference;

public interface QueueResolver {
    ResolvedQueue resolve(VideoReference reference);
}

