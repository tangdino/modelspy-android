# ModelSpy

Android app that reveals **which LLM is actually running** behind any OpenAI-compatible API endpoint.

Many third-party LLM relays sell "GPT-5.5", "Claude Opus 5.5", "Grok 4.6" etc. while silently serving a
completely different (usually cheaper) model. ModelSpy detects the substitution.

## Why prompt-based checks fail

Asking a model "who are you?" or "how many r's in strawberry?" is useless against a relay:
the operator simply injects a system prompt and the model answers whatever identity it is told.
ModelSpy was verified against a relay that cloaked **40 fake model names** — every one of them
confidently self-identified as its claimed vendor (OpenAI / Anthropic / xAI / Alibaba / Zhipu).

## Method: numeric-distribution fingerprinting

Instead of asking semantic questions, ModelSpy asks the model to write ~300 integers between
1 and 355 by first instinct. Language models do not produce uniform "random" numbers — each model
has stable preferences in value range, intervals and final digits that come from its **weights**,
so they cannot be faked by prompt injection.

The resulting distribution is compared against a reference bank of 59 modern models
(GPT-5.x/6, Claude 4.x/5, Gemini 3/4, Grok 4.x, Qwen3.8, DeepSeek v4, Seed 2.1, Kimi, GLM, Muse…)
using the ranker from [Ikaleio/lm-detector](https://github.com/Ikaleio/lm-detector):
Hellinger distance + ordered blocks + positional kernel + sequence features + LDA,
with temperature-calibrated closed-set confidence.

Three valid samples per model are required before a confidence is reported.

## Usage

1. Enter the endpoint (e.g. `https://host/v1`) and your API key.
2. Adjust concurrency — **keep it low (4–8)**; relays rate-limit aggressively (HTTP 429).
3. Tap *Start* — the app fetches `/v1/models` and fingerprints every model.
4. Results show the real model plus a confidence percentage and top-5 candidates.

## Build

```bash
export JAVA_HOME=/opt/jdk-21   # JDK 17+ required
./gradlew assembleDebug
```

Requires Android SDK (compileSdk 36), minSdk 26.

## Notes / limitations

- Confidence is a **closed-set** probability: a model absent from the bank still gets a
  "closest" match. Treat results as strong evidence, not cryptographic proof.
- Reasoning models emit hundreds of internal `reasoning_tokens` per call, so each sample
  takes 10–30 s. Budget roughly 1–2 minutes per model.
- The reference bank is a point-in-time snapshot (built 2026-10-13).

## Credits

Detection method and reference bank from [Ikaleio/lm-detector](https://github.com/Ikaleio/lm-detector) (MIT).
