# Уведомления о стороннем ПО (Third-Party Notices)

Дистрибутив ISPF включает сторонние компоненты с лицензиями, отличными от лицензии кода ISPF. При распространении исходных или бинарных сборок сохраняйте [LICENSE](../LICENSE), [NOTICE](../NOTICE), этот файл и license/notice-файлы самих зависимостей.

Этот документ является инженерной инвентаризацией, а не юридическим заключением. Для публичного релиза или коммерческой поставки формируйте SBOM и проводите юридическую проверку.

## Политика лицензирования ISPF

Код платформы в репозитории лицензируется под **GNU AGPL-3.0** ([ADR-0016](decisions/0016-agpl-dual-licensing.md)). Сторонние библиотеки сохраняют свои лицензии.

Текущее состояние поставки:

- **Платформа (`ispf-server`, `web-console`, core packages):** AGPL-3.0; Enterprise — optional `platform-license.json`.
- **Device drivers:** только **driver packs** в `${ISPF_DRIVER_PACKS_DIR}`; каждый pack — свой `LICENSE` и `licenseType`.
- **Web console spreadsheet:** встроенный MIT/AGPL движок формул (`ispfSheetEval`); HyperFormula **не используется**.
- **Copyleft driver packs** (BACnet, DLMS, IEC-104, …): отдельные packs с GPL/LGPL notices — не часть `ispf-server.jar`.
- **Application bundles:** declarative JSON + EULA заказчика; не «исходники платформы» по AGPL.

## License Risk Register

| Компонент | Где используется | Лицензия | Риск / действие |
|-----------|------------------|----------|-----------------|
| `hyperformula` | *(removed)* | — | Replaced by `ispfSheetEval` in web-console (2026-06). |
| `exceljs` | `apps/web-console` (spreadsheet XLSX import/export) | **MIT** | Lazy-loaded; spreadsheet widget only. |
| `com.infiniteautomation:bacnet4j` | `packages/ispf-driver-bacnet` (driver pack) | **GPL-3.0** | Ship only in `ispf-driver-bacnet` pack with GPL notice; optional RSA license block. |
| `org.gurux:gurux.dlms`, `org.gurux:gurux.net` | `packages/ispf-driver-dlms` (driver pack) | **GPL-2.0** | Ship only in DLMS driver pack. |
| `org.openmuc:j60870` | `packages/ispf-driver-iec104*` (driver packs) | **GPL** | Ship only in IEC-104 driver packs. |
| `org.tinyradius:tinyradius` | `packages/ispf-driver-radius` | LGPL | Можно использовать при соблюдении LGPL, но для appliance/closed поставки проверить условия замены/линковки. |
| `com.fazecast:jSerialComm` | `packages/ispf-driver-modem-at` | Apache-2.0 / LGPL-3.0 dual | Использовать Apache-2.0 вариант и зафиксировать это в SBOM. |
| `org.openmuc:jmbus` | `packages/ispf-driver-mbus` | MPL-2.0 | File-level copyleft. Совместимо с коммерческой поставкой при соблюдении MPL notice/source obligations. |
| `bpmn-js` | `apps/web-console`, BPMN editor/viewer | bpmn.io license (MIT-like + watermark condition) | Watermark bpmn.io должен оставаться видимым. |
| `@mapbox/jsonlint-lines-primitives` | transitive npm runtime dependency | UNKNOWN in lockfile | Проверить upstream license перед релизом. |
| `fr.jrds:vxIPMI`, `javax.sip:jain-sip-ri` | IPMI/SIP drivers | UNKNOWN from local POM cache | Проверить upstream license перед релизом. |

## Рекомендация по лицензии проекта

Модель ISPF (см. [LICENSE.md](LICENSE.md), ADR-0016):

1. **Платформа** — AGPL-3.0; network use triggers source-offer obligations unless Enterprise EULA applies.
2. **Drivers** — pack-only runtime; copyleft deps isolated per pack with pack `LICENSE`.
3. **Web console** — AGPL; spreadsheet uses built-in `ispfSheetEval` (no GPL third-party formula engine).
4. **Commercial/closed сценарий:** Enterprise platform license + per-pack RSA licenses where required; application bundles under separate EULA.

