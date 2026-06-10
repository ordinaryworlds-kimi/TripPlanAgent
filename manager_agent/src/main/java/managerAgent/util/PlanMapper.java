package managerAgent.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.plan.model.Plan;
import io.agentscope.core.plan.model.SubTask;
import managerAgent.dto.PlanStatusDto;
import managerAgent.dto.SubtaskStatusDto;

import java.util.ArrayList;
import java.util.List;

public final class PlanMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PlanMapper() {
    }

    public static PlanStatusDto toDto(Plan plan) {
        if (plan == null) {
            return null;
        }
        PlanStatusDto dto = new PlanStatusDto();
        dto.setName(plan.getName());
        dto.setDescription(plan.getDescription());
        dto.setState(plan.getState().name());

        List<SubTask> subtasks = plan.getSubtasks();
        if (subtasks != null) {
            List<SubtaskStatusDto> items = new ArrayList<>();
            for (int i = 0; i < subtasks.size(); i++) {
                SubTask subTask = subtasks.get(i);
                SubtaskStatusDto item = new SubtaskStatusDto();
                item.setIndex(i);
                item.setName(subTask.getName());
                item.setDescription(subTask.getDescription());
                item.setState(subTask.getState().name());
                items.add(item);
            }
            dto.setSubtasks(items);
        }
        return dto;
    }

    public static String toJson(Plan plan) {
        try {
            PlanStatusDto dto = toDto(plan);
            return dto == null ? "{}" : MAPPER.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
