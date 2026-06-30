package com.ispf.server.ai.agent;

import java.util.Locale;
import java.util.Map;

public final class AgentStepHumanizer {

    private AgentStepHumanizer() {
    }

    public static String label(
            String type,
            String tool,
            Map<String, Object> arguments,
            Map<String, Object> result,
            String summary
    ) {
        if ("finish".equalsIgnoreCase(type)) {
            return summary != null && !summary.isBlank()
                    ? summary
                    : "Задача выполнена.";
        }
        if (tool == null) {
            return "Шаг агента";
        }
        return switch (tool) {
            case "search_context" -> "Ищу в документации: «" + arg(arguments, "query") + "»";
            case "list_objects" -> "Смотрю содержимое «" + orDefault(arg(arguments, "parent"), "root") + "»";
            case "get_object" -> "Открываю объект «" + arg(arguments, "path") + "»";
            case "create_object" -> "Создаю " + arg(arguments, "type") + " «" + arg(arguments, "name") + "»";
            case "delete_object" -> "Удаляю объект «" + arg(arguments, "path") + "»";
            case "get_dashboard_layout" -> "Читаю layout дашборда «" + orDefault(arg(arguments, "path"), arg(arguments, "template")) + "»";
            case "set_dashboard_layout" -> "Обновляю layout «" + arg(arguments, "path") + "»";
            case "add_dashboard_widget" -> "Добавляю виджет на «" + arg(arguments, "path") + "»";
            case "get_widget_catalog" -> widgetCatalogLabel(arguments);
            case "get_automation_schema" -> "Справочник: " + orDefault(arg(arguments, "topic"), "all");
            case "list_variables" -> "Читаю переменные «" + arg(arguments, "path") + "»";
            case "set_variable" -> "Обновляю «" + arg(arguments, "name") + "» на «" + arg(arguments, "path") + "»";
            case "configure_driver" -> "Настраиваю драйвер на «" + arg(arguments, "devicePath") + "»";
            case "driver_control" -> driverControlLabel(arguments, result);
            case "save_mimic_diagram" -> "Сохраняю элементы mimic «" + arg(arguments, "path") + "»";
            case "add_mimic_elements" -> "Добавляю символы на mimic «" + arg(arguments, "path") + "»";
            case "list_mimic_symbols" -> "Справочник SCADA-символов";
            case "get_workflow" -> "Читаю workflow «" + arg(arguments, "path") + "»";
            case "save_workflow_bpmn" -> "Сохраняю BPMN «" + arg(arguments, "path") + "»";
            case "run_workflow" -> "Запускаю workflow «" + arg(arguments, "path") + "»";
            case "configure_platform_context_rule" -> "Правило dashboard «" + arg(arguments, "path") + "»";
            case "configure_platform_schedule" -> "Настраиваю расписание «" + orDefault(arg(arguments, "scheduleId"), arg(arguments, "path")) + "»";
            case "deploy_tree_function" -> "Деплою функцию «" + arg(arguments, "functionName") + "» на «" + arg(arguments, "path") + "»";
            case "get_function_template" -> "Шаблон функции: " + orDefault(arg(arguments, "topic"), "comparison");
            case "validate_bundle" -> "Проверяю bundle «" + arg(arguments, "appId") + "»";
            case "dry_run_deploy" -> "Dry-run деплоя «" + arg(arguments, "appId") + "»";
            case "import_package" -> "Импортирую пакет «" + orDefault(arg(arguments, "packageId"), arg(arguments, "appId")) + "»";
            case "get_variable_history" -> "История «" + arg(arguments, "name") + "» на «" + arg(arguments, "path") + "»";
            case "get_variable_trend" -> "Тренд «" + arg(arguments, "name") + "» на «" + arg(arguments, "path") + "»";
            case "list_work_queue" -> "Открытые задачи оператора";
            case "list_app_memory" -> "Память приложения" + memoryQuery(arguments);
            case "remember_app_memory" -> "Запоминаю для приложения";
            case "run_report" -> "Отчёт «" + arg(arguments, "path") + "»";
            case "get_mimic_diagram" -> "Читаю схему mimic «" + arg(arguments, "path") + "»";
            case "list_events" -> "События «" + orDefault(arg(arguments, "objectPath"), "платформа") + "»";
            default -> "Вызов " + tool;
        };
    }

    private static String memoryQuery(Map<String, Object> arguments) {
        String query = arg(arguments, "query");
        return query.isBlank() ? "" : " («" + query + "»)";
    }

    private static String widgetCatalogLabel(Map<String, Object> arguments) {
        String type = arg(arguments, "type");
        String binding = arg(arguments, "binding");
        if (!type.isBlank()) {
            return "Справочник виджетов: тип «" + type + "»";
        }
        if (!binding.isBlank()) {
            return "Справочник виджетов: привязка «" + binding + "»";
        }
        return "Справочник всех виджетов дашборда";
    }

    private static String driverControlLabel(Map<String, Object> arguments, Map<String, Object> result) {
        String action = arg(arguments, "action").toLowerCase(Locale.ROOT);
        String path = arg(arguments, "devicePath");
        String status = result != null ? String.valueOf(result.getOrDefault("connected", "")) : "";
        return switch (action) {
            case "start" -> "Запускаю опрос драйвера «" + path + "»";
            case "stop" -> "Останавливаю драйвер «" + path + "»";
            case "poll" -> "Запрашиваю мгновенный poll «" + path + "»";
            default -> "Статус драйвера «" + path + "»" + (status.isBlank() ? "" : " (connected=" + status + ")");
        };
    }

    private static String arg(Map<String, Object> args, String key) {
        if (args == null) {
            return "";
        }
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String orDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