## Backend (Java / Gradle)

Источник инвентаризации: `packages/**/build.gradle.kts`, `:packages:ispf-server:runtimeClasspath`, Maven POM license metadata из локального Gradle cache.

### Core, Expression, AI

| Модуль | Компонент | Лицензия | Примечание |
|--------|-----------|----------|------------|
| `packages/ispf-core` | `com.fasterxml.jackson.core:jackson-annotations:2.21` | Apache-2.0 | Runtime |
| `packages/ispf-expression` | `dev.cel:cel:0.5.1` | Apache-2.0 | Runtime |
| `packages/ispf-ai-api` | `com.fasterxml.jackson.core:jackson-databind:2.21.0` | Apache-2.0 | Runtime |
| `packages/ispf-ai-openai-compatible` | `com.fasterxml.jackson.core:jackson-databind:2.21.0` | Apache-2.0 | Runtime |
| `packages/ispf-ai-ollama` | `com.fasterxml.jackson.core:jackson-databind:2.21.0` | Apache-2.0 | Runtime |

### Server

| Компонент | Версия | Лицензия | Примечание |
|-----------|--------|----------|------------|
| Spring Boot / Spring Framework starters | 4.0.7 | Apache-2.0 | Web, WebSocket, Validation, Actuator, JPA, Redis, Cache, Security, OAuth2 Resource Server, Flyway starter |
| Spring Security | 7.0.6 | Apache-2.0 | Transitive via OAuth2 resource server |
| PostgreSQL JDBC Driver | 42.7.11 | BSD-2-Clause | Runtime |
| H2 Database | 2.4.240 | MPL-2.0 / EPL-1.0 dual | Local/test runtime |
| Flyway Core / PostgreSQL support | 11.14.1 | Apache-2.0 | Migrations |
| Micrometer metrics/tracing | 1.16.6 / 1.6.6 | Apache-2.0 | Prometheus, OTLP, tracing bridge |
| OpenTelemetry Java APIs/SDK (transitive) | via Micrometer | Apache-2.0 | Observability |
| JNATS | 2.20.5 | Apache-2.0 | NATS integration |
| YARG | 2.2.22 | Apache-2.0 | Report export |
| Apache POI (transitive via YARG) | managed by YARG | Apache-2.0 | Office export/templates |
| docx4j | 8.3.11 | Apache-2.0 | Document export support |
| JAXB API/runtime | 2.3.1 / 2.3.9 | CDDL/GPL with classpath exception / EDL-style variants; verify exact artifacts in SBOM | XML binding |

### Protocol Drivers

