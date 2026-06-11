package utils.rag;

import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本地哈希 Embedding，用于 DeepSeek 等暂无 Embedding API 的场景。
 * <p>
 * 实现 {@link EmbeddingModel}，通过字符 n-gram 与词哈希映射到固定维度向量，
 * 经 L2 归一化后供 {@link io.agentscope.core.rag.knowledge.SimpleKnowledge} 做余弦相似度检索。
 * 适合小规模 Markdown 知识库的 demo，语义能力弱于云端 Embedding 模型。
 */
public class LocalHashEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    /**
     * @param dimensions 向量维度，需与 {@link io.agentscope.core.rag.store.InMemoryStore} 配置一致
     * @throws IllegalArgumentException dimensions 小于等于 0 时
     */
    public LocalHashEmbeddingModel(int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.dimensions = dimensions;
    }

    /**
     * @return 向量维度数
     */
    @Override
    public int getDimensions() {
        return dimensions;
    }

    /**
     * @return 模型标识，用于日志与调试
     */
    @Override
    public String getModelName() {
        return "local-hash-embedding";
    }

    /**
     * 将 AgentScope 消息块转为向量（RAG 文档入库/检索时的标准入口）。
     *
     * @param block 通常为 {@link TextBlock}，其他类型退化为 {@code toString()}
     * @return 单条文本对应的 embedding 向量
     */
    @Override
    public Mono<double[]> embed(ContentBlock block) {
        return Mono.fromCallable(() -> toArray(extractText(block)));
    }

    /**
     * 批量文本转向量（供 AgentScope 默认接口或批量入库使用）。
     *
     * @param text 原始文本
     * @return 单条 embedding 向量
     */
    public Mono<double[]> embed(String text) {
        return Mono.fromCallable(() -> toArray(text));
    }

    /**
     * 多条文本批量转向量。
     *
     * @param texts 文本列表
     * @return 与输入顺序一致的 embedding 列表
     */
    public Mono<List<double[]>> embed(List<String> texts) {
        return Mono.fromCallable(() -> {
            List<double[]> embeddings = new ArrayList<>(texts.size());
            for (String text : texts) {
                embeddings.add(toArray(text));
            }
            return embeddings;
        });
    }

    /**
     * 从 ContentBlock 提取可嵌入的纯文本。
     *
     * @param block AgentScope 内容块
     * @return 提取到的文本；无法识别时返回空字符串或 {@code toString()}
     */
    private static String extractText(ContentBlock block) {
        if (block instanceof TextBlock textBlock) {
            return textBlock.getText();
        }
        return block != null ? block.toString() : "";
    }

    /**
     * 核心向量化：2/3-gram + 空格分词哈希，累加后 L2 归一化。
     *
     * @param text 待向量化的文本
     * @return 长度为 {@link #dimensions} 的 double 数组
     */
    private double[] toArray(String text) {
        float[] vector = new float[dimensions];
        if (text != null && !text.isBlank()) {
            String normalized = text.toLowerCase(Locale.ROOT).trim();
            addNgrams(normalized, 2, vector);
            addNgrams(normalized, 3, vector);
            for (String token : normalized.split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }
                int bucket = Math.floorMod(token.hashCode(), dimensions);
                vector[bucket] += 1.0f;
            }
            normalize(vector);
        }
        double[] result = new double[dimensions];
        for (int i = 0; i < dimensions; i++) {
            result[i] = vector[i];
        }
        return result;
    }

    /**
     * 将固定长度的字符 n-gram 哈希到向量桶并累加权重。
     *
     * @param text   已归一化的文本
     * @param size   n-gram 长度（如 2 或 3）
     * @param vector 待写入的浮点向量
     */
    private static void addNgrams(String text, int size, float[] vector) {
        if (text.length() < size) {
            int bucket = Math.floorMod(text.hashCode(), vector.length);
            vector[bucket] += 1.0f;
            return;
        }
        for (int i = 0; i <= text.length() - size; i++) {
            String gram = text.substring(i, i + size);
            int bucket = Math.floorMod(gram.hashCode(), vector.length);
            vector[bucket] += 1.0f;
        }
    }

    /**
     * 对向量做 L2 归一化，便于余弦相似度计算。
     *
     * @param vector 原地修改的向量；全零向量不做处理
     */
    private static void normalize(float[] vector) {
        double sum = 0.0d;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0.0d) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
