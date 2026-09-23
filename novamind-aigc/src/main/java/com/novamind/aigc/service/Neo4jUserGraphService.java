package com.novamind.aigc.service;

import cn.hutool.core.util.StrUtil;
import com.novamind.aigc.config.GraphProperties;
import com.novamind.aigc.dto.ChatDTO;
import com.novamind.aigc.dto.ChatMediaDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "tj.ai.graph", name = "enabled", havingValue = "true")
public class Neo4jUserGraphService implements UserGraphService {

    private final GraphProperties properties;
    private RestClient restClient;

    @PostConstruct
    public void init() {
        this.restClient = RestClient.builder()
                .baseUrl(StrUtil.removeSuffix(properties.getUri(), "/"))
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        initializeSchema();
    }

    @Override
    public String loadPromptContext(Long userId, ChatDTO chatDTO) {
        if (userId == null) {
            return "";
        }
        try {
            var rows = query("""
                    MATCH (u:User {id: $userId})-[r]->(n)
                    RETURN type(r) AS relation, labels(n) AS labels, coalesce(n.name, n.value, n.id) AS value, n.updatedAt AS updatedAt
                    ORDER BY n.updatedAt DESC
                    LIMIT $limit
                    """, Map.of("userId", userId, "limit", Math.max(1, properties.getMaxPromptFacts())));
            if (rows.isEmpty()) {
                return "";
            }
            var builder = new StringBuilder("User knowledge graph context:\n");
            for (Map<String, Object> row : rows) {
                builder.append("- ")
                        .append(row.getOrDefault("relation", "RELATED"))
                        .append(" ")
                        .append(row.getOrDefault("value", "unknown"))
                        .append(" ")
                        .append(row.getOrDefault("labels", ""))
                        .append('\n');
            }
            return builder.toString();
        } catch (Exception e) {
            log.warn("Load Neo4j prompt context failed. userId={}", userId, e);
            return "";
        }
    }

    @Override
    public void recordConversation(Long userId, ChatDTO chatDTO, String answer) {
        if (userId == null || chatDTO == null) {
            return;
        }
        try {
            var now = Instant.now().toString();
            execute("""
                    MERGE (u:User {id: $userId})
                    SET u.updatedAt = $now
                    CREATE (i:Interaction {id: $interactionId, question: $question, answerPreview: $answerPreview, createdAt: $now, updatedAt: $now})
                    MERGE (u)-[:ASKED]->(i)
                    """, Map.of(
                    "userId", userId,
                    "interactionId", chatDTO.getSessionId() + "-" + System.currentTimeMillis(),
                    "question", StrUtil.maxLength(StrUtil.blankToDefault(chatDTO.getQuestion(), ""), 1000),
                    "answerPreview", StrUtil.maxLength(StrUtil.blankToDefault(answer, ""), 1000),
                    "now", now
            ));
            upsertMediaFacts(userId, chatDTO.getMedia(), now);
        } catch (Exception e) {
            log.warn("Record Neo4j conversation failed. userId={}", userId, e);
        }
    }