| Модуль | Компонент | Лицензия | Риск / примечание |
|--------|-----------|----------|-------------------|
| `ispf-driver-mqtt` | Eclipse Paho MQTT v3 `1.2.5` | EPL / EDL | Eclipse notice required |
| `ispf-driver-modbus`, `ispf-driver-modbus-rtu`, `ispf-driver-modbus-udp` | `com.ghgande:j2mod:3.2.1` | Apache-2.0 | |
| `ispf-driver-snmp` | `org.snmp4j:snmp4j:3.9.0` | Apache-2.0 | |
| `ispf-driver-coap` | Eclipse Californium `3.12.0` | EPL/EDL (verify exact POM in SBOM) | Eclipse notice required |
| `ispf-driver-opcua`, `ispf-driver-opcua-server` | Eclipse Milo `0.6.15` | EPL-2.0 (verify exact POM in SBOM) | Eclipse notice required |
| `ispf-driver-s7` | `com.github.s7connector:s7connector:2.1` | Apache-2.0 | |
| `ispf-driver-iec104`, `ispf-driver-iec104-server` | `org.openmuc:j60870:1.7.2` | GPL | **Copyleft risk** |
| `ispf-driver-bacnet` | `com.infiniteautomation:bacnet4j:6.0.0` | GPL-3.0 | **Copyleft risk** |
| `ispf-driver-dlms` | `org.gurux:gurux.dlms:4.0.79`, `org.gurux:gurux.net:1.0.30` | GPL-2.0 | **Copyleft risk** |
| `ispf-driver-jms` | Apache ActiveMQ Client `6.1.8` | Apache-2.0 | Version resolved by dependency management |
| `ispf-driver-kafka` | Apache Kafka Clients `4.1.2` | Apache-2.0 | Declared `3.8.1`, resolved to `4.1.2` |
| `ispf-driver-imap`, `ispf-driver-pop3` | Eclipse Angus Mail `2.0.3` | EPL-2.0 / GPL with classpath exception (verify exact artifact) | Eclipse notice required |
| `ispf-driver-telnet` | Apache Commons Net `3.11.1` | Apache-2.0 | |
| `ispf-driver-smb` | SMBJ `0.13.0` | Apache-2.0 | |
| `ispf-driver-modem-at` | jSerialComm `2.11.0` | Apache-2.0 / LGPL-3.0 dual | Use Apache-2.0 option |
| `ispf-driver-ldap` | UnboundID LDAP SDK `7.0.1` | Apache-2.0 / GPLv2 / LGPLv2.1 / Free Use License | Use Apache/free-use compatible option; verify for redistribution |
| `ispf-driver-graph-db` | Neo4j Java Driver `5.26.0` | Apache-2.0 (verify exact POM in SBOM) | |
| `ispf-driver-xmpp` | Smack `4.4.8` (`smack-tcp`, `smack-im`, `smack-extensions`) | Apache-2.0 | |
| `ispf-driver-smpp` | JSMPP `3.0.1` | Apache-2.0 | |
| `ispf-driver-radius` | TinyRadius `1.1.3` | LGPL | LGPL obligations |
| `ispf-driver-mbus` | OpenMUC jMBus `3.3.0` | MPL-2.0 | MPL file-level copyleft |
| `ispf-driver-ssh` | mwiede JSch `0.2.21` | BSD / ISC | |
| `ispf-driver-ipmi` | vxIPMI `2.0.0.1` | UNKNOWN from local POM cache | Must verify before release |
| `ispf-driver-sip` | JAIN SIP RI `1.3.0-91` | UNKNOWN from local POM cache | Must verify before release |

### In-tree driver modules without direct runtime third-party libraries

Следующие модули сейчас не объявляют прямых runtime third-party зависимостей в `build.gradle.kts` сверх ISPF project modules и test libraries: `ispf-driver-api`, `ispf-driver-virtual`, `ispf-driver-http`, `ispf-driver-icmp`, `ispf-driver-jdbc`, `ispf-driver-file`, `ispf-driver-folder`, `ispf-driver-application`, `ispf-driver-message-stream`, `ispf-driver-nmea`, `ispf-driver-soap`, `ispf-driver-ip-host`, `ispf-driver-gps-tracker`, `ispf-driver-flexible`, `ispf-driver-dnp3`, `ispf-driver-ethernet-ip`, `ispf-driver-omron-fins`, `ispf-driver-asterisk`, `ispf-driver-corba`, `ispf-driver-opc-da`, `ispf-driver-opc-bridge`, `ispf-driver-odbc`, `ispf-driver-cwmp`, `ispf-driver-web-transaction`, `ispf-driver-http-server`, `ispf-driver-vmware`, `ispf-driver-smis`, `ispf-driver-dhcp`, `ispf-driver-wmi`.

### Test-only Java dependencies

| Компонент | Лицензия | Примечание |
|-----------|----------|------------|
| JUnit Jupiter / JUnit Platform | EPL-2.0 | Test only |
| AssertJ | Apache-2.0 | Test only |
| Mockito | MIT | Test only; verify exact resolved artifact in SBOM |
| Spring Boot test starters | Apache-2.0 | Test only |

### Build-time Gradle plugins

