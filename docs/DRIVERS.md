# Драйверы устройств

Каталог кандидатов на новые драйверы (roadmap): [PLATFORM_DEVELOPER_BACKLOG.md §10](PLATFORM_DEVELOPER_BACKLOG.md#10-каталог-драйверов-устройств-roadmap) (REQ-PF-14).

## Зрелость драйверов

| Уровень | Значение |
|---------|----------|
| **production** | Полный poll/read/write, тесты, документированный config |
| **beta** | Рабочий connectivity, ограниченный feature set |
| **stub** | TCP/session check или connectivity shell (v0.1) |
| **simulator** | Virtual/profile-based (см. PF-09) |

Многие записи каталога REQ-PF-14 помечены как stub в [PLATFORM_DEVELOPER_BACKLOG.md §10](PLATFORM_DEVELOPER_BACKLOG.md#10-каталог-драйверов-устройств-roadmap).

## Архитектура

Драйверы реализуют SPI `DeviceDriver` (`packages/ispf-driver-api`):

```java
public interface DeviceDriver {
    DriverMetadata metadata();
    void connect(Map<String, String> config) throws DriverException;
    void disconnect();
    void readPoints() throws DriverException;
    void writePoint(String pointId, DataRecord value) throws DriverException;
}
```

Регистрация через **driver packs** в `${ISPF_DRIVER_PACKS_DIR}` (`LicensedDriverPackLoader` → `LicensedDriverRegistry` → `DriverCatalog`). Runtime — `DriverRuntimeService`: poll loop по `pollIntervalMs`.

Сборка packs: `./gradlew syncAllDriverPacks` → `build/driver-packs/<packId>/`. См. [LICENSED_DRIVER_PACKS.md](LICENSED_DRIVER_PACKS.md).

## Переменные устройства (driver group)

На объекте `DEVICE` переменные группы `driver` появляются при **provisioning драйвера** (`POST /objects` с `driverId` или `PUT .../drivers/runtime/configure`), а не через auto-apply RELATIVE-моделей.

`DeviceProvisioningService` → `SystemObjectStructureService.ensureDeviceDriverStructure()` встраивает схему (`driverId`, `driverStatus`, `driverPollIntervalMs`, `driverConfigJson`, `driverPointMappingsJson`, `status`) из blueprint без записи в каталог моделей и без `appliedModelIds`.

Fixture RELATIVE-модель `device-driver-v1` (при `fixtures-enabled`) — для demo/lab и явного apply; см. [ADR-0018](decisions/0018-fixture-models-and-cel-applicability.md).

| Переменная | Описание |
|------------|----------|
| `driverId` | ID драйвера — полный список см. таблицу ниже |
| `driverStatus` | `STOPPED` / `RUNNING` / `ERROR` |
| `driverPollIntervalMs` | Интервал опроса |
| `driverConfigJson` | JSON конфигурации |
| `driverPointMappingsJson` | JSON: `variableName → pointId` |

## REST Runtime API

```http
POST /api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01
POST /api/v1/drivers/runtime/stop?devicePath=...
POST /api/v1/drivers/runtime/poll?devicePath=...
POST /api/v1/drivers/runtime/write?devicePath=...&pointId=<variableName>
PUT  /api/v1/drivers/runtime/configure?devicePath=...
GET  /api/v1/drivers/runtime/status?devicePath=...
```

`write` body — `DataRecord` с полем `value` (число, boolean или string). `pointId` — ключ из `driverPointMappingsJson` (имя переменной).

## Driver packs (не встроены в server JAR)

Каждый протокол — отдельный pack (`ispf-driver-*`). Без установленных packs `GET /api/v1/drivers` пуст.

### virtual (`ispf-driver-virtual`)

Симулятор для стенда без железа. Профиль задаётся в `driverConfigJson.profile`:

| `profile` | Переменные | Назначение |
|-----------|------------|------------|
| `demo` (default) | `temperature`, `status` | Синусоида температуры |
| `meter` | `meterLiters`, `flowRate`, `filling` | Налив: `litersPerSecond`, `filling` |
| `weighbridge` | `grossWeight`, `tareKg` | `tareKg + meterLiters * density` |
| `rack-signals` | `gasPresent`, `groundConnected` | Булевы сигналы по `rackId` |

Пример meter (virtual driver profile):

```json
{
  "profile": "meter",
  "litersPerSecond": "120",
  "filling": "true"
}
```

Конфиг demo-температуры:

```json
{
  "profile": "demo",
  "baseTemperature": "22.0",
  "amplitude": "15.0",
  "periodSec": "60"
}
```

### mqtt (`ispf-driver-mqtt`)

Eclipse Paho, подписка на топики.

Конфиг: `brokerUrl`, `topicPrefix`, `clientId`, credentials.

Point mapping: `variableName → mqttTopicSuffix`.

### modbus-tcp (`ispf-driver-modbus`)

j2mod, Modbus TCP. Poll/read/write через `readPoints` / `writePoint`.

Формат точки: `slaveId:registerType:address[:count]`

Типы регистров: `HOLDING`, `INPUT`, `COIL`, `DISCRETE`.

Write (`writePoint`):

| Тип | Modbus function | Поле в `DataRecord` |
|-----|-----------------|---------------------|
| `HOLDING` | Write Single Register (FC6) | `raw` или `value` (число) |
| `COIL` | Write Single Coil (FC5) | `value` (boolean) |
| `INPUT`, `DISCRETE` | — | read-only, ошибка |

Конфиг: `host`, `port`, `timeoutMs`, `pollIntervalMs`.

### modbus-rtu (`ispf-driver-modbus-rtu`)

j2mod, Modbus RTU serial. Тот же формат точек и write matrix, что у `modbus-tcp`.

Write: `HOLDING` (FC6), `COIL` (FC5); `INPUT`/`DISCRETE` read-only.

Конфиг: `serialPort`, `baudRate`, `dataBits`, `stopBits`, `parity`, `timeoutMs`, `pollIntervalMs`.

### snmp (`ispf-driver-snmp`)

SNMP4J, v1/v2c/v3 GET/SET (v3: USM MD5/SHA + DES/AES128).

Формат точки: `oid`, `oid:VALUE_KIND` (`STRING`, `INTEGER`, …), или `oid:VALUE_KIND:optional` — последний вариант не прерывает poll при отсутствии OID (например `hrProcessorLoad` на Windows SNMP agent).

Демо `snmp-localhost`: MIB-II + HOST-RESOURCES-MIB + IF-MIB (см. модель `snmp-agent-v1` и дашборд `snmp-host-monitoring`):

| Переменная | OID |
|------------|-----|
| `sysName` | 1.3.6.1.2.1.1.5.0 |
| `sysDescr` | 1.3.6.1.2.1.1.1.0 |
| `sysUpTime` | 1.3.6.1.2.1.1.3.0 |
| `sysLocation` | 1.3.6.1.2.1.1.6.0 |
| `sysContact` | 1.3.6.1.2.1.1.4.0 |
| `hrMemorySize` | 1.3.6.1.2.1.25.2.2.0 |
| `hrSystemProcesses` | 1.3.6.1.2.1.25.1.6.0 |
| `hrSystemNumUsers` | 1.3.6.1.2.1.25.1.5.0 |
| `ifNumber` | 1.3.6.1.2.1.2.1.0 |
| `ifInOctets` | 1.3.6.1.2.1.2.2.1.10.2 (typical Linux NIC index 2) |
| `ifOutOctets` | 1.3.6.1.2.1.2.2.1.16.2 |
| `hrProcessorLoad` | 1.3.6.1.2.1.25.3.3.1.2.196608 (optional — Linux hrDevice index) |

Конфиг v2c:

```json
{
  "host": "127.0.0.1",
  "port": "161",
  "community": "public",
  "version": "2c",
  "timeoutMs": "3000",
  "retries": "1"
}
```

Конфиг v3 (дополнительно):

```json
{
  "version": "3",
  "securityName": "snmpuser",
  "authProtocol": "MD5",
  "authPassphrase": "authpass",
  "privProtocol": "DES",
  "privPassphrase": "privpass"
}
```

### http (`ispf-driver-http`)

HTTP/HTTPS client (Java HttpClient). Опрос REST endpoints.

Point mapping: `path`, `GET:path`, `HEAD:path`, полный URL, суффикс `:json` для строкового JSON-скаляра.

```json
{
  "baseUrl": "http://127.0.0.1:8080",
  "timeoutMs": "5000"
}
```

Пример mappings: `{"platformVersion": "GET:/api/v1/info:json"}`

### icmp (`ispf-driver-icmp`)

Доступность хоста (ICMP / `InetAddress.isReachable`).

Point mapping: hostname или IP на переменную; пустое значение — `host` из конфига.

```json
{
  "host": "127.0.0.1",
  "timeoutMs": "3000"
}
```

Переменная получает: `reachable`, `latencyMs`, `host`.

### ssh (`ispf-driver-ssh`)

Удалённое выполнение shell-команды (JSch).

Point mapping: команда на переменную, например `uptime`.

```json
{
  "host": "192.168.1.10",
  "port": "22",
  "username": "admin",
  "password": "secret",
  "timeoutMs": "10000"
}
```

Переменная: `value` (stdout), `exitCode`, `stderr`.

### coap (`ispf-driver-coap`)

CoAP client (Eclipse Californium), GET ресурсов IoT-устройств.

Point mapping: путь `/sensor/temp` или полный `coap://host:5683/...`

```json
{
  "host": "127.0.0.1",
  "port": "5683",
  "timeoutMs": "5000"
}
```

Loopback test: `CoapDeviceDriverTest` (in-process Californium CoAP server).

## Каталог зарегистрированных драйверов (58)

Поле `maturity` в `GET /api/v1/drivers`: `PRODUCTION` (по умолчанию), `BETA`, `STUB`. Метки задаются в `DriverMaturityRegistry` на сервере и отображаются в Web Console при выборе драйвера.

### Stub promotion (demand-driven)

58 `driverId` зарегистрированы; часть — **STUB** или **BETA** (connectivity shell без полного протокола). Продвижение до **PRODUCTION** — **не** по расписанию roadmap, а **по запросу app-команды** через gate [0002](decisions/0002-dogfooding-gate.md):

1. App-команда описывает сценарий (устройство, point mapping, acceptance test).
2. Platform PR добавляет протокольную логику в существующий `ispf-driver-*` модуль.
3. `DriverMaturityRegistry` обновляется; документация в этом файле.

Текущие STUB/BETA кандидаты (июнь 2026):

| `driverId` | Maturity | Заметка |
|------------|----------|---------|
| `corba` | STUB/BETA | CORBA IIOP TCP shell |
| `ethernet-ip` | STUB/BETA | EtherNet/IP session stub |
| `opc-da` | STUB/BETA | OPC DA (DCOM/native) |
| `opc-bridge` | STUB/BETA | OPC/LON bridge TCP |
| `vmware` | STUB/BETA | vSphere SOAP stub |
| `smi-s` | STUB/BETA | SMI-S CIM-XML stub |

Отдельный хвост: native STUB promotion — см. § Stub promotion ниже.

См. [ROADMAP.md § Phase 17.4](ROADMAP.md#phase-17--post-baseline-hardening-v080), [GAP_REGISTRY.md](GAP_REGISTRY.md).

Полный список `driverId` в `DriverCatalog`:

| `driverId` | Модуль | Назначение |
|------------|--------|------------|
| `virtual` | `ispf-driver-virtual` | Симулятор |
| `mqtt` | `ispf-driver-mqtt` | MQTT subscribe |
| `modbus-tcp` | `ispf-driver-modbus` | Modbus TCP |
| `modbus-rtu` | `ispf-driver-modbus-rtu` | Modbus RTU serial |
| `modbus-udp` | `ispf-driver-modbus-udp` | Modbus UDP |
| `snmp` | `ispf-driver-snmp` | SNMP v1/v2c/v3 |
| `http` | `ispf-driver-http` | HTTP/HTTPS client |
| `http-server` | `ispf-driver-http-server` | Встроенный HTTP server |
| `icmp` | `ispf-driver-icmp` | Ping |
| `ssh` | `ispf-driver-ssh` | SSH command |
| `coap` | `ispf-driver-coap` | CoAP GET |
| `opcua` | `ispf-driver-opcua` | OPC UA client (Milo) |
| `opcua-server` | `ispf-driver-opcua-server` | OPC UA server (Milo) |
| `opc-da` | `ispf-driver-opc-da` | OPC DA stub (DCOM/native) |
| `opc-bridge` | `ispf-driver-opc-bridge` | OPC/LON bridge TCP stub |
| `s7` | `ispf-driver-s7` | Siemens S7 |
| `iec104` | `ispf-driver-iec104` | IEC 104 client |
| `iec104-server` | `ispf-driver-iec104-server` | IEC 104 server/slave |
| `bacnet` | `ispf-driver-bacnet` | BACnet/IP |
| `dnp3` | `ispf-driver-dnp3` | DNP3 TCP master (Class 0/1/2/3 poll) |
| `ethernet-ip` | `ispf-driver-ethernet-ip` | EtherNet/IP session stub |
| `dlms` | `ispf-driver-dlms` | DLMS/COSEM master (Gurux read/write) |
| `jmx` | `ispf-driver-jmx` | JMX local/remote |
| `jdbc` | `ispf-driver-jdbc` | SQL JDBC |
| `odbc` | `ispf-driver-odbc` | ODBC via JDBC bridge JAR |
| `file` | `ispf-driver-file` | File metadata/content |
| `folder` | `ispf-driver-folder` | Directory listing |
| `application` | `ispf-driver-application` | Shell/script |
| `message-stream` | `ispf-driver-message-stream` | TCP/UDP stream |
| `nmea` | `ispf-driver-nmea` | NMEA 0183 |
| `telnet` | `ispf-driver-telnet` | Telnet |
| `soap` | `ispf-driver-soap` | SOAP |
| `ip-host` | `ispf-driver-ip-host` | PING, HTTP, TCP, DNS, SMTP, FTP |
| `ldap` | `ispf-driver-ldap` | LDAP search |
| `dhcp` | `ispf-driver-dhcp` | DHCP discover |
| `imap` | `ispf-driver-imap` | IMAP mailbox |
| `pop3` | `ispf-driver-pop3` | POP3 mailbox |
| `radius` | `ispf-driver-radius` | RADIUS auth check |
| `ipmi` | `ispf-driver-ipmi` | IPMI LAN |
| `wmi` | `ispf-driver-wmi` | WMI (PowerShell, Windows) |
| `kafka` | `ispf-driver-kafka` | Kafka |
| `jms` | `ispf-driver-jms` | JMS (ActiveMQ) |
| `cwmp` | `ispf-driver-cwmp` | TR-069 Inform client |
| `web-transaction` | `ispf-driver-web-transaction` | Multi-step HTTP |
| `graph-db` | `ispf-driver-graph-db` | Neo4j / Gremlin |
| `vmware` | `ispf-driver-vmware` | vSphere SOAP stub |
| `smi-s` | `ispf-driver-smis` | SMI-S CIM-XML stub |
| `gps-tracker` | `ispf-driver-gps-tracker` | GPS/M2M TCP server |
| `flexible` | `ispf-driver-flexible` | Flexible TCP/UDP |
| `mbus` | `ispf-driver-mbus` | M-Bus |
| `modem-at` | `ispf-driver-modem-at` | GSM AT commands |
| `omron-fins` | `ispf-driver-omron-fins` | Omron FINS |
| `asterisk` | `ispf-driver-asterisk` | Asterisk AMI |
| `sip` | `ispf-driver-sip` | SIP OPTIONS/REGISTER |
| `xmpp` | `ispf-driver-xmpp` | XMPP (Smack) |
| `smpp` | `ispf-driver-smpp` | SMPP |
| `smb` | `ispf-driver-smb` | SMB/CIFS |
| `corba` | `ispf-driver-corba` | CORBA IIOP TCP stub |

Подробные конфиги для базовых драйверов — в секциях ниже. Остальные следуют тому же паттерну: `driverConfigJson` + `driverPointMappingsJson`, см. `DriverMetadata` в модуле.

### Ограничения v0.1 (нужен native / полный стек)

| `driverId` | Что есть сейчас | Для production |
|------------|-----------------|----------------|
| `dnp3` | Class 0/1/2/3 poll (read) | `io.stepfunc:dnp3` native |
| `ethernet-ip` | Register Session | CIP tag read/write library |
| `dlms` | TCP WRAPPER + read/write | Gurux association (auth NONE v0.2) |
| `opc-da` | status / proxy TCP | Windows DCOM bridge |
| `corba` | IIOP TCP | JDK CORBA removed; use bridge |
| `wmi` | PowerShell | Только Windows |

### Примеры (кратко)

### opcua (`ispf-driver-opcua`)

OPC UA client (Eclipse Milo). Poll/read/write через `readPoints` / `writePoint`.

Point mapping: `ns=2;s=TagName` (NodeId).

Write (`writePoint`): Milo `writeValue` на Value attribute; тип Variant подбирается по текущему значению узла (boolean, numeric, string, unsigned). Поля `value` или `raw`.

Конфиг: `endpointUrl`, `timeoutMs`, `pollIntervalMs` (SecurityPolicy None).

### s7 (`ispf-driver-s7`)

Siemens S7 over ISO-on-TCP. Poll/read/write через `readPoints` / `writePoint`.

Point mapping: `area:dbNumber:offset:type` (например `DB:1:0:REAL`).

Поддерживаемые типы: `BOOL`, `BYTE`, `SINT`, `USINT`, `INT`, `UINT`, `WORD`, `DINT`, `UDINT`, `DWORD`, `REAL`, `LREAL`.

Write (`writePoint`):

| Тип | Поле в `DataRecord` |
|-----|---------------------|
| `BOOL` | `value` (boolean); read-modify-write бита 0 в байте offset |
| целочисленные | `raw` или `value` (число) |
| `REAL`, `LREAL` | `value` или `raw` (число) |

Конфиг: `host`, `port` (102), `rack`, `slot`, `timeoutMs`.

### iec104 (`ispf-driver-iec104`)

IEC 60870-5-104 master. Конфиг: `host`, `port` (2404), `commonAddress`, `timeoutMs`.

Point mapping: `ioa:dataType` (например `2001:BOOL`, `3001:FLOAT`, `1001:M_ME_NA_1`).

**Write (BL-23):** `BOOL` / `M_SP_NA_1` → `singleCommand`; `FLOAT` / `M_ME_NC_1` → `setShortFloatCommand`; `INT` / `M_ME_NA_1` → `setNormalizedValueCommand`. После write переменная обновляется локально (`quality=GOOD`); poll read может вернуть `NOT_AVAILABLE` если outstation не отвечает на read command.

Loopback test: `Iec104DeviceDriverTest` против `iec104-server`.

### bacnet (`ispf-driver-bacnet`)

BACnet/IP read/write property (`present-value`). Конфиг: `host`, `port` (47808), `localDeviceId`, `remoteDeviceId`, `timeoutMs`. Опционально: `bindAddress` (UDP bind, default `0.0.0.0`), `bindPort` (если отличается от `port` на том же хосте).

Point mapping: `objectType:instance:property` (например `analog-output:1:present-value`).

**Write:** `analog-output`/`analog-value` → `Real`; `binary-output`/`binary-value` → `BinaryPV`; `multi-state-output`/`multi-state-value` → `UnsignedInteger`. Read-only: `analog-input`, `binary-input`, `multi-state-input`.

Maturity: **beta**. Тесты: guard-rails + `BacnetLoopbackServer` simulator + connect smoke (`BacnetDeviceDriverTest`); полный property exchange — на hardware/BACnet simulator.

### dnp3 (`ispf-driver-dnp3`)

DNP3 TCP **master** с integrity poll Class 0/1/2/3 (`io.stepfunc:dnp3` 1.6.0).

Конфиг: `host`, `port`, `localAddress` (master link address, default `1`), `outstationAddress` (default `1024`), `timeoutMs`.

Point mapping: `index:dataType` — `BINARY_INPUT`, `BINARY_OUTPUT`, `ANALOG_INPUT`, `ANALOG_OUTPUT`, `COUNTER` (например `0:ANALOG_INPUT`).

На каждый `readPoints` выполняется `Request.classRequest(0,1,2,3)`; значения и DNP3 flags (`status`) обновляются в переменных объекта.

Maturity: **beta** (read-only; loopback test `Dnp3DeviceDriverTest`).

### dlms (`ispf-driver-dlms`)

DLMS/COSEM **master** over TCP **WRAPPER** (`gurux.dlms` + `gurux.net`).

Конфиг: `host`, `port` (default `4059`), `clientAddress` (default `16`), `logicalDevice` (default `1`), `timeoutMs`.

Point mapping: `logicalDevice:obis[:objectType[:attribute]]` — по умолчанию `REGISTER`, attribute `2`.  
Примеры: `1:1.0.1.8.0.255`, `1:0.0.42.0.0.255:DATA:2`.

`readPoints` / `writePoint`: SNRM + AARQ association, Gurux GET/SET. Write: поля `value` или `raw` (numeric для REGISTER).

Maturity: **beta** (auth NONE; loopback `DlmsDeviceDriverTest`).

### jmx (`ispf-driver-jmx`)

JMX remote или local. Конфиг: `serviceUrl` (`service:jmx:rmi:///jndi/rmi://host:port/jmxrmi`) или пусто для local.

Point mapping: `ObjectName:attribute` (например `java.lang:type=Memory:HeapMemoryUsage`).

### jdbc (`ispf-driver-jdbc`)

SQL scalar. Конфиг: `jdbcUrl`, `username`, `password`, `query`, `timeoutMs`.

Point mapping: имя колонки или `value` для одной ячейки.

### file / folder

`file`: путь → `exists`, `size`, `lastModified`, `content` (текст).  
`folder`: путь → список файлов, счётчики.

### application (`ispf-driver-application`)

Запуск процесса. Конфиг: `command`, `workingDir`, `timeoutMs`.

Point mapping: переменная → аргумент или `stdout`/`exitCode`.

### message-stream (`ispf-driver-message-stream`)

TCP client/server или UDP. Конфиг: `mode`, `host`, `port`, `encoding`.

Point mapping: `feed` — последнее сообщение.

### nmea (`ispf-driver-nmea`)

NMEA 0183. Конфиг: `mode` (tcp/serial), `host`/`port` или `serialPort`.

Point mapping: sentence type (`GGA`, `RMC`) или `raw`.

### telnet / soap

`telnet`: host, port, credentials; mapping — команда.  
`soap`: `endpointUrl`, `soapAction`; mapping — тело запроса или XPath-подобный путь к значению.

### ip-host (`ispf-driver-ip-host`)

Единый IT-мониторинг. Point mapping префиксы:

| Префикс | Пример | Проверка |
|---------|--------|----------|
| `PING:` | `PING:8.8.8.8` | ICMP |
| `HTTP:` | `HTTP:https://host/` | HTTP HEAD |
| `TCP:` | `TCP:host:443` | TCP connect |
| `DNS:` | `DNS:example.com` | DNS resolve |
| `SMTP:` | `SMTP:host:25` | SMTP banner |
| `FTP:` | `FTP:host:21` | FTP connect |

### kafka (`ispf-driver-kafka`)

Конфиг: `bootstrapServers`, `topic`, `groupId`, `timeoutMs`.

Point mapping: `consume` (последнее сообщение) или `produce:payload`.

### cwmp (`ispf-driver-cwmp`) — PRODUCTION

TR-069 CPE client: Periodic Inform к ACS, обработка `GetParameterValues` RPC.

Конфиг (`driverConfigJson`):

```json
{
  "acsUrl": "http://acs.example:7547/",
  "deviceId": "000000-000000000000",
  "timeoutMs": "5000",
  "informParameters": "Device.DeviceInfo.SoftwareVersion"
}
```

Point mapping: TR-069 parameter name (например `Device.DeviceInfo.SoftwareVersion`) или `connected` (статус последнего Inform).

Write: `POST /api/v1/drivers/runtime/write` с `pointId` и `{ "rows": [{ "value": "..." }] }` — драйвер применяет `SetParameterValues` локально и отправляет `SetParameterValuesResponse` на ACS. Требуется предварительный poll (point mapping в памяти). Псевдо-точка `connected` — read-only.

### gps-tracker (`ispf-driver-gps-tracker`)

TCP **server** — устройства подключаются к платформе. Конфиг: `listenPort`, `bufferSize`.

Point mapping: `feed` — последняя строка/буфер.

### flexible (`ispf-driver-flexible`)

Универсальный TCP/UDP. Версия **0.2.0** — legacy-режим и **exchange pipeline** для framed request/response.

#### Legacy (без изменений)

Конфиг: `protocol`, `host`, `port`, `encoding` (`hex`|`utf8`|`escapes`), `timeoutMs`.

Point mapping: `request[:responseRegex]` — отправка и опциональный regex capture group 1.

#### Exchange pipeline

Для ASCII/фреймовых протоколов (SOH/ETX, optional checksum, structured extractors). Типичный случай — **serial computer format over TCP**: ASCII function code + ASCII-hex floats; checksum на проводе часто **не проверяют** (`checksumAlgorithm: none`), т.к. TCP уже обеспечивает транспорт.

Конфиг device:

| Ключ | Значение | Описание |
|------|----------|----------|
| `readMode` | `idle` (default) \| `delimiter` | `idle` — читать пока буфер пуст; `delimiter` — до байта `readUntilHex` |
| `readUntilHex` | `03` | терминатор (hex), при `readMode=delimiter` |
| `readMaxBytes` | `8192` | лимит ответа |
| `checksumAlgorithm` | `none` \| `sum16-complement-hex` | `none` — типично для serial-over-TCP; `sum16-complement-hex` — если шлюз требует проверку |
| `checksumMarker` | `&&` | маркер перед checksum |
| `checksumLength` | `4` | длина checksum (hex-символы) |
| произвольные ключи | | подстановка в `${key}` шаблонах запроса |

Point mapping (pipeline):

```
req:{template}|var:{k}={v}|verifyChecksum|extract:{type}:{args}
```

| Сегмент | Пример |
|---------|--------|
| `req:` | `\x01${securityCode}i20101` — `\xHH` escapes, `${var}` из config/point |
| `var:` | `tank=01` — per-point переменные |
| `verifyChecksum` | явная проверка (или auto при `checksumAlgorithm`) |
| `extract:regex:{pattern}:{group}` | regex capture |
| `extract:asciiHexFloat:{index}` | N-й 8-nibble IEEE float в payload |
| `extract:asciiHexFloat:{index}:after:{marker}` | floats после ASCII-маркера |
| `extract:slice:{start}:{len}` | подстрока |
| `extract:literal:{text}` | константа |

Points с одинаковым resolved `req:` **группируются** — один TCP/UDP exchange на poll.

Пример (ASCII serial-over-TCP, без checksum): [examples/framed-serial-tcp](../examples/framed-serial-tcp/README.md).

Response schema: `value`, `raw`, `bytesRead` (STRING/STRING/INTEGER).

### mbus (`ispf-driver-mbus`)

M-Bus meter read (jmbus 3.x). Конфиг: TCP (`host`, `port`) или serial (`serialPort`).

Point mapping: `primary:secondary:register`.

### omron-fins (`ispf-driver-omron-fins`)

FINS/TCP. Конфиг: `host`, `port` (9600), `destNode`, `srcNode`.

Point mapping: `DM:100:1` (area:address:count).

### asterisk (`ispf-driver-asterisk`)

AMI. Конфиг: `host`, `port` (5038), `username`, `secret`.

Point mapping: AMI action block (например `Action: Ping`).

### smpp (`ispf-driver-smpp`)

SMPP 3.x (jsmpp). Конфиг: `host`, `port`, `systemId`, `password`.

Point mapping: `bind` (статус) или `destination:message` (submit).

### smb (`ispf-driver-smb`)

SMB/CIFS (smbj). Конфиг: `host`, `share`, `username`, `password`, `domain`.

Point mapping: путь к файлу в share → `exists`, `size`.

## Добавление своего драйвера

1. Создайте модуль `packages/ispf-driver-xxx`, зависимость на `ispf-driver-api`.
2. Реализуйте `DeviceDriver`.
3. Зарегистрируйте в `DriverCatalog` (`ispf-server`).
4. Добавьте `implementation(project(...))` в `ispf-server/build.gradle.kts`.
5. Определите model с driver-переменными или примените `RELATIVE` модель к `DEVICE`.

## Диагностика

- Логи: `com.ispf.server.driver` (уровень DEBUG в `local`/`dev`)
- `driverStatus` на объекте устройства
- WARN в логе при ошибке poll (SNMP timeout); optional OID — один раз DEBUG, poll продолжается
