package com.novamind.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.novamind.common.exceptions.CommonException;
import com.novamind.common.utils.JsonUtils;
import com.novamind.search.annotation.VectorField;
import com.novamind.search.constants.SearchErrorInfo;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * R25 ES8 升级：基于 {@code dense_vector} 字段的原生向量检索服务
 * <p>
 * 关键点：
 * <ul>
 *   <li>ES 7.x 需要通过 {@code runtime_mappings} + {@code function_score} 间接实现向量检索，
 *       且不支持原生 {@code cosineSimilarity} 函数；需要借助 painless 脚本。</li>
 *   <li>ES 8.x 原生支持 {@code dense_vector} 字段类型 + {@code cosineSimilarity}、
 *       {@code dotProduct}、{@code l1norm}、{@code l2norm} 等距离函数，
 *       性能远超 painless runtime script 方案。</li>
 *   <li>底层 ANN 算法使用 HNSW（图算法），通过 {@code index: true} + {@code similarity} 参数启用，
 *       K 值通过 {@code k} 参数控制召回数。</li>
 * </ul>
 *
 * @author novamind
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryIndexService {

    private final ElasticsearchClient esClient;

    @Value("${tj.search.memory.index:course_memory}")
    private String memoryIndex;

    /** 向量维度（与 Embedding 模型输出一致，如 text-embedding-ada-002 = 1536） */
    @Value("${tj.search.memory.vector-dim:1536}")
    private int vectorDim;

    /** 检索召回 TopK */
    @Value("${tj.search.memory.top-k:10}")
    private int topK;

    @PostConstruct
    public void initIndex() {
        try {
            ensureIndex();
        } catch (Exception e) {
            log.warn("[MemoryIndex] 初始化失败：{}", e.getMessage());
        }
    }

    /**
     * 确保索引存在，并写入 {@code dense_vector} mapping
     */
    public void ensureIndex() throws IOException {
        // 部署修复注记：exists() 接收 ExistsRequest，原代码误用 GetIndexRequest
        boolean exists = esClient.indices().exists(ExistsRequest.of(b -> b.index(memoryIndex))).value();
        if (exists) {
            return;
        }
        log.info("[MemoryIndex] 自动创建向量索引：{} dim={}", memoryIndex, vectorDim);
        // 1.构造 dense_vector 字段
        Property embedding = Property.of(p -> p.denseVector(
                DenseVectorProperty.of(d -> d
                        .dims(vectorDim)
                        .index(true)               // 启用 HNSW 索引
                        .similarity("cosine")       // 余弦相似度
                )
        ));
        // 2.构造 mapping
        Map<String, Property> props = new HashMap<>();
        props.put("courseId", Property.of(p -> p.long_(l -> l)));
        props.put("name", Property.of(p -> p.text(t -> t)));
        props.put("embedding", embedding);   // ★ ES8 原生向量字段

        // 3.创建索引
        esClient.indices().create(CreateIndexRequest.of(b -> b
                .index(memoryIndex)
                .settings(IndexSettings.of(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                ))
                .mappings(TypeMapping.of(m -> m.properties(props)))
        ));
    }

    /**
     * 写入一条课程向量记忆
     *
     * @param courseId 课程 id
     * @param name     课程名
     * @param vector   1536 维向量
     */
    public void saveMemory(Long courseId, String name, List<Float> vector) {
        if (vector == null || vector.size() != vectorDim) {
            throw new CommonException(SearchErrorInfo.SAVE_COURSE_ERROR,
                    new IllegalArgumentException("向量维度不匹配，期望 " + vectorDim + " 实际 " + vector.size()));
        }
        Map<String, Object> doc = new HashMap<>();
        doc.put("courseId", courseId);
        doc.put("name", name);
        doc.put("embedding", vector);
        try {
            esClient.index(i -> i.index(memoryIndex).id(String.valueOf(courseId)).document(doc));
        } catch (IOException e) {
            throw new CommonException(SearchErrorInfo.SAVE_COURSE_ERROR, e);
        }
    }

    /**
     * 原生 cosineSimilarity 向量检索
     * <p>
     * 8.x 推荐语法：{@code cosineSimilarity(params.query_vector, "embedding") + 1.0}
     * 因为 cosineSimilarity 取值范围是 [-1, 1]，加 1 后变成 [0, 2]，与 score 越大越相关一致。
     *
     * @param queryVector 查询向量
     * @return 命中的 MemoryDoc 列表（按相似度降序）
     */
    public List<MemoryDoc> searchByVector(List<Float> queryVector) {
        try {
            // 1.构造 script_score + cosineSimilarity 查询
            // ES8 Java Client 8.x：通过 Script.of(...) + inline.params(...) 直接传 List<Float>，
            // 序列化层会自动按 JSON 输出为 float[] 数组，Painless 端可以正常接收。
            Script script = Script.of(s -> s.inline(i -> i
                    .lang("painless")
                    .source("cosineSimilarity(params.query_vector, 'embedding') + 1.0")
                    .params("query_vector", co.elastic.clients.json.JsonData.of(queryVector))
            ));

            Query query = Query.of(q -> q.scriptScore(ss -> ss
                    .query(qq -> qq.matchAll(m -> m))
                    .script(script)
                    .minScore(0.0f)   // 部署修复注记：ES8 客户端 minScore 形参为 Float
            ));

            SearchRequest req = SearchRequest.of(s -> s
                    .index(memoryIndex)
                    .query(query)
                    .size(topK)
                    .minScore(1.0)   // 部署修复注记：SearchRequest.minScore 形参为 Double（ScriptScore 的是 Float）
            );
            SearchResponse<Map> resp = esClient.search(req, Map.class);
            List<MemoryDoc> result = new ArrayList<>();
            for (Hit<Map> hit : resp.hits().hits()) {
                MemoryDoc m = JsonUtils.toBean(JsonUtils.toJsonStr(hit.source()), MemoryDoc.class);
                m.setScore(hit.score());
                result.add(m);
            }
            return result;
        } catch (IOException e) {
            throw new CommonException(SearchErrorInfo.QUERY_COURSE_ERROR, e);
        }
    }

    /**
     * 内存中的文档表示
     */
    @Data
    public static class MemoryDoc {
        private Long courseId;
        private String name;

        /**
         * 向量字段（R25 ES8 新增）：使用 {@link VectorField} 标注，dim 由 application-search.yml 注入
         */
        @VectorField(dims = 1536, similarity = "cosine", index = true)
        private List<Float> embedding;

        private Double score;
    }
}
