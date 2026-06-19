# Уведомления о стороннем ПО (Third-Party Notices)

Дистрибутив ISPF включает компоненты с лицензиями, отличными от Apache 2.0 ядра.  
При распространении бинарных сборок сохраняйте [LICENSE](../LICENSE), [NOTICE](../NOTICE) и этот файл.

## Backend (Java / Gradle)

| Компонент | Лицензия | Примечание |
|-----------|----------|------------|
| Spring Boot, Spring Framework | Apache License 2.0 | NOTICE в JAR зависимостей |
| PostgreSQL JDBC Driver | BSD-2-Clause | Runtime |
| H2 Database | MPL 2.0 / EPL 1.0 (dual) | Тесты и профиль `local` |
| Flyway | Apache License 2.0 | |
| SNMP4J | Apache License 2.0 | Драйвер SNMP |
| j2mod (Modbus) | Apache License 2.0 | Транзитивные зависимости — проверять SBOM |
| Eclipse Paho MQTT | EPL 2.0 / EDL 1.0 | Драйвер MQTT |
| Google CEL (`dev.cel:cel`) | Apache License 2.0 | |
| JNATS | Apache License 2.0 | Опционально |

Полный список версий: `./gradlew :packages:ispf-server:dependencies` и lockfiles Maven.

## Web Console (npm)

| Компонент | Лицензия | Примечание |
|-----------|----------|------------|
| React, Vite, TanStack Query | MIT | |
| **bpmn-js** | bpmn.io License (MIT + условие) | **Watermark bpmn.io обязателен** на диаграммах — [bpmn.io/license](https://bpmn.io/license/) |
| **bpmn-auto-layout** | MIT | |
| recharts | MIT | |

Полный список: `apps/web-console/package-lock.json`.

## bpmn-js — дополнительное условие

Из лицензии bpmn-js (Camunda Services GmbH):

> The source code responsible for displaying the bpmn.io project watermark … MUST NOT be removed or changed. When this software is being used in a website or application, the watermark must stay fully visible and not visually overlapped by other elements.

Нарушение условия прекращает право использования bpmn-js.

## Инфраструктура (Docker Compose)

Образы PostgreSQL, Redis, NATS, Mosquitto, Keycloak — лицензии соответствующих проектов; не входят в исходники ISPF.

## Генерация SBOM (рекомендация)

```bash
./gradlew :packages:ispf-server:dependencies --configuration runtimeClasspath
cd apps/web-console && npm ls --all
```

Для production-сборок формируйте полный SBOM и прикладывайте к релизу.
