# RAG 智能检索 - 快速启动指南

## 5 分钟快速上手

### 第一步：复制配置

将以下配置添加到你的 `application.yml` 或 `application-local.yml` 文件：

```yaml
tj:
  ai:
    intent:
      enabled: true
      min-length: 10
      long-question-threshold: 30
      rag-keywords:
        - "怎么"
        - "如何"
        - "为什么"
        - "原理"
        - "教程"
        - "学习"
      chat-keywords:
        - "你好"
        - "谢谢"
        - "再见"
    rag:
      similarity-threshold: 0.6
      top-k: 6
```

### 第二步：重启应用

```bash
# 如果使用 Maven
mvn spring-boot:run

# 或者重新打包并运行
mvn clean package -DskipTests
java -jar target/your-app.jar
```

### 第三步：测试效果

#### 测试 1：简单问候（不使用 RAG）
```bash
curl -X POST "http://localhost:8080/chat" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "question=你好&sessionId=test1"
```

**预期结果**：
- 日志显示：`意图识别结果: CHAT, 使用RAG: false`
- 响应速度快（无检索）
- metadata 中 `ragEnabled: false`

#### 测试 2：知识查询（使用 RAG）
```bash
curl -X POST "http://localhost:8080/chat" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "question=Java多线程怎么学？&sessionId=test2"
```

**预期结果**：
- 日志显示：`意图识别结果: KNOWLEDGE_QUERY, 使用RAG: true`
- 响应基于知识库内容
- metadata 中 `ragEnabled: true`

### 第四步：查看日志

```bash
# 实时查看意图识别日志
tail -f logs/app.log | grep "意图识别"

# 统计 RAG 启用率
grep "使用RAG: true" logs/app.log | wc -l
```

## 常见配置场景

### 场景 1：禁用意图识别（所有请求都使用 RAG）

```yaml
tj:
  ai:
    intent:
      enabled: false
```

**适用场景**：
- 知识库内容非常重要
- 不想遗漏任何相关问题
- 可以接受性能开销

### 场景 2：严格模式（只对明确的知识问题使用 RAG）

```yaml
tj:
  ai:
    intent:
      min-length: 20
      long-question-threshold: 50
      rag-keywords:
        - "怎么"
        - "如何"
        - "为什么"
    rag:
      similarity-threshold: 0.7
      top-k: 3
```

**适用场景**：
- 知识库内容有限
- 追求高精度
- 性能要求高

### 场景 3：宽松模式（对大多数问题使用 RAG）

```yaml
tj:
  ai:
    intent:
      min-length: 5
      long-question-threshold: 15
      rag-keywords:
        - "怎么"
        - "如何"
        - "为什么"
        - "是"
        - "有"
        - "能"
    rag:
      similarity-threshold: 0.5
      top-k: 10
```

**适用场景**：
- 知识库内容丰富
- 希望充分利用知识库
- 可以接受较高性能开销

## 性能调优建议

### 1. 调整相似度阈值

**阈值过高（>0.7）**：
- ✅ 检索结果更精准
- ❌ 可能遗漏相关内容
- 适用：知识库质量高

**阈值过低（<0.5）**：
- ✅ 检索结果更全面
- ❌ 可能包含无关内容
- 适用：知识库内容少

**推荐值**：0.6-0.7

### 2. 调整返回数量（topK）

**数量过少（<3）**：
- ✅ 响应速度快
- ❌ 上下文信息少
- 适用：简单问题

**数量过多（>10）**：
- ✅ 上下文信息丰富
- ❌ Token 消耗高
- ❌ 响应延迟大
- 适用：复杂问题

**推荐值**：5-8

### 3. 调整关键词列表

**根据实际使用情况调整**：

```bash
# 查看最常见的问题
grep "问题:" logs/app.log | sort | uniq -c | sort -rn | head -20

# 根据结果添加或删除关键词
```

## 故障排查

### 问题 1：意图识别不生效

**症状**：所有请求都使用 RAG，或都不使用

**检查**：
1. 确认配置文件已正确加载
2. 检查日志中是否有 `意图识别结果`
3. 确认 `tj.ai.intent.enabled` 为 `true`

**解决**：
```bash
# 检查配置是否生效
curl http://localhost:8080/actuator/configprops | grep intent
```

### 问题 2：RAG 检索结果不准确

**症状**：回答与问题不相关

**检查**：
1. 查看日志中的相似度分数
2. 检查向量数据库中的文档质量
3. 确认相似度阈值设置

**解决**：
```yaml
# 提高相似度阈值
tj:
  ai:
    rag:
      similarity-threshold: 0.7  # 从 0.6 提高到 0.7
```

### 问题 3：响应速度慢

**症状**：所有请求都很慢

**检查**：
1. 确认是否启用了意图识别
2. 查看 RAG 启用率
3. 检查向量数据库性能

**解决**：
```yaml
# 优化配置
tj:
  ai:
    intent:
      enabled: true
      min-length: 15  # 提高最小长度
    rag:
      top-k: 4  # 减少返回数量
```

## 监控和运维

### 关键监控指标

```bash
# 1. RAG 启用率
grep "使用RAG: true" logs/app.log | wc -l
grep "使用RAG: false" logs/app.log | wc -l

# 2. 意图分布
grep "意图识别结果" logs/app.log | awk -F',' '{print $1}' | sort | uniq -c

# 3. 平均响应时间（需要配合 APM 工具）
```

### 日志分析脚本

```bash
#!/bin/bash
# analyze_rag_logs.sh

echo "=== RAG 使用统计 ==="
echo "总请求数: $(grep '意图识别结果' logs/app.log | wc -l)"
echo "RAG 启用数: $(grep '使用RAG: true' logs/app.log | wc -l)"
echo "RAG 禁用数: $(grep '使用RAG: false' logs/app.log | wc -l)"

echo ""
echo "=== 意图分布 ==="
grep '意图识别结果' logs/app.log | awk -F'意图识别结果: ' '{print $2}' | awk -F',' '{print $1}' | sort | uniq -c | sort -rn
```

## 最佳实践

### 1. 配置管理
- ✅ 将配置放在 `application.yml` 中
- ✅ 使用 Profile 区分环境（dev/test/prod）
- ✅ 定期根据使用情况调整配置

### 2. 日志监控
- ✅ 开启 DEBUG 日志用于调试
- ✅ 定期分析日志优化配置
- ✅ 设置告警阈值

### 3. 性能优化
- ✅ 根据实际使用调整阈值
- ✅ 定期清理无用的向量数据
- ✅ 监控向量数据库性能

### 4. 持续改进
- ✅ 收集用户反馈
- ✅ 定期更新关键词列表
- ✅ 优化相似度阈值

---

**快速开始时间**: 5 分钟
**配置复杂度**: ⭐⭐ (简单)
**性能提升**: 🚀 显著

有问题？查看 [详细配置文档](RAG_CONFIGURATION.md) 或 [优化总结](RAG_OPTIMIZATION_SUMMARY.md)
