package io.coredge.health.claims.dto.operate;

import lombok.Data;
import java.util.List;
@Data
public class OperateProcessInstance {

    private Long id;
    private String processId;
    private String processName;
    private Integer processVersion;
    private String processVersionTag;

    private String bpmnProcessId;

    private String startDate;
    private String endDate;

    private String state;

    private boolean hasActiveOperation;

    private List<OperateOperation> operations;

    private String parentInstanceId;
    private String rootInstanceId;

    private Object callHierarchy;

    private String tenantId;

    private List<SortValue> sortValues;

    private List<String> permissions;


    @Data
    public static class OperateOperation {
        private String id;
        private String type;
        private String state;
        private String errorMessage;
    }


    @Data
    public static class SortValue {
        private Object value;
        private String valueType;
    }
}



