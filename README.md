# Pedidos360 (microservicios con eventos)

```
pedidos360/
├── frontend/      React + Vite (sin cambios)
├── docker-compose.yml   RabbitMQ (local y EC2)
├── rabbit-bridge/ Java simple · puente RabbitMQ -> SNS (corre en la EC2 del broker)
├── ms-catalog/    Spring Boot · productos y stock · /api/catalog/* · puerto 8081
└── ms-orders/     Spring Boot · pedidos y estados · /api/orders/*  · puerto 8082
```

## Cómo se comunican los servicios

| Qué | Cómo | Dónde |
|---|---|---|
| Consultar un producto (precio, stock) al crear un pedido | HTTP síncrono con el JWT del usuario | `CatalogClient` (ms-orders) -> API Gateway -> ms-catalog |
| Descontar stock al ACEPTAR un pedido | **Evento** (asíncrono) | **RabbitMQ** (bus de los servicios, en una EC2) + **SNS/SQS** (entrega a las Lambdas) |

**Reparto de responsabilidades:** los microservicios solo hablan con RabbitMQ (publican con confirmación del broker).
SNS y SQS siguen en el flujo como la etapa de entrega hacia las Lambdas consumidoras (fan-out por tópico, filtro por `tipo`,
reintentos y DLQ de SQS). Entre ambos mundos está **`rabbit-bridge`**, un proceso que corre en la misma EC2 que RabbitMQ y reenvía las dos colas a sus tópicos SNS
(Amazon MQ no está disponible en el rol del lab, por eso RabbitMQ va en una EC2 y el puente no es una Lambda).

### Flujo de "aceptar pedido"

```
Operador acepta el pedido (PATCH /api/orders/{id}/estado)
  1. ms-orders (Lambda API): estado=ACEPTADO + fila en outbox_eventos (misma transacción)
  2. tras el commit publica PedidoAceptado en RabbitMQ: exchange pedidos360.eventos, routing key pedido.aceptado
     -> cola pedidos360.pedido-aceptado   (DLQ: pedidos360.pedido-aceptado.dlq)
  3. rabbit-bridge (EC2) reenvía la cola a SNS pedidos360-pedido-aceptado y confirma (ack) solo si SNS aceptó
  4. SNS -> SQS pedidos360-catalog-stock-queue (DLQ: pedidos360-catalog-stock-dlq)
  5. Lambda ms-catalog-stock-consumer: descuenta stock (idempotente, atómico)
  6. publica StockDescontado / StockRechazado en RabbitMQ: routing key stock.descontado / stock.rechazado
     -> cola pedidos360.stock-resultado   (DLQ: pedidos360.stock-resultado.dlq)
  7. rabbit-bridge (EC2) reenvía la cola a SNS pedidos360-stock-resultado
  8. SNS -> SQS pedidos360-orders-stock-result-queue (DLQ: pedidos360-orders-stock-result-dlq)
  9. Lambda ms-orders-stock-result-consumer: confirma el stock del pedido
     o, si fue rechazado, lo cancela (compensación) con el motivo
```

Reglas que hacen seguro el flujo:

* **Outbox** (ms-orders): el evento se guarda junto con el cambio de estado; solo se marca como publicado cuando
  RabbitMQ **confirma** la recepción (publisher confirm). Si falla queda pendiente y se reintenta en la siguiente
  aceptación o con `POST /api/orders/outbox/reintentar` (solo Admin).
* **Reconciliación**: `POST /api/orders/stock/reconciliar` (solo Admin) vuelve a emitir el evento de los pedidos
  aceptados sin stock confirmado. El eventId es fijo (`pedido-<id>`), así que no descuenta dos veces.
* **Idempotencia** (ms-catalog): tabla `eventos_procesados`. Tanto SQS como el puente entregan "al menos una vez";
  un evento repetido no vuelve a descontar stock (solo reenvía el resultado guardado).
* **Descuento atómico**: una sola sentencia SQL condicionada al stock, sin pisarse con otros consumidores.
* **Stock reservado**: al crear pedidos se resta lo de pedidos CREADO y de los ACEPTADO sin confirmación
  (`stockConfirmado = false`), para que no haya una ventana en la que ese stock parezca libre.
* **Base de datos por servicio**: cada microservicio tiene su propia instancia RDS.
* **Mensajes envenenados**: en RabbitMQ las colas llevan DLX + TTL (1 h): lo que no se pueda reenviar acaba en la
  `.dlq` en vez de reintentarse sin fin. En SQS siguen las DLQ con 3 intentos.

### Topología de RabbitMQ

La declara sola `RabbitTopologyConfig` (Spring AMQP) la primera vez que un servicio abre conexión; ambos servicios
llevan la misma clase y declarar es idempotente.

| Elemento | Nombre | Detalle |
|---|---|---|
| Exchange | `pedidos360.eventos` | topic, durable |
| Cola | `pedidos360.pedido-aceptado` | binding `pedido.aceptado` |
| Cola | `pedidos360.stock-resultado` | binding `stock.*` |
| Exchange DLX | `pedidos360.eventos.dlx` | direct |
| Colas DLQ | `<cola>.dlq` | rechazados o vencidos |

### Contrato de eventos (JSON)

