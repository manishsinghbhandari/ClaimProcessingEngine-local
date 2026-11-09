package io.coredge.health.claims.dto.operate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperateProcessInstanceResponse {

    private List<OperateProcessInstance> processInstances;
    private long totalCount;

}
