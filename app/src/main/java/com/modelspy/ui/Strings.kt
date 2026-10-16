package com.modelspy.ui

/** Bilingual UI strings. Switch with AppStrings.lang = "zh" / "en". */
object AppStrings {
    var lang: String = "zh"

    private val zh = mapOf(
        "title" to "API Model Spy",
        "endpoint" to "接口地址",
        "endpoint_hint" to "https://your-relay/v1",
        "apikey" to "API Key",
        "concurrency" to "并发数",
        "concurrency_hint" to "建议 4-8，过高会被限流",
        "start" to "开始全量扫描",
        "stop" to "停止",
        "scanning" to "扫描中",
        "done" to "完成",
        "real_model" to "真实模型",
        "confidence" to "置信度",
        "candidates" to "候选",
        "insufficient" to "样本不足：缺",
        "progress" to "进度",
        "found" to "发现",
        "models" to "个模型",
        "language" to "语言",
        "about" to "关于",
        "check_update" to "检测更新",
        "checking" to "正在检查更新...",
        "up_to_date" to "已是最新版本",
        "update_found" to "发现新版本",
        "download" to "下载",
        "update_failed" to "检查更新失败",
        "about_title" to "关于 ModelSpy",
        "about_body" to "版本 1.0\n\n通过数字分布指纹识别任意 OpenAI 兼容接口背后的真实大模型。\n\n本项目的检测方法与模型参考库基于以下开源项目构建：\nIkaleio/lm-detector (MIT)\nhttps://github.com/Ikaleio/lm-detector",
        "ok" to "确定",
        "current_version" to "当前版本",
    )

    private val en = mapOf(
        "title" to "API Model Spy",
        "endpoint" to "Endpoint",
        "endpoint_hint" to "https://your-relay/v1",
        "apikey" to "API Key",
        "concurrency" to "Concurrency",
        "concurrency_hint" to "Keep 4-8; higher gets rate-limited",
        "start" to "Start full scan",
        "stop" to "Stop",
        "scanning" to "Scanning",
        "done" to "Done",
        "real_model" to "Real model",
        "confidence" to "Confidence",
        "candidates" to "Candidates",
        "insufficient" to "Insufficient samples: missing ",
        "progress" to "Progress",
        "found" to "Found",
        "models" to " models",
        "language" to "Language",
        "about" to "About",
        "check_update" to "Check for updates",
        "checking" to "Checking for updates...",
        "up_to_date" to "Already up to date",
        "update_found" to "New version available",
        "download" to "Download",
        "update_failed" to "Update check failed",
        "about_title" to "About ModelSpy",
        "about_body" to "Version 1.0\n\nIdentifies the real LLM behind any OpenAI-compatible endpoint via numeric-distribution fingerprinting.\n\nBuilt on the detection method and reference bank of:\nIkaleio/lm-detector (MIT)\nhttps://github.com/Ikaleio/lm-detector",
        "ok" to "OK",
        "current_version" to "Current version",
    )

    fun get(key: String): String = (if (lang == "zh") zh else en)[key] ?: key
}