`PedidoAceptado` (ms-orders -> ms-catalog):
```json
{ "eventId": "uuid", "tipo": "PedidoAceptado", "version": 1, "ocurridoEn": "2026-11-01T15:00:00Z",
  "pedidoId": 12, "items": [ { "productoId": 1, "cantidad": 2 } ] }
```
`StockDescontado` / `StockRechazado` (ms-catalog -> ms-orders):
```json
{ "eventId": "res-<eventId origen>", "tipo": "StockRechazado", "version": 1, "ocurridoEn": "2026-11-01T15:00:01Z",
  "eventoOrigenId": "uuid", "pedidoId": 12, "motivo": "Stock insuficiente para 'Mouse': disponible 1, solicitado 2" }
```
El contrato JSON **no cambió**. `rabbit-bridge` copia el campo `tipo` del cuerpo al atributo de mensaje `tipo` de SNS
(el que usan los filtros de las suscripciones), y las suscripciones SNS -> SQS deben seguir con **Raw message delivery** activado.

## Compilar (cada servicio por separado)

```bash
cd ms-catalog && mvn clean package     # -> target/ms-pedidos360-catalog-1.0.0-aws.jar
cd ms-orders  && mvn clean package     # -> target/ms-pedidos360-orders-1.0.0-aws.jar
```
El jar `-aws.jar` es el que se sube a Lambda. **Cada jar se usa en dos Lambdas** (API y consumidora),
cambiando solo el handler.

## Ejecutar en local (perfil `local`, base H2 en memoria)

```bash
cd ms-catalog && mvn spring-boot:run     # :8081
cd ms-orders  && mvn spring-boot:run     # :8082  (usa CATALOG_SERVICE_URL=http://localhost:8081 por defecto)
```
Los endpoints exigen un JWT de Cognito (`Authorization: Bearer <idToken>`).

Por defecto no hace falta ningún broker: los eventos solo se escriben en el log, así que **el stock no se descuenta**
al aceptar. Para probar el flujo completo **sin AWS** se levanta un RabbitMQ en Docker y se activan estas variables en
**ambos** servicios:

```bash
docker compose up -d                     # RabbitMQ en :5672 (panel en http://localhost:15672, guest/guest)
export RABBITMQ_ENABLED=true RABBITMQ_LOCAL_CONSUMER=true
```
Con `RABBITMQ_LOCAL_CONSUMER=true` cada servicio escucha su cola y hace lo que en AWS hacen las Lambdas
(SNS/SQS y `rabbit-bridge` no participan en local).

## Compilar y ejecutar rabbit-bridge

```bash
cd rabbit-bridge && mvn clean package     # -> target/rabbit-bridge.jar (fat-jar ejecutable)
```
En la EC2 (con RabbitMQ ya levantado con `docker compose`), ejecutarlo **directamente en el host**, no en un contenedor
(así toma las credenciales del rol de la instancia sin problemas con el hop limit de IMDS):

```bash
export RABBITMQ_HOST=localhost RABBITMQ_USERNAME=pedidos360 RABBITMQ_PASSWORD='<clave>'
export PEDIDO_ACEPTADO_TOPIC_ARN=arn:aws:sns:us-east-1:<ACCOUNT_ID>:pedidos360-pedido-aceptado
export STOCK_RESULTADO_TOPIC_ARN=arn:aws:sns:us-east-1:<ACCOUNT_ID>:pedidos360-stock-resultado
java -jar rabbit-bridge.jar
```
Declara la misma topología que los servicios, se reconecta solo si RabbitMQ cae y espera al broker si arranca antes que él.
Necesita permiso `sns:Publish` (lo da el rol de la instancia, p. ej. `LabInstanceProfile`).

## Las 4 Lambdas

| Lambda | Jar | Handler | Disparador |
|---|---|---|---|
| `ms-catalog-api` | catalog | `com.pedidos360.catalog.aws.StreamLambdaHandler::handleRequest` | API Gateway |
| `ms-orders-api` | orders | `com.pedidos360.orders.aws.StreamLambdaHandler::handleRequest` | API Gateway |
| `ms-catalog-stock-consumer` | catalog | `com.pedidos360.catalog.aws.StockConsumerHandler::handleRequest` | SQS `pedidos360-catalog-stock-queue` |
| `ms-orders-stock-result-consumer` | orders | `com.pedidos360.orders.aws.StockResultHandler::handleRequest` | SQS `pedidos360-orders-stock-result-queue` |

Las Lambdas siguen dentro de la VPC y deben alcanzar la EC2 de RabbitMQ por su **IP privada, puerto 5672** (el security group de la EC2
debe permitir 5672 desde `sg-pedidos360-lambda`).

## Variables de entorno

| Variable | Valor | Lambdas |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `aws` | las 4 |
| `DB_PROXY_ENDPOINT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | los de **la RDS de ese servicio** (catalog o orders) | las 4 |
| `COGNITO_USER_POOL_ID` | `us-east-1_u6NeyPwe1` | las 4 |
| `ALLOWED_ORIGIN` | dominio de Amplify | las 2 API |
| `CATALOG_SERVICE_URL` | invoke URL del API Gateway (sin `/api`) | solo `ms-orders-api` |
| `RABBITMQ_HOST` | IP privada de la EC2 con RabbitMQ | `ms-orders-api`, `ms-catalog-stock-consumer` |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | usuario del broker (no `guest`) | `ms-orders-api`, `ms-catalog-stock-consumer` |
| `RABBITMQ_PORT` / `RABBITMQ_SSL` / `RABBITMQ_VHOST` | opcionales; en `aws` por defecto `5672` / `false` / `/` | las que usan `RABBITMQ_HOST` |

Ya **no existen** `ORDER_ACCEPTED_TOPIC_ARN` ni `STOCK_RESULT_TOPIC_ARN`: los servicios no publican en SNS; los ARN ahora los usa
solo `rabbit-bridge` (`PEDIDO_ACEPTADO_TOPIC_ARN`, `STOCK_RESULTADO_TOPIC_ARN`).
`ms-orders-stock-result-consumer` no publica eventos, así que no necesita variables `RABBITMQ_*`.