| Компонент | Лицензия | Примечание |
|-----------|----------|------------|
| Gradle | Apache-2.0 | Build tool |
| `org.springframework.boot` Gradle plugin | Apache-2.0 | Build only |
| `io.spring.dependency-management` Gradle plugin | Apache-2.0 | Build only |
| `org.gradle.toolchains.foojay-resolver-convention` | Apache-2.0 (verify in SBOM) | Build only |

## Web Console (npm)

Источник инвентаризации: `apps/web-console/package.json`, `apps/web-console/package-lock.json`.

### Direct runtime dependencies

| Компонент | Версия в lockfile | Лицензия | Примечание |
|-----------|-------------------|----------|------------|
| `@tanstack/react-query` | 5.101.0 | MIT | |
| `@tanstack/react-virtual` | 3.14.3 | MIT | |
| `bpmn-auto-layout` | 1.3.0 | MIT | |
| `bpmn-js` | 18.18.0 | bpmn.io license | Watermark condition; see below |
| `i18next` | 26.3.1 | MIT | |
| `maplibre-gl` | 5.24.0 | BSD-3-Clause | |
| `react` | 19.2.7 | MIT | |
| `react-dom` | 19.2.7 | MIT | |
| `react-i18next` | 17.0.8 | MIT | |
| `react-map-gl` | 8.1.1 | MIT | |
| `react-router-dom` | 7.18.0 | MIT | |
| `recharts` | 3.8.1 | MIT | |

### Runtime transitive license summary

| Лицензия | Количество пакетов |
|----------|--------------------|
| MIT | 95 |
| ISC | 22 |
| BSD-3-Clause | 6 |
| BSD-2-Clause | 3 |
| Apache-2.0 | 3 |
| MIT OR Apache-2.0 | 1 |
| MIT AND ISC | 1 |
| SEE LICENSE IN LICENSE | 1 |
| UNKNOWN | 1 |

Special npm packages:

| Компонент | Лицензия | Примечание |
|-----------|----------|------------|
| `bpmn-js` | bpmn.io license | Requires visible bpmn.io watermark |
| `@mapbox/jsonlint-lines-primitives` | UNKNOWN in package-lock | Verify upstream license before release |

### Dev-only npm dependencies

| Компонент | Лицензия | Примечание |
|-----------|----------|------------|
| Vite / `@vitejs/plugin-react` | MIT | Build only |
| TypeScript | Apache-2.0 | Build only |
| Vitest | MIT | Test only |
| React type packages | MIT | Build only |

## bpmn-js — дополнительное условие

Из лицензии bpmn-js (Camunda Services GmbH):

> The source code responsible for displaying the bpmn.io project watermark ... MUST NOT be removed or changed. When this software is being used in a website or application, the watermark must stay fully visible and not visually overlapped by other elements.

Нарушение условия прекращает право использования bpmn-js.

## Инфраструктура (Docker Compose / VPS)

Образы PostgreSQL, Redis, NATS, Mosquitto, Keycloak, ClickHouse и другие инфраструктурные контейнеры распространяются по лицензиям соответствующих проектов и не являются исходным кодом ISPF. При поставке appliance/VM image их лицензии также должны войти в SBOM/notice bundle.

## Генерация SBOM перед релизом

Минимальные команды для проверки:

```bash
./gradlew :packages:ispf-server:dependencies --configuration runtimeClasspath
./gradlew :packages:ispf-server:dependencies --configuration testRuntimeClasspath
cd apps/web-console && npm ls --all
```

Рекомендуемый релизный процесс:

1. Сформировать CycloneDX/SPDX SBOM для Gradle и npm.
2. Проверить все `UNKNOWN` licenses вручную.
3. Удалить/заменить GPL-компоненты из Apache/core профиля или явно маркировать поставку как copyleft/GPL-containing.
4. Приложить `LICENSE`, `NOTICE`, `THIRD_PARTY_NOTICES.md`, npm/Java SBOM и upstream license files к binary distribution.
