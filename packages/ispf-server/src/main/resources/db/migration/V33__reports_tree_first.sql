UPDATE object_nodes SET type = 'REPORTS' WHERE path = 'root.platform.reports';

UPDATE object_nodes SET type = 'REPORT' WHERE template_id = 'report-v1';

DELETE FROM object_variables
WHERE object_path IN (
    SELECT path FROM object_nodes
    WHERE path LIKE 'root.platform.applications.%.reports.%'
);

DELETE FROM object_nodes
WHERE path LIKE 'root.platform.applications.%.reports.%';

DELETE FROM object_nodes
WHERE path LIKE 'root.platform.applications.%.reports'
  AND type IN ('REPORTS', 'CUSTOM');
