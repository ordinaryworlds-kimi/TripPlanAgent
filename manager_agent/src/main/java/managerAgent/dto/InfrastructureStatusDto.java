package managerAgent.dto;

import lombok.Data;

/**
 * 基础设施连通状态 DTO。
 * <p>
 * 由 {@code GET /api/status} 返回，前端 {@code InfraStatus} 组件据此展示
 * Nacos 注册中心与各子 Agent 是否在线（绿色/红色指示）。
 */
@Data
public class InfrastructureStatusDto {

    /** Nacos 注册中心（默认 localhost:8848）是否可连接 */
    private boolean nacos;

    /** 路线制定子 Agent（RouteMakingAgent，端口 8082）是否可连接 */
    private boolean routeMakingAgent;

    /** 行程规划子 Agent（TripPlannerAgent，端口 8085）是否可连接 */
    private boolean tripPlannerAgent;
}
