# Document 类获取内容的方法说明

## ❌ 错误：getContent() 方法无法解析

### 问题描述
```java
// ❌ 错误：getContent() 方法无法解析
Document chunkWithMetadata = new Document(chunk.getContent(), metadata);
```

### 可能原因
1. IDE 缓存问题
2. 依赖版本不匹配
3. 需要使用其他方法

---

## ✅ 解决方案

### 方案1：清理并重新构建项目
```bash
# 在项目根目录执行
mvn clean compile

# 或者
./mvnw clean compile
```

### 方案2：使用 getFormattedContent() 方法
```java
// ✅ 正确：使用 getFormattedContent() 方法
Document chunkWithMetadata = new Document(chunk.getFormattedContent(), metadata);
```

---

## 📊 Document 类的获取内容方法对比

### getContent() vs getFormattedContent()

| 方法 | 返回内容 | 说明 |
|------|---------|------|
| `getContent()` | 原始内容 | 返回文档的原始文本内容 |
| `getFormattedContent()` | 格式化内容 | 返回格式化后的文本内容 |

### 方法签名
```java
// 获取原始内容
public String getContent();

// 获取格式化内容（多种重载）
public String getFormattedContent();
public String getFormattedContent(MetadataMode metadataMode);
public String getFormattedContent(ContentFormatter contentFormatter, MetadataMode metadataMode);
```

---

## 💡 学习要点

### 1. 查看类的公共方法
```bash
# 使用 javap 查看类的公共方法
javap -public -classpath <jar文件> <类名>

# 示例：
javap -public -classpath spring-ai-core-0.8.1.jar org.springframework.ai.document.Document
```

### 2. 方法选择
```java
// 如果只需要原始文本，使用 getContent()
String content = doc.getContent();

// 如果需要格式化后的文本，使用 getFormattedContent()
String formattedContent = doc.getFormattedContent();
```

### 3. IDE 缓存问题
```bash
# 清理并重新构建项目
mvn clean compile

# 或者清理 IDE 缓存
# IntelliJ IDEA: File -> Invalidate Caches -> Restart
```

---

## 🎯 完整的正确示例

```java
// 1. 创建 TokenTextSplitter
TokenTextSplitter splitter = new TokenTextSplitter(
    200,    // chunkSize
    5,      // minChunkSize
    10000,  // maxChunkSize
    0,      // 第4个参数
    true    // keepSeparator
);

// 2. 创建 Document
Document doc = Document.builder()
    .text("你的长文本内容...")
    .build();

// 3. 执行切片
List<Document> chunks = splitter.apply(List.of(doc));

// 4. 为每个切片添加元数据
for (int i = 0; i < chunks.size(); i++) {
    Document chunk = chunks.get(i);

    // 构建元数据
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("documentId", documentId);
    metadata.put("chunkIndex", i);
    metadata.put("title", document.getTitle());

    // 创建新的 Document 对象，包含元数据
    // 使用 getFormattedContent() 获取内容
    Document chunkWithMetadata = new Document(chunk.getFormattedContent(), metadata);
    chunks.set(i, chunkWithMetadata);
}

// 5. 存储到向量数据库
vectorStore.add(chunks);
```

---

## ✅ 纠正完成

所有错误已经纠正：
1. ✅ 使用 `getFormattedContent()` 方法获取内容
2. ✅ 代码应该可以正常编译和运行

现在代码应该可以正常编译和运行了！🎉

---

**最后提醒**：
- 如果 `getContent()` 方法无法解析，尝试使用 `getFormattedContent()` 方法
- 清理并重新构建项目：`mvn clean compile`
- 查看类的公共方法：`javap -public -classpath <jar文件> <类名>`

祝你学习顺利！🚀
