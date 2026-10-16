package com.modelspy.data

data class Signature(
    val name: String,
    val provider: String,
    val tier: String,
    val tierRank: Int,
    val identityKeywords: List<String>,
    val strawberryFail: Boolean,
    val decimalFail: Boolean,
    val hasReasoningTokens: Boolean,
)

object ModelSignatures {
    val ALL = listOf(
        Signature("GPT-3.5 Turbo", "OpenAI", "budget", 1, listOf("gpt-3.5", "gpt 3.5", "chatgpt"), true, true, false),
        Signature("GPT-4", "OpenAI", "premium", 4, listOf("gpt-4", "gpt 4"), false, false, false),
        Signature("GPT-4 Turbo", "OpenAI", "premium", 4, listOf("gpt-4-turbo", "gpt-4 turbo", "gpt4-turbo"), false, false, false),
        Signature("GPT-4o", "OpenAI", "mid", 3, listOf("gpt-4o"), false, false, false),
        Signature("GPT-4o Mini", "OpenAI", "budget", 1, listOf("gpt-4o-mini", "gpt-4o mini"), true, true, false),
        Signature("o1", "OpenAI", "premium", 5, listOf("o1"), false, false, true),
        Signature("Claude 3 Haiku", "Anthropic", "budget", 1, listOf("claude 3 haiku", "claude-3-haiku"), true, false, false),
        Signature("Claude 3 Sonnet", "Anthropic", "mid", 3, listOf("claude 3 sonnet", "claude-3-sonnet"), false, false, false),
        Signature("Claude 3 Opus", "Anthropic", "premium", 5, listOf("claude 3 opus", "claude-3-opus"), false, false, false),
        Signature("Claude 3.5 Sonnet", "Anthropic", "mid", 3, listOf("claude 3.5 sonnet", "claude-3.5-sonnet", "claude-3-5-sonnet"), false, false, false),
        Signature("Claude 3.5 Haiku", "Anthropic", "budget", 1, listOf("claude 3.5 haiku", "claude-3.5-haiku", "claude-3-5-haiku"), true, false, false),
        Signature("Claude Sonnet 4", "Anthropic", "mid", 3, listOf("claude sonnet 4", "claude-sonnet-4"), false, false, false),
        Signature("Claude Opus 4", "Anthropic", "premium", 5, listOf("claude opus 4", "claude-opus-4"), false, false, false),
        Signature("Gemini 1.5 Pro", "Google", "mid", 3, listOf("gemini 1.5 pro", "gemini-1.5-pro"), false, false, false),
        Signature("Gemini 1.5 Flash", "Google", "budget", 1, listOf("gemini 1.5 flash", "gemini-1.5-flash"), true, false, false),
        Signature("Llama 3 70B", "Meta", "mid", 2, listOf("llama 3", "llama-3", "llama3"), true, true, false),
        Signature("Mistral Large", "Mistral AI", "mid", 3, listOf("mistral large", "mistral-large"), false, false, false),
    )
}
