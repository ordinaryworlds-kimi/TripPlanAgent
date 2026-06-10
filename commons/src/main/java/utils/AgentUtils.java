package utils;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.OpenAIChatModel;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

/**
 * Agent 公共工具：创建 ReActAgent、配置 LLM、读取 .env 配置。
 * <p>
 * 通过 {@code commons/src/main/resources/.env} 中的 {@code LLM_PROVIDER}
 * 在 DashScope（通义）与 DeepSeek 之间切换，所有 Agent 模块共用此配置。
 */
public class AgentUtils {

    private static final String DEFAULT_DASHSCOPE_MODEL = "qwen-plus";
    private static final String DEFAULT_DEEPSEEK_MODEL = "deepseek-chat";
    private static final String DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com";

    /**
     * 创建 ReActAgent 构建器，已绑定当前环境配置的 LLM 模型。
     *
     * @param name        Agent 名称，用于日志与 Nacos 注册
     * @param description Agent 能力描述，写入 Agent Card
     * @return 可继续链式配置 toolkit、planNotebook、hook 等的 Builder
     */
    public static ReActAgent.Builder getReActAgentBuilder(String name, String description) {
        return ReActAgent.builder()
                .name(name)
                .description(description)
                .model(buildChatModel());
    }

    /**
     * 以流式方式向 Agent 发送单轮用户消息。
     *
     * @param agent  目标 Agent 实例
     * @param prompt 用户输入文本
     * @return 事件流，每个 {@link Event} 可能包含思考、工具调用或回复片段
     */
    public static Flux<Event> streamResponse(AgentBase agent, String prompt) {
        return agent.stream(
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(List.of(
                                TextBlock.builder()
                                        .text(prompt)
                                        .build()
                        ))
                        .build()
        );
    }

    /**
     * 根据 {@code LLM_PROVIDER} 选择并构建对应的聊天模型。
     *
     * @return DashScope 或 DeepSeek 模型实例
     * @throws IllegalStateException 当 provider 不是 dashscope / deepseek 时
     */
    private static ChatModelBase buildChatModel() {
        String provider = resolveProperty(
                "LLM_PROVIDER",
                List.of("LLM_PROVIDER"),
                "dashscope"
        ).toLowerCase();

        return switch (provider) {
            case "deepseek" -> buildDeepSeekModel();
            case "dashscope" -> buildDashScopeModel();
            default -> throw new IllegalStateException(
                    "Unsupported LLM_PROVIDER: " + provider + ". Use dashscope or deepseek.");
        };
    }

    /**
     * 构建阿里云 DashScope（通义千问）模型，默认开启流式输出。
     */
    private static ChatModelBase buildDashScopeModel() {
        String modelName = resolveProperty(
                "DASHSCOPE_MODEL_NAME",
                List.of("DASHSCOPE_MODEL_NAME"),
                DEFAULT_DASHSCOPE_MODEL
        );
        return DashScopeChatModel.builder()
                .apiKey(resolveDashScopeApiKey())
                .modelName(modelName)
                .stream(true)
                .build();
    }

    /**
     * 构建 DeepSeek 模型（OpenAI 兼容接口）。
     * <p>
     * 流式由 {@code DEEPSEEK_STREAM} 控制，子 Agent 经 A2A 调用时建议设为 false，
     * 避免 HTTP chunked 传输与 LLM 流式响应冲突。
     */
    private static ChatModelBase buildDeepSeekModel() {
        String modelName = resolveProperty(
                "DEEPSEEK_MODEL_NAME",
                List.of("DEEPSEEK_MODEL_NAME"),
                DEFAULT_DEEPSEEK_MODEL
        );
        String baseUrl = resolveProperty(
                "DEEPSEEK_BASE_URL",
                List.of("DEEPSEEK_BASE_URL"),
                DEFAULT_DEEPSEEK_BASE_URL
        );
        boolean stream = Boolean.parseBoolean(resolveProperty(
                "DEEPSEEK_STREAM",
                List.of("DEEPSEEK_STREAM"),
                "false"
        ));
        return OpenAIChatModel.builder()
                .apiKey(resolveDeepSeekApiKey())
                .modelName(modelName)
                .baseUrl(baseUrl)
                .stream(stream)
                .build();
    }

    /** 读取 DashScope API Key，环境变量优先于 .env 文件 */
    private static String resolveDashScopeApiKey() {
        return resolveRequiredProperty(
                List.of(
                        "ALIBABA_DASHSCOPE_API_KEY",
                        "ALIBABA_DASHCOPE_KEY",
                        "DASHSCOPE_API_KEY"),
                "DashScope API key not configured. Set ALIBABA_DASHSCOPE_API_KEY in "
                        + "commons/src/main/resources/.env or as an environment variable.");
    }

    /** 读取 DeepSeek API Key，环境变量优先于 .env 文件 */
    private static String resolveDeepSeekApiKey() {
        return resolveRequiredProperty(
                List.of("DEEPSEEK_API_KEY"),
                "DeepSeek API key not configured. Set DEEPSEEK_API_KEY in "
                        + "commons/src/main/resources/.env or as an environment variable.");
    }

    /**
     * 读取百度地图 API Key，供路线规划等工具使用。
     *
     * @return 有效的 API Key
     * @throws IllegalStateException 未配置时抛出
     */
    public static String resolveBaiduMapApiKey() {
        return resolveRequiredProperty(
                List.of("BAIDU_MAP_KEY"),
                "Baidu Map API key not configured. Set BAIDU_MAP_KEY in "
                        + "commons/src/main/resources/.env or as an environment variable.");
    }

    /**
     * 读取配置项，支持多个别名；未找到时返回默认值。
     *
     * @param propName     .env 中的主键名
     * @param envNames     查找顺序（环境变量 + .env 属性名列表）
     * @param defaultValue 全部未配置时的默认值
     */
    private static String resolveProperty(String propName, List<String> envNames, String defaultValue) {
        String value = resolveOptionalProperty(envNames, propName);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /**
     * 读取必填配置项，未找到时抛出异常。
     *
     * @param names        查找顺序（环境变量 + .env 属性名列表）
     * @param errorMessage 缺失时的错误提示
     */
    private static String resolveRequiredProperty(List<String> names, String errorMessage) {
        String value = resolveOptionalProperty(names, names.get(0));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(errorMessage);
        }
        return value.trim();
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

    /** 从 classpath 加载 {@code commons/src/main/resources/.env} 为 Properties */
    private static Properties loadEnvProperties() {
        Properties props = new Properties();
        try (InputStream in = AgentUtils.class.getClassLoader().getResourceAsStream(".env")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }
        return props;
    }
}
