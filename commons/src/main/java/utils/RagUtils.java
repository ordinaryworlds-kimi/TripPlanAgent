package utils;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.VDBStoreBase;
import io.agentscope.core.tool.Toolkit;
import lombok.extern.slf4j.Slf4j;
import utils.rag.LocalHashEmbeddingModel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/**
 * RAG 公共工具：SimpleKnowledge + InMemoryStore + AGENTIC 模式。
 * <p>
 * 供 {@code TripPlannerAgent} 在启动时创建知识库、加载 Markdown 文档，并将 RAG 能力挂到 ReActAgent 上。
 * Chat 使用 DeepSeek；Embedding 使用本地哈希向量（DeepSeek 官方暂无 /v1/embeddings 接口）。
 * <p>
 * 运行时检索由 AgentScope 内置工具 {@code retrieve_knowledge} 触发，无需在本类中手写检索调用。
 */
@Slf4j
public final class RagUtils {

    private static final int DEFAULT_DIMENSIONS = 1024;
    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;
    private static final int DEFAULT_RETRIEVE_LIMIT = 5;
    private static final float DEFAULT_SCORE_THRESHOLD = 0.35f;

    /** classpath 下待入库的 Markdown 知识文件（相对 {@code commons/src/main/resources/}） */
    private static final List<String> KNOWLEDGE_FILES = List.of(
            "knowledge/overview.md",
            "knowledge/domestic-travel-tips.md",
            "knowledge/sample-cities.md"
    );

    /**
     * 行程规划 Agent 的系统提示词。
     * <p>
     * 引导 LLM 在 AGENTIC 模式下主动调用 {@code retrieve_knowledge}，再基于检索结果作答。
     */
    public static final String TRIP_PLANNER_SYS_PROMPT = """
            你是行程规划专家，擅长景点、餐饮、住宿与当地玩法安排。
            当任务涉及具体城市、景点、美食或住宿时，请先调用 retrieve_knowledge 检索知识库，再结合检索结果作答。
            若知识库没有相关内容，可结合常识回答，并说明依据可能不完整。
            """;

    private RagUtils() {
    }

    /**
     * 读取 {@code RAG_ENABLED} 配置，判断是否启用 RAG。
     * <p>
     * 优先读环境变量，其次读 classpath 下的 {@code .env}；默认 {@code true}。
     *
     * @return {@code true} 表示 TripPlannerAgent 应挂载知识库
     */
    public static boolean isRagEnabled() {
        return Boolean.parseBoolean(resolveProperty("RAG_ENABLED", List.of("RAG_ENABLED"), "true"));
    }

    /**
     * 创建旅游知识库并完成启动时文档入库。
     * <p>
     * 流程：构建 Embedding 模型 → 创建 InMemory 向量库 → {@link SimpleKnowledge} 组装
     * → 扫描 {@link #KNOWLEDGE_FILES} 分块写入向量库。
     *
     * @return 已索引文档的 {@link Knowledge} 实例，供 {@link #applyAgenticRag} 使用
     */
    public static Knowledge createTravelKnowledge() {
        int dimensions = parseIntProperty("EMBEDDING_DIMENSIONS", DEFAULT_DIMENSIONS);
        EmbeddingModel embeddingModel = buildEmbeddingModel(dimensions);
        VDBStoreBase vectorStore = InMemoryStore.builder().dimensions(dimensions).build();
        Knowledge knowledge = SimpleKnowledge.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(vectorStore)
                .build();
        loadKnowledgeFromClasspath(knowledge);
        return knowledge;
    }

    /**
     * 为 ReActAgent 启用 AGENTIC 模式 RAG。
     * <p>
     * AgentScope 会自动注册 {@code retrieve_knowledge} 工具；LLM 在 ReAct 循环中按需调用，
     * 内部执行向量检索并返回文档片段。
     *
     * @param builder   已配置 name / description / model 的 Agent 构建器
     * @param knowledge 由 {@link #createTravelKnowledge()} 创建的知识库
     * @return 已挂载 sysPrompt、toolkit、knowledge、retrieveConfig 的构建器
     */
    public static ReActAgent.Builder applyAgenticRag(ReActAgent.Builder builder, Knowledge knowledge) {
        RetrieveConfig retrieveConfig = RetrieveConfig.builder()
                .limit(parseIntProperty("RAG_RETRIEVE_LIMIT", DEFAULT_RETRIEVE_LIMIT))
                .scoreThreshold(parseFloatProperty("RAG_SCORE_THRESHOLD", DEFAULT_SCORE_THRESHOLD))
                .build();

        return builder
                .sysPrompt(TRIP_PLANNER_SYS_PROMPT)
                .toolkit(new Toolkit())
                .knowledge(knowledge)
                .ragMode(RAGMode.AGENTIC)
                .retrieveConfig(retrieveConfig);
    }

