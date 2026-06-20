# Драйверы устройств

Каталог кандидатов на новые драйверы (roadmap): [PLATFORM_DEVELOPER_BACKLOG.md §10](PLATFORM_DEVELOPER_BACKLOG.md#10-каталог-драйверов-устройств-roadmap) (REQ-PF-14).

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

Регистрация в `DriverCatalog` (server). Runtime — `DriverRuntimeService`: poll loop по `pollIntervalMs`.

## Переменные устройства (driver group)

На объекте `DEVICE` с моделью задаются:

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
PUT  /api/v1/drivers/runtime/configure?devicePath=...
GET  /api/v1/drivers/runtime/status?devicePath=...
```

## Встроенные драйверы

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

j2mod, Modbus TCP.

Формат точки: `slaveId:registerType:address[:count]`

Типы регистров: `HOLDING`, `INPUT`, `COIL`, `DISCRETE`.

### snmp (`ispf-driver-snmp`)

SNMP4J, v1/v2c/v3 GET/SET (v3: USM MD5/SHA + DES/AES128).

Формат точки: `oid` или `oid:VALUE_KIND` (`STRING`, `INTEGER`, …).

Демо `snmp-localhost`: MIB-II OIDs (`sysName`, `sysDescr`, `sysUpTime`, `sysLocation`).

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

## Каталог зарегистрированных драйверов (58)

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
| `dnp3` | `ispf-driver-dnp3` | DNP3 TCP (connectivity) |
| `ethernet-ip` | `ispf-driver-ethernet-ip` | EtherNet/IP session stub |
| `dlms` | `ispf-driver-dlms` | DLMS/COSEM (Gurux) |
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
| `dnp3` | TCP connectivity | `io.stepfunc:dnp3` native |
| `ethernet-ip` | Register Session | CIP tag read/write library |
| `dlms` | TCP + OBIS parse | Gurux association + auth |
| `opc-da` | status / proxy TCP | Windows DCOM bridge |
| `corba` | IIOP TCP | JDK CORBA removed; use bridge |
| `wmi` | PowerShell | Только Windows |

### Примеры (кратко)

OPC UA client. Конфиг: `endpointUrl`, `securityPolicy` (optional), credentials.

Point mapping: `ns=2;s=TagName` (NodeId).

### s7 (`ispf-driver-s7`)

Siemens S7 over ISO-on-TCP. Конфиг: `host`, `port` (102), `rack`, `slot`, `timeoutMs`.

Point mapping: `DB1.DBD0:REAL` или area/offset.

### iec104 (`ispf-driver-iec104`)

IEC 60870-5-104 master. Конфиг: `host`, `port` (2404), `commonAddress`.

Point mapping: `ioa:type` (например `1001:M_ME_NC_1`).

### bacnet (`ispf-driver-bacnet`)

BACnet/IP read property. Конфиг: `broadcastAddress`, `localBindAddress`, `port` (47808).

Point mapping: `deviceId:objectType:instance:propertyId`.

### dnp3 (`ispf-driver-dnp3`)

TCP-сессия к outstation; полный DNP3 application layer требует native (`io.stepfunc:dnp3`).

Point mapping: `index:dataType` (например `0:ANALOG_INPUT`). Статус: `connected`, `reachable`.

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

### gps-tracker (`ispf-driver-gps-tracker`)

TCP **server** — устройства подключаются к платформе. Конфиг: `listenPort`, `bufferSize`.

Point mapping: `feed` — последняя строка/буфер.

### flexible (`ispf-driver-flexible`)

Универсальный TCP/UDP. Конфиг: `protocol`, `host`, `port`, `encoding` (`hex`|`utf8`), `timeoutMs`.

Point mapping: `request[:responseRegex]` — отправка и опциональный regex capture.

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
- WARN в логе при ошибке poll (SNMP timeout, read-only variable)
