# RAG 智能检索配置指南

## 概述

本项目实现了智能 RAG（检索增强生成）策略，通过意图识别自动判断用户问题是否需要知识库检索，优化性能和用户体验。

## 核心特性

### 1. 智能意图识别
- **自动判断**：根据用户问题自动判断是否需要 RAG
- **关键词匹配**：基于配置化的关键词进行意图分类
- **长度分析**：问题长度影响 RAG 启用决策
- **可配置**：支持动态调整关键词和阈值

### 2. 性能优化
- **按需检索**：只有需要时才查询向量数据库
- **减少 Token 消耗**：避免不必要的上下文注入
- **降低延迟**：简单问题直接回答，无需检索

### 3. 状态透明
- **有状态返回**：后端返回 RAG 启用状态和意图类型
- **前端可控**：前端可根据状态决定是否展示给用户
- **调试友好**：完整的日志记录意图识别过程

## 配置参数

### 意图识别配置 (`tj.ai.intent`)

```yaml
tj:
  ai:
    intent:
      # 是否启用意图识别（默认：true）
      enabled: true

      # 问题最小长度（低于此长度直接跳过 RAG，默认：10）
      min-length: 10

      # 长问题阈值（超过此长度默认启用 RAG，默认：30）
      long-question-threshold: 30

      # RAG 相关关键词列表
      rag-keywords:
        - "怎么"
        - "如何"
        - "为什么"
        - "原理"
        - "教程"
        - "学习"
        - "入门"
        - "进阶"
        - "配置"
        - "部署"
        - "错误"
        - "问题"

      # 闲聊关键词列表
      chat-keywords:
        - "你好"
        - "嗨"
        - "谢谢"
        - "再见"
        - "在吗"
```

### RAG 检索配置 (`tj.ai.rag`)

```yaml
tj:
  ai:
    rag:
      # 相似度阈值（0-1，越高越严格，默认：0.6）
      similarity-threshold: 0.6

      # 返回结果数量（默认：6）
      top-k: 6
```

## 意图分类规则

| 意图类型 | 条件 | 是否启用 RAG | 说明 |
|---------|------|-------------|------|
| **EMPTY** | 问题为空 | ❌ 否 | 无输入，跳过 |
| **CHAT** | 包含闲聊关键词 | ❌ 否 | 简单问候，直接回答 |
| **SIMPLE_QUERY** | 长度 < 30 且无关键词 | ❌ 否 | 简单问题，直接回答 |
| **KNOWLEDGE_QUERY** | 包含 RAG 关键词 | ✅ 是 | 知识查询，启用 RAG |
| **COMPLEX_QUERY** | 长度 ≥ 30 | ✅ 是 | 复杂问题，启用 RAG |

## 返回数据结构

### ChatEventVO metadata 字段

```json
{
  "metadata": {
    "mediaCount": 0,
    "graphContextAttached": false,
    "ragEnabled": true,
    "intentType": "KNOWLEDGE_QUERY",
    "ragTopK": 6,
    "ragSimilarityThreshold": 0.6
  }
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `ragEnabled` | boolean | RAG 是否启用 |
| `intentType` | string | 意图类型（EMPTY/CHAT/SIMPLE_QUERY/KNOWLEDGE_QUERY/COMPLEX_QUERY） |
| `ragTopK` | int | RAG 返回结果数量（仅在 ragEnabled=true 时返回） |
| `ragSimilarityThreshold` | double | RAG 相似度阈值（仅在 ragEnabled=true 时返回） |

## 前端集成建议

### 1. 显示 RAG 状态

```javascript
// 监听 SSE 事件
eventSource.addEventListener('THOUGHT', (event) => {
  const data = JSON.parse(event.data);
  const metadata = data.metadata;

  if (metadata.ragEnabled) {
    showRagIndicator(true, metadata.intentType);
    console.log(`RAG 已启用，意图类型: ${metadata.intentType}`);
  } else {
    showRagIndicator(false, metadata.intentType);
    console.log(`RAG 未启用，意图类型: ${metadata.intentType}`);
  }
});
```

### 2. 用户开关（可选）

```html
<label>
  <input type="checkbox" v-model="forceRag">
  强制使用知识库
</label>
```

```javascript
// 前端可以传递参数给后端
const params = new URLSearchParams({
  question: userQuestion,
  sessionId: sessionId,
  forceRag: forceRag  // 可选参数
});
```

## 性能优化建议

### 1. 缓存策略
- **问题缓存**：相同问题不重复进行意图识别
- **结果缓存**：相同问题的 RAG 结果可以缓存

### 2. 异步处理
- **预检索**：用户输入时就开始异步检索
- **并行处理**：意图识别和记忆加载可以并行

### 3. 监控指标
- **意图识别耗时**：监控 IntentClassifier 执行时间
- **RAG 启用率**：统计 RAG 启用的比例
- **检索耗时**：监控向量数据库查询时间

## 调试和日志

### 日志级别配置

```yaml
logging:
  level:
    com.novamind.aigc.service.IntentClassifier: DEBUG
    com.novamind.aigc.advisor.AdvisorFactory: DEBUG
```

### 日志示例

```
2024-01-15 10:30:15.123 DEBUG IntentClassifier - 问题长度过短(5字符)，跳过RAG: 你好
2024-01-15 10:30:15.456 DEBUG IntentClassifier - 检测到RAG关键词 '怎么'，启用RAG
2024-01-15 10:30:15.789 INFO  ChatServiceImpl - 意图识别结果: KNOWLEDGE_QUERY, 使用RAG: true, 问题: Java多线程怎么学？
```

## 扩展点

### 1. 自定义意图识别策略

如果需要更复杂的意图识别（如机器学习模型），可以：

```java
@Service
public class AdvancedIntentClassifier implements IntentClassifier {

    @Override
    public boolean shouldUseRag(String question) {
        // 调用 ML 模型进行意图分类
        IntentResult result = mlModel.classify(question);
        return result.needsRag();
    }
}
```

### 2. A/B 测试

可以配置不同的意图识别策略进行 A/B 测试：

```yaml
tj:
  ai:
    intent:
      strategy: KEYWORDS  # 或 ML_MODEL
```

## 常见问题

### Q: 如何添加新的 RAG 关键词？

在配置文件中添加：
```yaml
tj:
  ai:
    intent:
      rag-keywords:
        - "你的新关键词"
```

### Q: 如何禁用意图识别？

```yaml
tj:
  ai:
    intent:
      enabled: false
```

### Q: 如何调整相似度阈值？

根据实际效果调整：
```yaml
tj:
  ai:
    rag:
      similarity-threshold: 0.7  # 提高阈值，更严格
```

## 最佳实践

1. **关键词维护**：定期根据用户问题更新关键词列表
2. **阈值调优**：根据实际检索效果调整相似度阈值
3. **性能监控**：监控 RAG 启用率和检索耗时
4. **用户反馈**：收集用户对回答质量的反馈，持续优化

---

**最后更新**: 2024-01-15
**版本**: 1.0.0
