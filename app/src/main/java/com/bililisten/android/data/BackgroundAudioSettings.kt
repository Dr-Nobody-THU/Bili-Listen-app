package com.bililisten.android.data

enum class BackgroundSound(val label: String, val group: String, val description: String, val asset: String? = null) {
    WHITE("白噪声", "纯净噪声", "明亮、均匀的沙沙声"),
    PINK("粉噪声", "纯净噪声", "高频更柔和，听感较均衡"),
    BROWN("棕噪声", "纯净噪声", "低沉、厚实的持续声"),
    RAIN("雨声", "自然", "持续落雨的细密声音", "rain"),
    STORM("雷雨", "自然", "雨幕中偶尔传来雷声", "storm"),
    WAVES("海浪", "自然", "海浪涌起、退去", "waves"),
    STREAM("溪流", "自然", "流水轻轻流淌", "stream"),
    WIND("风声", "自然", "缓缓起伏的风", "wind"),
    BIRDS("鸟鸣", "自然", "户外鸟儿的鸣叫", "birds"),
    NIGHT("夏夜虫鸣", "生活与环境", "夜晚的蟋蟀声", "summer-night"),
    FIRE("壁炉", "生活与环境", "火焰与细微的木柴噼啪声", "fireplace"),
    CAFE("咖啡馆", "生活与环境", "轻声交谈与室内环境声", "coffee-shop"),
    TRAIN("列车", "生活与环境", "车轮行进的节奏", "train"),
}

data class BackgroundAudioSettings(
    val enabled: Boolean = false,
    val sound: BackgroundSound = BackgroundSound.RAIN,
    val volume: Float = DEFAULT_VOLUME,
) {
    fun normalized() = copy(volume = if (volume.isFinite()) volume.coerceIn(0f, 1f) else DEFAULT_VOLUME)

    companion object { const val DEFAULT_VOLUME = 0.2f }
}
