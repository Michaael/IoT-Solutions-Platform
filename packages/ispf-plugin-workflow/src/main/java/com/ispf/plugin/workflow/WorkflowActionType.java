package com.ispf.plugin.workflow;

public enum WorkflowActionType {
    LOG,
    SET_VARIABLE,
    PUBLISH_NATS,
    INVOKE_FUNCTION,
    FIRE_EVENT,
    READ_VARIABLE,
    START_WORKFLOW
}
