package com.ispf.core.object;

/**
 * Classification of object nodes in the resource tree.
 */
public enum ObjectType {
    ROOT,
    TENANT,
    USER,
    /** Platform root node ({@code root.platform}). */
    PLATFORM,
    /** Device catalog folder. */
    DEVICES,
    DEVICE,
    DRIVER,
    MODEL,
    /** Dashboard catalog folder. */
    DASHBOARDS,
    DASHBOARD,
    /** SCADA mimic catalog folder. */
    MIMICS,
    MIMIC,
    /** Workflow catalog folder. */
    WORKFLOWS,
    WORKFLOW,
    /** Alert rule catalog folder. */
    ALERT_RULES,
    ALERT,
    /** Correlator catalog folder. */
    CORRELATORS,
    CORRELATOR,
    /** Application catalog folder. */
    APPLICATIONS,
    APPLICATION,
    /** Operator HMI apps folder. */
    OPERATOR_APPS,
    /** Security & RBAC root folder. */
    SECURITY,
    /** User accounts folder. */
    USERS,
    /** Roles folder. */
    ROLES,
    /** Platform role definition. */
    ROLE,
    /** SQL data source catalog folder. */
    DATA_SOURCES,
    /** JDBC schema reference for reports, bindings, script SQL. */
    DATA_SOURCE,
    /** Application reports folder. */
    REPORTS,
    REPORT,
    /** Application functions folder. */
    FUNCTIONS,
    FUNCTION,
    /** Application schedules folder. */
    SCHEDULES,
    SCHEDULE,
    /** Application SQL bindings folder. */
    BINDINGS,
    BINDING,
    /** Application migrations folder. */
    MIGRATIONS,
    MIGRATION,
    /** Operator screens folder. */
    SCREENS,
    SCREEN,
    AGENT,
    /** Visual-only grouping node; members stored in {@code @groupMembers}. */
    VISUAL_GROUP,
    CUSTOM
}
