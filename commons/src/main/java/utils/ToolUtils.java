package utils;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientWrapper;

/**
 * Agent 工具集封装。
 * <p>
 * AgentScope 1.0.8 默认工具执行超时为 5 分钟，无需 ExecutionConfig（更高版本才提供）。
 * 用于将 Java 工具对象或 MCP 客户端注册到 {@link Toolkit} 并交给 ReActAgent 使用。
 */
public class ToolUtils {

    private final Toolkit toolkit;

    public ToolUtils() {
        toolkit = new Toolkit();
    }

    /**
     * 将带 {@code @Tool} 注解的 Java 对象注册到工具集。
     * <p>
     * 例如主管 Agent 注册 {@code RemoteAgentTool}，子 Agent 注册地图查询等工具。
     *
     * @param tool 工具实例（方法上需有 {@code @Tool} / {@code @ToolParam} 注解）
     * @return 已注册该工具的 Toolkit，可传给 ReActAgent.Builder
     */
    public Toolkit getToolkit(Object tool) {
        toolkit.registerTool(tool);
        return toolkit;
    }

    /**
     * 将 MCP（Model Context Protocol）客户端注册到工具集。
     * <p>
     * 阻塞等待 MCP 连接建立完成后再返回。
     *
     * @param mcp MCP 客户端包装器
     * @return 已注册 MCP 工具的 Toolkit
     */
    public Toolkit getToolkit(McpClientWrapper mcp) {
        toolkit.registerMcpClient(mcp).block();
        return toolkit;
    }
}
