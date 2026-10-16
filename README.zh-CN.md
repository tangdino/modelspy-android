# ModelSpy

安卓应用：揭示任何 OpenAI 兼容接口背后**真正运行的是哪个大模型**。

很多第三方中转站售卖「GPT-5.5」「Claude Opus 5.5」「Grok 4.6」等型号，
实际却在悄悄提供完全不同（通常更便宜）的模型。ModelSpy 用来检测这种替换行为。

## 为什么「直接问它是谁」没用

问模型「你是谁？」「strawberry 有几个 r？」对中转站完全无效——
运营方只要注入一段 system prompt，模型就会照着编造的身份回答。

我们实测过一个中转站，它伪造了 **40 个假型号**，每一个都能「自信」地
自称是对应厂商（OpenAI / Anthropic / xAI / 阿里 / 智谱），甚至包括
`claude-fable-5`、`gpt-5.6-luna` 这种厂商从未发布过的虚构型号。

## 原理：数字分布指纹

ModelSpy 不问语义问题，而是让模型**凭第一反应写出约 300 个 1 到 355 之间的整数**。

语言模型写出的「随机数」并不均匀——每个模型在数值区间、间隔、末位数字上
都有稳定的偏好，这些偏好来自**模型权重**，因此无法通过提示词伪造。

得到的分布会与内置参考库中的 **59 个现代模型**比对
（GPT-5.x/6、Claude 4.x/5、Gemini 3/4、Grok 4.x、Qwen3.8、DeepSeek v4、Seed 2.1、Kimi、GLM、Muse 等），
算法来自 [Ikaleio/lm-detector](https://github.com/Ikaleio/lm-detector)：
Hellinger 距离 + 有序分块 + 位置核 + 序列特征 + LDA，
并经过温度校准，给出闭集置信度。

每个模型需要 **3 份有效样本**才会计算置信度。

## 使用

1. 填入接口地址（如 `https://你的中转站/v1`）和 API Key（默认均为空，需自行填写）
2. 调整并发数——**建议保持较低（4–8）**，中转站限流很激进（HTTP 429）
3. 点击「开始全量扫描」——应用会拉取 `/v1/models` 并逐个指纹识别
4. 结果显示真实模型、置信度百分比和 Top 5 候选

> **耗时说明**：推理模型每次会生成数百个内部思考 token，单份样本需 10–30 秒，
> 一个模型 3 份样本约 1–2 分钟。这是为了不被伪造而必须付出的代价。

## 编译

```bash
export JAVA_HOME=/opt/jdk-21   # 需要 JDK 17+
./gradlew assembleDebug
```

需要 Android SDK（compileSdk 36），最低支持 Android 8.0（minSdk 26）。

## 局限说明

- 置信度是**参考库内的闭集概率**：不在库中的模型也会得到一个「最接近」的匹配。
  请把结果当作强证据，而非密码学意义上的身份证明。
- 参考库是时间快照（构建于 2026-10-13），新模型可能不在其中。
- 中转站配额耗尽时会返回 429（`All available accounts are currently rate limited`），
  此时应等待配额恢复再测，否则会大量出现「样本不足」。

## 致谢

检测方法与参考库来自 [Ikaleio/lm-detector](https://github.com/Ikaleio/lm-detector)（MIT 协议）。
