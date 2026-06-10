package managerAgent.dto;

import lombok.Data;

/**
 * 计划子任务状态 DTO。
 * <p>
 * 主管 Agent 将旅游规划拆分为多个子任务（如路线制定、行程规划），
 * 每个子任务对应一条记录，供前端 {@code PlanPanel} 展示进度。
 */
@Data
public class SubtaskStatusDto {

    /** 子任务在计划中的序号，从 0 开始 */
    private int index;

    /** 子任务名称，如「路线制定」「景点行程规划」 */
    private String name;

    /** 子任务详细描述，包含出发地、目的地、偏好等上下文 */
    private String description;

    /**
     * 子任务状态，对应 AgentScope {@code SubTaskState} 枚举名。
     * 常见值：{@code TODO}、{@code IN_PROGRESS}、{@code DONE}、{@code ABANDONED}
     */
    private String state;
}
