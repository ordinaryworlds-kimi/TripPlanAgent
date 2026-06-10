package managerAgent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 旅游计划快照 DTO。
 * <p>
 * 由 {@link managerAgent.util.PlanMapper} 从 AgentScope 的 {@code Plan} 对象转换而来，
 * 通过 SSE {@code plan} 事件推送给前端，并序列化存入会话历史。
 * <p>
 * {@code state} 常见取值：{@code NEEDS_CONFIRM}（待用户确认）、
 * {@code IN_PROGRESS}（执行中）、{@code DONE}（已完成）、{@code ABANDONED}（已放弃）。
 */
@Data
public class PlanStatusDto {

    /** 计划名称，如「深圳到惠州3日游」 */
    private String name;

    /** 计划整体描述或目标说明 */
    private String description;

    /** 计划当前状态，对应 AgentScope {@code PlanState} 枚举名 */
    private String state;

    /** 子任务列表，按执行顺序排列；默认为空列表而非 null */
    private List<SubtaskStatusDto> subtasks = new ArrayList<>();
}
