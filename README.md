# BankX – transactions-service

**Caso:** BankX – Microservicio de Movimientos y Riesgo

**Stack:** Spring Boot 3 · WebFlux · MongoDB (reactivo) · JPA/H2 (legado) · Resilience4j · Log4j2

## 0) Contrato

### Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/transactions` | Crea un movimiento. Body: `accountNumber`, `type` (DEBIT/CREDIT), `amount` |
| GET | `/api/transactions?accountNumber=` | Lista movimientos de la cuenta |
| GET | `/api/stream/transactions` | Flujo SSE de movimientos en vivo |
| GET | `/actuator/health` | Health general |
| GET | `/actuator/health/readiness` · `/liveness` | Probes para Kubernetes |

### Errores homogéneos

Todos devuelven `{"error": "<codigo>"}` con HTTP 400:

`account_not_found` · `risk_rejected` · `insufficient_funds` · `validation_error`

### Correlación

`X-Correlation-Id` se acepta en la petición y **se devuelve en la respuesta**. Si no
viene, se genera un UUID. Se propaga al MDC de Log4j2 como `corrId` y aparece en
cada línea de log JSON.

### Campo `riskSource`

Cada transacción registra el origen de la decisión de riesgo:

| Valor | Significado |
|---|---|
| `REMOTE` | Respondió el servicio remoto de riesgo |
| `LEGACY_FALLBACK` | El remoto falló o excedió el timeout; resolvió el módulo legado H2 |

Es lo que hace observable al circuit breaker desde la propia respuesta.

### Simulación de fallos

Mediante cabeceras, para poder ejercitar resiliencia sin tocar el backend:

| Cabecera | Efecto |
|---|---|
| `X-Risk-Fail: true` | Fuerza un error en el servicio remoto |
| `X-Risk-DelayMs: 1500` | Introduce latencia; por encima de 1 s dispara el TimeLimiter |

## 1) Requisitos y Herramientas

* **Java 17**
* **Maven 3.9.11**
* **Docker** para levantar **MongoDB 6** y  **SonarQube**
* **IntelliJ**
* **Postman**

## 2) Configuración MongoDB

### 2.1. Levantar MongoDB con Docker

```bash
docker rm -f mongo-bankx 2>/dev/null || true
docker run -d --name mongo-bankx -p 27017:27017 mongo:6
```

### 2.2. Probar

Por defecto la app usa:

* **Mongo:** `mongodb://localhost:27017/bankx`

* La configuración de acceso a MongoDb está en el `application.yml`

## 3) Configuración SonarQube

### 31. Levantar SonarQube con Docker

```bash
docker rm -f sonar 2>$null
docker run -d --name sonar -p 9000:9000 sonarqube:lts-community
```
### 3.2. Probar
* **Accede a** http://localhost:9000 (credenciales: admin / admin).

## 4) Pruebas (Sesion 09 y 10)

### 4.1 Crear transacción (OK)

```bash
curl -L 'http://localhost:8084/api/transactions' -H 'Content-Type: application/json' -H 'X-Correlation-Id: misaki-123' -d '{"accountNumber":"001-0001","type":"DEBIT","amount":100}'
```

### 4.2 Riesgo (rechazo)

```bash
curl -L 'http://localhost:8084/api/transactions' -H 'Content-Type: application/json' -d '{"accountNumber":"001-0001","type":"DEBIT","amount":2000}'
```

### 4.4 Cuenta no encontrada (rechazo)

```bash
curl -L 'http://localhost:8084/api/transactions' -H 'Content-Type: application/json' -d '{"accountNumber":"001-0003","type":"DEBIT","amount":2000}'
```

### 4.5 Listar por cuenta

```bash
curl -L 'http://localhost:8084/api/transactions?accountNumber=001-0001'
```

### 4.6 SSE - Notificación de transacciones en tiempo real

```bash
curl -L 'http://localhost:8084/api/stream/transactions'
```
## 5) Pruebas (Sesion 14 y 15)

### 5.1 Crear transacción (OK)

```bash
curl -L 'http://localhost:8084/api/transactions' -H 'Content-Type: application/json' -H 'X-Correlation-Id: misaki-123' -d '{"accountNumber":"001-0001","type":"DEBIT","amount":100}'
```

### 5.2 Crear transacción (fail=true)

```bash
curl -L 'http://localhost:8084/api/transactions' -H 'Content-Type: application/json' -H 'X-Correlation-Id: misaki-123' -H 'X-Risk-Fail: true' -d '{"accountNumber":"001-0001","type":"DEBIT","amount":100}'
```

### 5.2 Crear transacción (delayMs=1500)

```bash
curl -L 'http://localhost:8084/api/transactions' -H 'Content-Type: application/json' -H 'X-Correlation-Id: misaki-123' -H 'X-Risk-Fail: true' -H 'X-Risk-DelayMs: 1500' -d '{"accountNumber":"001-0001","type":"DEBIT","amount":100}'
```

## 6) Calidad

### 6.1. Ejecutar tests + reportes

```bash
mvn clean verify
```
* **JaCoCo:** target/site/jacoco/index.html _(Objetivo del laboratorio ≥ 70% líneas y ≥ 60% branches)_

* **Checkstyle:** integrado en mvn verify (usa google_checks.xml)

### 6.1. SonarQube
#### 6.1.1 Levantar en Navegador

```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:lts-community
```
#### 6.1.2 Ejecutar Analisis

```bash
export SONAR_TOKEN=<tu-token>

mvn clean verify sonar:sonar \
  -Dsonar.token=$SONAR_TOKEN \
  -Dsonar.host.url=http://localhost:9000
```

> El token **nunca** se escribe en el `pom.xml` ni en ningún archivo versionado.
> `sonar.login` está deprecado; la propiedad vigente es `sonar.token`.
#### 6.1.3. Revisar Resultado
* Accede a _http://localhost:9000_