    /**
     * 从 classpath 加载 Markdown 文档，分块后写入知识库。
     * <p>
     * 分块参数由 {@code RAG_CHUNK_SIZE}、{@code RAG_CHUNK_OVERLAP} 控制；
     * 单个文件失败不影响其他文件；若无任何块入库则打 WARN 日志。
     *
     * @param knowledge 目标知识库，文档将通过 {@code addDocuments} 写入
     */
    private static void loadKnowledgeFromClasspath(Knowledge knowledge) {
        ClassLoader classLoader = RagUtils.class.getClassLoader();
        if (classLoader == null) {
            log.warn("RAG: ClassLoader unavailable, skip knowledge loading.");
            return;
        }

        TextReader reader = new TextReader(
                parseIntProperty("RAG_CHUNK_SIZE", DEFAULT_CHUNK_SIZE),
                SplitStrategy.PARAGRAPH,
                parseIntProperty("RAG_CHUNK_OVERLAP", DEFAULT_CHUNK_OVERLAP)
        );

        int loadedFiles = 0;
        int loadedChunks = 0;
        for (String resourceName : KNOWLEDGE_FILES) {
            try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    log.warn("RAG: skip missing resource {}", resourceName);
                    continue;
                }
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                if (content.isBlank()) {
                    log.warn("RAG: skip empty document {}", resourceName);
                    continue;
                }
                ReaderInput input = ReaderInput.fromString(content);
                List<Document> documents = reader.read(input).block();
                if (documents == null || documents.isEmpty()) {
                    log.warn("RAG: no chunks generated for {}", resourceName);
                    continue;
                }
                knowledge.addDocuments(documents).block();
                loadedFiles++;
                loadedChunks += documents.size();
                log.info("RAG: loaded {} chunks from {}", documents.size(), resourceName);
            } catch (Exception ex) {
                log.error("RAG: failed to load {}", resourceName, ex);
            }
        }

        if (loadedChunks == 0) {
            log.warn("RAG: no knowledge chunks indexed; agent will run without retrieved context.");
        } else {
            log.info("RAG: indexed {} chunks from {} files.", loadedChunks, loadedFiles);
        }
    }

    /**
     * 根据 {@code EMBEDDING_PROVIDER} 构建 Embedding 模型。
     * <p>
     * 当前 Phase 1 支持 {@code local} / {@code deepseek}，均使用 {@link LocalHashEmbeddingModel}。
     *
     * @param dimensions 向量维度，需与 InMemoryStore 一致
     * @return Embedding 模型实例
     * @throws IllegalStateException 配置了不支持的 provider 时
     */
    private static EmbeddingModel buildEmbeddingModel(int dimensions) {
        String provider = resolveProperty(
                "EMBEDDING_PROVIDER",
                List.of("EMBEDDING_PROVIDER"),
                "local"
        ).toLowerCase();

        if ("local".equals(provider) || "deepseek".equals(provider)) {
            log.info(
                    "RAG: using local hash embedding (dimensions={}). "
                            + "DeepSeek chat API currently has no /v1/embeddings endpoint.",
                    dimensions);
            return new LocalHashEmbeddingModel(dimensions);
        }

        throw new IllegalStateException(
                "Unsupported EMBEDDING_PROVIDER: " + provider + ". Use local or deepseek for this project phase.");
    }

    /**
     * 从环境变量或 .env 读取整型配置。
     *
     * @param key          配置键名
     * @param defaultValue 未配置时的默认值
     * @return 解析后的整数值
     */
    private static int parseIntProperty(String key, int defaultValue) {
        String value = resolveOptionalProperty(List.of(key), key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.trim());
    }

    /**
     * 从环境变量或 .env 读取浮点型配置。
     *
     * @param key          配置键名
     * @param defaultValue 未配置时的默认值
     * @return 解析后的浮点值
     */
    private static float parseFloatProperty(String key, float defaultValue) {
        String value = resolveOptionalProperty(List.of(key), key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Float.parseFloat(value.trim());
    }

    /**
     * 读取配置项，支持多个别名；未找到时返回默认值。
     *
     * @param propName     .env 中的主键名
     * @param envNames     查找顺序（环境变量 + .env 属性名列表）
     * @param defaultValue 全部未配置时的默认值
     * @return 最终配置值
     */
    private static String resolveProperty(String propName, List<String> envNames, String defaultValue) {
        String value = resolveOptionalProperty(envNames, propName);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /**
     * 按优先级查找配置：先查系统环境变量，再查 classpath 下的 .env 文件。
     *
     * @param names    候选键名列表
     * @param propName .env 中的备用键名（可为 null）
     * @return 找到的值，或 null
     */
    private static String resolveOptionalProperty(List<String> names, String propName) {
        for (String envName : names) {
            String value = System.getenv(envName);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        Properties props = loadEnvProperties();
        for (String name : names) {
            String value = props.getProperty(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        if (propName != null) {
            String value = props.getProperty(propName);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 从 classpath 加载 {@code commons/src/main/resources/.env} 为 Properties。
     *
     * @return 配置属性；文件不存在时返回空 Properties
     */
    private static Properties loadEnvProperties() {
        Properties props = new Properties();
        try (InputStream in = RagUtils.class.getClassLoader().getResourceAsStream(".env")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }
        return props;
    }
}
