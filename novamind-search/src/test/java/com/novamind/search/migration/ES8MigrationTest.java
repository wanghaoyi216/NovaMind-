package com.novamind.search.migration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R25 ES8 升级：7.x → 8.x 索引迁移一致性测试
 * <p>
 * 验证目标：
 * <ol>
 *   <li>在 7.x 模拟索引（旧 mapping/旧 API 写）写入 50 条文档</li>
 *   <li>使用 {@link IndexMigrationTool}（基于 BulkRequest 的 search_after 翻页）迁移到 8.x 目标索引</li>
 *   <li>断言：源/目标文档数一致、字段一致、查询结果集完全相同</li>
 *   <li>验证 Java API Client 8.x 的 {@link ElasticsearchClient} 已正常注入</li>
 * </ol>
 * <p>
 * 环境要求：本地 9200 端口有 ES 8.x 服务可用。CI 环境下可通过 {@code @DisabledIfEnvironmentVariable}
 * 或 docker-compose 自动启停。
 *
 * @author novamind
 */
@Slf4j
@SpringBootTest(classes = com.novamind.SearchApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test"})
@DisplayName("R25 ES8 索引迁移一致性测试")
class ES8MigrationTest {

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private IndexMigrationTool migrationTool;

    private static final String SOURCE_INDEX = "course_v7_test";
    private static final String TARGET_INDEX = "course_v8_test";
    private static final int DOC_COUNT = 50;

    @BeforeEach
    void setUp() throws IOException {
        // 清理可能残留的索引
        deleteIndexIfExists(SOURCE_INDEX);
        deleteIndexIfExists(TARGET_INDEX);

        // 1.创建 7.x 风格源索引（mapping 与生产一致）
        esClient.indices().create(CreateIndexRequest.of(b -> b
                .index(SOURCE_INDEX)
                .settings(IndexSettings.of(s -> s.numberOfShards("1").numberOfReplicas("0")))
                .mappings(m -> m
                        .properties("id", p -> p.long_(l -> l))
                        .properties("name", p -> p.text(t -> t))
                        .properties("categoryIdLv1", p -> p.long_(l -> l))
                        .properties("free", p -> p.boolean_(bb -> bb))
                        .properties("sold", p -> p.integer(i -> i))
                        .properties("publishTime", p -> p.date(d -> d))
                )
        ));

        // 2.写入 50 条文档（用 7.x 兼容的格式）
        List<BulkOperation> ops = new ArrayList<>();
        for (int i = 0; i < DOC_COUNT; i++) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("id", 1000L + i);
            doc.put("name", "Java 实战课-" + i);
            doc.put("categoryIdLv1", 1001L);
            doc.put("free", i % 2 == 0);
            doc.put("sold", 100 + i);
            doc.put("publishTime", "2024-01-01T00:00:00Z");
            int finalI = i;
            ops.add(BulkOperation.of(b -> b.index(idx -> idx
                    .index(SOURCE_INDEX)
                    .id(String.valueOf(1000 + finalI))
                    .document(doc)
            )));
        }
        BulkResponse bulkResp = esClient.bulk(BulkRequest.of(b -> b.operations(ops).refresh(co.elastic.clients.elasticsearch._types.Refresh.True)));
        log.info("[TEST] 7.x 模拟索引已写入 {} 条", bulkResp.items().size());

        // 3.刷新一下确保可被搜到
        esClient.indices().refresh(r -> r.index(SOURCE_INDEX));
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteIndexIfExists(SOURCE_INDEX);
        deleteIndexIfExists(TARGET_INDEX);
    }

    @Test
    @DisplayName("1) ES8 Java API Client 已成功注入")
    void shouldInjectElasticsearchClient() {
        assertNotNull(esClient, "ElasticsearchClient Bean 注入失败");
        log.info("[TEST] ElasticsearchClient 注入成功: {}", esClient.getClass().getName());
    }

    @Test
    @DisplayName("2) 7.x 索引源文档数 == DOC_COUNT")
    void sourceIndexShouldContainAllDocs() throws IOException {
        CountResponse count = esClient.count(CountRequest.of(c -> c.index(SOURCE_INDEX)));
        log.info("[TEST] 源索引文档数: {}", count.count());
        assertEquals(DOC_COUNT, count.count(), "源索引文档数不匹配");
    }

    @Test
    @DisplayName("3) reindex 后 8.x 目标索引文档数一致")
    void reindexShouldPreserveDocumentCount() throws Exception {
        IndexMigrationTool.MigrationResult result = migrationTool.migrate(SOURCE_INDEX, TARGET_INDEX);
        log.info("[TEST] 迁移结果：success={} failed={} cost={}ms",
                result.success(), result.failed(), result.costMillis());

        esClient.indices().refresh(r -> r.index(TARGET_INDEX));
        CountResponse count = esClient.count(CountRequest.of(c -> c.index(TARGET_INDEX)));

        assertAll(
                () -> assertTrue(result.isAllOk(), "迁移存在失败文档：errors=" + result.errors()),
                () -> assertEquals(DOC_COUNT, result.success(), "迁移成功数不匹配"),
                () -> assertEquals(DOC_COUNT, count.count(), "目标索引文档数不匹配")
        );
    }

    @Test
    @DisplayName("4) 源/目标索引检索结果完全一致（顺序不计）")
    void reindexSearchResultShouldMatch() throws Exception {
        // 1.先迁移
        migrationTool.migrate(SOURCE_INDEX, TARGET_INDEX);
        esClient.indices().refresh(r -> r.index(TARGET_INDEX));

        // 2.从两端各查询 categoryIdLv1=1001 的所有文档
        List<Long> sourceIds = matchAllIds(SOURCE_INDEX);
        List<Long> targetIds = matchAllIds(TARGET_INDEX);

        log.info("[TEST] sourceIds.size={}, targetIds.size={}", sourceIds.size(), targetIds.size());

        // 3.排序后逐位比对
        sourceIds.sort(Long::compareTo);
        targetIds.sort(Long::compareTo);
        assertEquals(sourceIds, targetIds, "源/目标 ID 集合不一致");
        assertEquals(DOC_COUNT, sourceIds.size(), "命中数量异常");
    }

    @Test
    @DisplayName("5) 字段内容无损（采样比对单条文档字段值）")
    void reindexFieldContentShouldMatch() throws Exception {
        migrationTool.migrate(SOURCE_INDEX, TARGET_INDEX);
        esClient.indices().refresh(r -> r.index(TARGET_INDEX));

        // 取 id=1023 的文档（也就是索引第 23 条）
        Map source = getById(SOURCE_INDEX, "1023");
        Map target = getById(TARGET_INDEX, "1023");

        assertNotNull(source, "源文档不存在");
        assertNotNull(target, "目标文档不存在");

        // 字段值必须完全一致（ES 自动做类型转换，字符串/数值应匹配）
        assertEquals(source.get("name"), target.get("name"), "name 字段不一致");
        assertEquals(source.get("categoryIdLv1"), target.get("categoryIdLv1"), "categoryIdLv1 字段不一致");
        assertEquals(source.get("free"), target.get("free"), "free 字段不一致");
        assertEquals(Objects.toString(source.get("sold")), Objects.toString(target.get("sold")), "sold 字段不一致");
    }

    // ---------------------- helpers ----------------------

    private List<Long> matchAllIds(String index) throws IOException {
        SearchResponse<Map> resp = esClient.search(SearchRequest.of(s -> s
                .index(index)
                .size(1000)
                .query(Query.of(q -> q.matchAll(m -> m)))
        ), Map.class);
        List<Long> ids = new ArrayList<>();
        for (Hit<Map> hit : resp.hits().hits()) {
            Object id = hit.source() != null ? hit.source().get("id") : null;
            if (id instanceof Number) {
                ids.add(((Number) id).longValue());
            } else {
                ids.add(Long.valueOf(hit.id()));
            }
        }
        return ids;
    }

    private Map getById(String index, String id) throws IOException {
        return esClient.get(g -> g.index(index).id(id), Map.class).source();
    }

    private void deleteIndexIfExists(String index) throws IOException {
        try {
            esClient.indices().delete(DeleteIndexRequest.of(d -> d.index(index)));
        } catch (Exception ignored) {
            // 索引可能不存在，忽略
        }
    }
}
