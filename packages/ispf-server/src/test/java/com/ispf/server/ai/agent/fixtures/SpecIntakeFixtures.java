package com.ispf.server.ai.agent.fixtures;

/**
 * Reference assignment fixtures for SpecIntakeScenarioTest (not production hardcode).
 */
public final class SpecIntakeFixtures {

    public static final String PUMP_STATION_TZ_EXCERPT = """
            ТЕХНИЧЕСКОЕ ЗАДАНИЕ на создание системы цифрового двойника насосной станции.
            Приложение B: перечень объектов — ЗД-01 (задвижка), НМ-1 (насос), СИКН-01 (расходомер),
            РВС-01 (резервуар). Функциональные требования: FR-1 realtime telemetry, FR-2 SCADA HMI L2,
            FR-3 LSTM прогноз (machine learning), FR-4 operator UI, FR-5 KPI ОПЭ.
            """;

    public static final String SNMP_PROMPT = "Подключи SNMP localhost и создай dashboard мониторинга";

    public static final String MES_BUNDLE_PROMPT = "Разверни MES demo mes-reference и покажи orders";

    private SpecIntakeFixtures() {
    }
}
