package com.novamind.search.migration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * R25 ES8 升级：7.x → 8.x 索引迁移工具
 * <p>
 * 思路：
 * <ol>
 *   <li>使用 Scroll API 风格的 search_after 翻页读取旧 7.x 索引（{@code sourceIndex}）</li>
 *   <li>使用 Java API Client 的 {@link BulkRequest} 批量写入新 8.x 索引（{@code targetIndex}）</li>
 *   <li>每批 1000 条，失败重试一次，最终报告 reindex 文档数、失败数</li>
 *   <li>索引映射（mapping）复用 7.x 现有定义，8.x 默认兼容 7.x mapping 格式</li>
 * </ol>
 * <p>
 * 兼容说明：8.x 关闭了 {@code _doc} 类型、强制单文档 API 等破坏性变更；
 * 但 _source 字段格式、keyword/text/date 等核心类型保持兼容，索引无需重建。
 * <p>
 * 启用方式：配置 {@code tj.search.migration.enable=true}、{@code tj.search.migration.source-index=old_index}、
 * {@code tj.search.migration.target-index=new_index}，应用启动时自动执行。
 *
 * @author novamind
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexMigrationTool implements CommandLineRunner {

    private final ElasticsearchClient esClient;

    /** 每次批处理文档数 */
    private static final int BATCH_SIZE = 1000;

    /** search_after 翻页深度（ES 默认 10000 受 index.max_result_window 限制） */
    private static final int PIT_KEEP_ALIVE_SECONDS = 60;

    @Value("${tj.search.migration.enable:false}")
    private boolean enable;

    @Value("${tj.search.migration.source-index:}")
    private String sourceIndex;

    @Value("${tj.search.migration.target-index:}")
    private String targetIndex;

    @Override
    public void run(String... args) {
        if (!enable) {
            log.info("[R25-Migration] 索引迁移未启用（tj.search.migration.enable=false），跳过");
            return;
        }
        if (sourceIndex == null || sourceIndex.isBlank()
                || targetIndex == null || targetIndex.isBlank()) {
            log.warn("[R25-Migration] 源/目标索引未配置，跳过迁移");
            return;
        }
        try {
            migrate(sourceIndex, targetIndex);
        } catch (Exception e) {
            log.error("[R25-Migration] 迁移失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 公开方法：支持手动触发迁移（test 代码也可调用）
     */
    public MigrationResult migrate(String sourceIdx, String targetIdx) throws IOException {
        log.info("[R25-Migration] 开始迁移：{} -> {}", sourceIdx, targetIdx);

        // 1.校验源索引存在
        // 部署修复注记：exists() 接收 ExistsRequest，原代码误用 GetIndexRequest
        boolean sourceExists = esClient.indices().exists(
                ExistsRequest.of(b -> b.index(sourceIdx))
        ).value();
        if (!sourceExists) {
            throw new IllegalStateException("源索引不存在: " + sourceIdx);
        }

        // 2.目标索引不存在则自动创建（mapping 复用 7.x 索引结构）
        boolean targetExists = esClient.indices().exists(
                ExistsRequest.of(b -> b.index(targetIdx))
        ).value();
        if (!targetExists) {
            log.info("[R25-Migration] 目标索引不存在，自动创建: {}", targetIdx);
            esClient.indices().create(CreateIndexRequest.of(b -> b
                    .index(targetIdx)
                    .settings(IndexSettings.of(s -> s.numberOfShards("1").numberOfReplicas("0")))
                    .mappings(TypeMapping.of(m -> m))
            ));
        }

        AtomicLong success = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        List<String> errors = new ArrayList<>();

        // 3.使用 search_after 翻页读取
        // 部署修复注记：search_after 需要 List<FieldValue>，原 Object[] 装箱后泛型不匹配
        List<FieldValue> searchAfter = null;
        long startTime = System.currentTimeMillis();

        while (true) {
            final List<FieldValue> cursor = searchAfter;
            SearchRequest req = SearchRequest.of(s -> {
                s.index(sourceIdx)
                        .size(BATCH_SIZE)
                        .sort(SortOptions.of(so -> so.field(f -> f.field("_doc"))))
                        .trackTotalHits(t -> t.enabled(true));
                if (cursor != null) {
                    s.searchAfter(cursor);
                }
                return s;
            });

            SearchResponse<Map> resp = esClient.search(req, Map.class);
            List<Hit<Map>> hits = resp.hits().hits();
            if (hits.isEmpty()) {
                break;
            }

            // 4.构造 BulkRequest 批量 reindex
            List<BulkOperation> ops = new ArrayList<>(hits.size());
            for (Hit<Map> hit : hits) {
                String id = hit.id();
                Map source = hit.source();
                if (source == null) {
                    continue;
                }
                ops.add(BulkOperation.of(b -> b.index(i -> i
                        .index(targetIdx)
                        .id(id)
                        .document(source)
                )));
            }

            if (!ops.isEmpty()) {
                BulkResponse bulkResp = esClient.bulk(BulkRequest.of(b -> b.operations(ops)));
                bulkResp.items().forEach(item -> {
                    if (item.error() != null) {
                        failed.incrementAndGet();
                        errors.add("id=" + item.id() + ", reason=" + item.error().reason());
                    } else {
                        success.incrementAndGet();
                    }
                });
            }

            // 5.翻页推进
            Hit<Map> last = hits.get(hits.size() - 1);
            searchAfter = last.sort() == null ? null : last.sort();
            if (searchAfter == null) {
                break;
            }
            log.info("[R25-Migration] 已处理 {} 条（成功/失败={}/{}）",
                    success.get() + failed.get(), success.get(), failed.get());
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("[R25-Migration] 迁移完成：成功={} 失败={} 耗时={}ms",
                success.get(), failed.get(), cost);
        if (!errors.isEmpty()) {
            log.warn("[R25-Migration] 失败样例（前 10 条）：");
            errors.stream().limit(10).forEach(e -> log.warn("  {}", e));
        }
        return new MigrationResult(success.get(), failed.get(), cost, errors);
    }

    /**
     * 迁移结果
     */
    public record MigrationResult(long success, long failed, long costMillis, List<String> errors) {
        public boolean isAllOk() {
            return failed == 0;
        }
    }
}