    @Override
    public Map<String, Object> getGraphVisualization(Long userId) {
        if (userId == null) {
            return Map.of("nodes", List.of(), "edges", List.of());
        }
        try {
            var rows = query("""
                    MATCH (u:User {id: $userId})-[r]->(n)
                    RETURN u.id AS sourceId, type(r) AS relation, n.id AS targetId, labels(n) AS targetLabels, coalesce(n.name, n.value, n.id) AS targetName
                    LIMIT 50
                    """, Map.of("userId", userId));
            var nodes = new ArrayList<Map<String, Object>>();
            var edges = new ArrayList<Map<String, Object>>();

            nodes.add(Map.of("id", String.valueOf(userId), "label", "用户 " + userId, "type", "USER"));

            for (var row : rows) {
                var targetId = String.valueOf(row.getOrDefault("targetId", "unknown"));
                var targetLabel = String.valueOf(row.getOrDefault("targetName", "unknown"));
                var relation = String.valueOf(row.getOrDefault("relation", "RELATED"));

                if (!nodes.stream().anyMatch(n -> n.get("id").equals(targetId))) {
                    var labels = row.getOrDefault("targetLabels", List.of());
                    String type = "INTERACTION";
                    if (labels instanceof List<?> l) {
                        if (l.contains("MediaFact")) type = "MEDIA";
                        else if (l.contains("Preference")) type = "PREFERENCE";
                    }
                    nodes.add(Map.of("id", targetId, "label", targetLabel, "type", type));
                }

                edges.add(Map.of("from", String.valueOf(userId), "to", targetId, "label", relation));
            }

            return Map.of("nodes", nodes, "edges", edges);
        } catch (Exception e) {
            log.warn("Get Neo4j graph visualization failed. userId={}", userId, e);
            return Map.of("nodes", List.of(), "edges", List.of());
        }
    }

    @Override
    public Map<String, Object> status() {
        return Map.of(
                "enabled", true,
                "uri", properties.getUri(),
                "database", properties.getDatabase()
        );
    }

    private void initializeSchema() {
        try {
            execute("CREATE CONSTRAINT user_id IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE", Map.of());
            execute("CREATE INDEX preference_value IF NOT EXISTS FOR (p:Preference) ON (p.value)", Map.of());
            execute("CREATE INDEX media_value IF NOT EXISTS FOR (m:MediaFact) ON (m.value)", Map.of());
        } catch (Exception e) {
            log.warn("Initialize Neo4j schema failed, service will continue without blocking startup.", e);
        }
    }

    private void upsertMediaFacts(Long userId, List<ChatMediaDTO> mediaList, String now) {
        if (mediaList == null || mediaList.isEmpty()) {
            return;
        }
        for (ChatMediaDTO media : mediaList) {
            if (media == null || StrUtil.isBlank(media.getType())) {
                continue;
            }
            String value = StrUtil.blankToDefault(media.getName(), media.getUrl());
            if (StrUtil.isBlank(value)) {
                continue;
            }
            execute("""
                    MATCH (u:User {id: $userId})
                    MERGE (m:MediaFact {type: $type, value: $value})
                    SET m.mimeType = $mimeType, m.updatedAt = $now
                    MERGE (u)-[:PROVIDED_MEDIA]->(m)
                    """, Map.of(
                    "userId", userId,
                    "type", media.getType(),
                    "value", StrUtil.maxLength(value, 300),
                    "mimeType", StrUtil.blankToDefault(media.getMimeType(), ""),
                    "now", now
            ));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> query(String statement, Map<String, Object> parameters) {
        Map<String, Object> body = execute(statement, parameters);
        var results = (List<Map<String, Object>>) body.getOrDefault("results", List.of());
        if (results.isEmpty()) {
            return List.of();
        }
        var result = results.get(0);
        var columns = (List<String>) result.getOrDefault("columns", List.of());
        var data = (List<Map<String, Object>>) result.getOrDefault("data", List.of());
        var rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : data) {
            var rowValues = (List<Object>) item.getOrDefault("row", List.of());
            var row = new LinkedHashMap<String, Object>();
            for (int i = 0; i < columns.size() && i < rowValues.size(); i++) {
                row.put(columns.get(i), rowValues.get(i));
            }
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> execute(String statement, Map<String, Object> parameters) {
        var payload = Map.of("statements", List.of(Map.of(
                "statement", statement,
                "parameters", parameters == null ? Map.of() : parameters
        )));
        var response = restClient.post()
                .uri("/db/{database}/tx/commit", properties.getDatabase())
                .body(payload)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            return Map.of();
        }
        var errors = (List<Map<String, Object>>) response.getOrDefault("errors", List.of());
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Neo4j returned errors: " + errors);
        }
        return response;
    }

    private String basicAuth() {
        String token = properties.getUsername() + ":" + properties.getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
