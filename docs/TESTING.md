# Тестирование

## Запуск

```bash
# Все модули
./gradlew test

# Только server
./gradlew :packages:ispf-server:test

# Один класс
./gradlew :packages:ispf-server:test --tests "com.ispf.server.api.DashboardApiTest"
```

JUnit 5, Java 25, Spring Boot 4.

Backend tests use modular Boot 4 starters (`spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, `spring-boot-starter-data-jpa-test`). JSON uses Jackson 3 (`tools.jackson`); `@JsonProperty` / `@JsonCreator` stay on `com.fasterxml.jackson.annotation`.

## Профиль test

`application-test.yml`:

- H2 in-memory
- `ispf.security.rbac-enabled: false`
- MQTT/NATS disabled
- Flyway migrations applied

## Уровни тестов

### Unit (packages)

| Модуль | Примеры |
|--------|---------|
| ispf-core | `ObjectTreeTest`, `DataRecordTest` |
| ispf-expression | `BindingEvaluatorTest` (expression eval), `ExpressionEngineTest` |
| ispf-plugin-model | `ModelEngineTest` |
| ispf-plugin-workflow | `BpmnParserTest`, `WorkflowEngineV2/V3Test` |
| ispf-driver-modbus | `ModbusPointTest` |
| ispf-driver-snmp | `SnmpPointTest` |

### Integration (ispf-server)

`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`:

| Класс | Область |
|-------|---------|
| `IspfServerApplicationTests` | Context load |
| `MesPlatformApiTest` | Objects, variables, demo sensor |
| `DashboardApiTest` | Dashboard CRUD |
| `WorkflowApiTest` | BPMN save, run |
| `WorkQueueApiTest` | Claim/complete |
| `AlertRuleApiTest` | Alert rules CRUD |
| `EventCorrelatorApiTest` | Correlators CRUD |
| `ModelControllerTest` | Models API |
| `OperatorAccessTest` | RBAC operator vs admin |
| `ApplicationPlatformApiTest` | REQ-PF: applications, bundle deploy, BFF invoke |

### Сценарии demo-стенда

1. Virtual driver → temperature updates
2. Binding `alarmActive` при temp > threshold
3. `acknowledgeAlarm` function
4. Alert rule → `thresholdExceeded` event
5. Correlator → workflow run
6. User task в work queue

## Smoke (ручной)

После `bootRun --spring.profiles.active=local`:

```bash
curl http://localhost:8080/api/v1/info
curl -H "X-ISPF-Role: admin" http://localhost:8080/api/v1/objects
```

Web Console: открыть `demo-sensor` dashboard, убедиться в live-значениях.

## Web Console

```bash
cd apps/web-console
npm test        # vitest (routing, driver maturity)
npm run build   # tsc + vite build
```

TypeScript strict check в CI-equivalent локально.

## Load / throughput (prod VPS)

Сценарии HTTP и internal automation load test, baseline метрики и dashboard probe — **[LOAD_TESTING.md](LOAD_TESTING.md)**.

```bash
python deploy/events-load-test.py --base-url https://ispf.iot-solutions.ru
python deploy/events-internal-load-test.py --skip-monitor-setup --condition-expr-file deploy/loadtest-sinewave-condition.txt
```

## CI (рекомендация)

```bash
./gradlew test
cd apps/web-console && npm ci && npm test && npm run build
```

## App bundle smoke (вне `main`)

E2E smoke конкретного приложения выполняется в репозитории app bundle, не в framework `main`.
