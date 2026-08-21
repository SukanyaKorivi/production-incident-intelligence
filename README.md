![Java](https://img.shields.io/badge/Java-25.0.4-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18.6-blue)
![Status](https://img.shields.io/badge/status-active-success)


# Production Incident Intelligence Engine

This backend service ingests application events, detects abnormal error patterns per service in real time, correlates related events into a single incident, and produces a structured, evidence-backed diagnosis — end to end, with no manual intervention.

The system's root-cause reasoning is intentionally rule-based rather than ML-driven: a design choice that keeps detection logic transparent and explainable, the same approach many production observability tools actually ship as v1. More advanced correlation (cross-service evidence linking, dependency-aware root cause) is scoped for future iterations — see Future Improvements below.

**What this is:** a focused backend component that turns a noisy stream of events into one structured, evidence-backed incident record — the part of an incident-intelligence system that's actually hard.

**What this isn't (yet):** a full observability platform, an ML-based anomaly detector, or a system with UI/dashboarding. Those are explicitly out of scope for this iteration.

## Tech Stack

- Java 25.0.4 / Spring Boot 4.1.0
- PostgreSQL 18.6
- Spring Data JPA (Hibernate 7.4.1.Final)
- Spring @Scheduled for background jobs (detection+correlation, cleanup)
- JUnit 5, Mockito, Spring @WebMvcTest — automated testing
- Docker (multi-stage build) — containerized deployment
- Plain java.net.http.HttpClient synthetic event generator (no external deps)

## Text-based flow

```
EventSenderClient (synthetic generator)
        |
        v
   POST /events  ---------------------->  events table (incident_id = NULL)
                                                   |
                                                   v
                                 Detection + correlation job (every 5s)
                                        - scans last 60s of ERROR events
                                        - groups by service
                                        - flags services with >= 10 errors in window
                                        - creates an Incident row and links matching Event rows
                                                   |
                                                   v
                                Cleanup job (every 10 min)
                                       - deletes stale events never linked to an incident
                                       - never deletes incidents themselves
                                                   |
                                                   v
                               GET /incidents  /  GET /incidents/{id}
                               (thin list)        (incident + nested evidence events)
```

Ingestion and detection are deliberately separate concerns: `POST /events` only ever validates and saves — it never judges whether an event is "bad." All pattern-recognition logic lives in the scheduled jobs, which read from the database independently of any HTTP request.

## Key Design Decisions

**Ingestion never judges content.** `POST /events` saves whatever valid event it receives — an ERROR and an INFO event are both just "successful writes" from the API's point of view. Deciding whether an event is significant is entirely the detection job's responsibility. This keeps the ingestion path fast, simple, and easy to reason about, and keeps the two concerns testable independently.

**incident_id` starts `NULL` and is filled in later, never at creation.** An event doesn't know what incident it belongs to when it's created — that's only discoverable after the fact, once the detection/correlation job has looked at a window of events together. Events are only ever retroactively linked to an incident.

**Threshold-based detection instead of ML/statistical anomaly detection.** A service crossing 10 errors in a 60-second window is treated as a potential incident. This is a conscious scope decision: rule-based detection is legitimate, explainable, and is genuinely what many real observability tools ship as v1. Statistical/ML-based detection is listed under Future Improvements rather than attempted here.

**Severity-based suppression, with a known limitation.** Low-severity-only patterns are currently suppressed and don't generate an incident on their own. During development I noticed a real gap in this approach: a low-severity warning that happens near in time to a genuine incident (e.g. an auth-service warning occurring seconds after a payment-service error spike) is likely related evidence, not independent noise — but the current suppression logic discards it rather than attaching it to the incident. I chose to ship the simpler version and document this rather than build full time-window cross-service correlation, to keep the project shippable within scope. See Future Improvements.

**Detail endpoint returns nested evidence; list endpoint stays thin.** `GET /incidents/{id}` returns the incident plus its full list of linked events, because the evidence trail is the actual point of this project — a caller viewing one incident wants the diagnosis and its evidence together, not a second round trip. `GET /incidents` (the list) intentionally returns summary fields only, since nesting every incident's full event list in a list view doesn't scale and isn't what a list view is for.

**Raw events are pruned; incidents are permanent.** A scheduled cleanup job deletes events that are older than a cutoff window and were never linked to an incident — this is disposable noise. Incidents themselves are never deleted; they're the system's structured memory of "something happened here," which is the actual value this project produces.

**Fixed thresholds and windows, not yet configurable.** Detection window (60s), error threshold (10), and cleanup cutoff (10min) are currently hardcoded constants rather than externalized to `application.properties`. This was a deliberate scope cut to prioritize finishing the core detect → correlate → serve → cleanup loop end-to-end before adding configurability.

## Testing
 
The project has automated test coverage in addition to manual/functional verification:
 
**9 unit and web-layer tests** written with JUnit 5, Mockito, and Spring `@WebMvcTest`,
across 3 test classes:
 
- **`EventServiceTest`** (unit tests, Mockito-mocked repositories) — detection logic
  flags a service when it hits ≥10 errors in the 60-second window and creates a
  linked Incident; does *not* flag a service below that threshold; cleanup job
  correctly delegates stale, uncorrelated-event deletion to the repository.
- **`EventControllerTest`** (`@WebMvcTest`) — `POST /events` rejects a missing or
  blank `serviceName` with `400`, and persists a valid event with `201 Created`.
- **`IncidentControllerTest`** (`@WebMvcTest`) — `GET /incidents` returns the list
  view; `GET /incidents/{id}` returns the incident with its nested evidence when
  found, and `404` when no incident matches the given id.
Run the test suite with:
 
```bash
mvn test
```
 
Manual/functional verification is also available: the included `EventSenderClient`
generates a repeatable traffic pattern (baseline load, then a targeted error burst)
so the full detect → correlate → serve → cleanup loop can be observed end-to-end via
console logs and the `/incidents` endpoints, as shown in
[Sample Output](#sample-output) below.
 
## Docker
 
The application is containerized with a multi-stage Dockerfile that separates
the Maven build image from a lean runtime image, producing a portable,
reproducible deployment artifact.
 
**Security note:** `application.properties` is intentionally excluded from
version control (see `.gitignore`). For Docker, credentials are injected at
`docker run` time via environment variables rather than baked into the
image — a deliberate choice to avoid shipping secrets inside image layers.
Do not create `application.properties` locally before running `docker build`
if you plan to share or push the resulting image; that file's contents would
get copied into the image layer. Keep `application.properties` for local
`mvn spring-boot:run` use only, and use the environment-variable flow below
for Docker.
 
**Build the image:**
 
```bash
docker build -t incident-intelligence-engine .
```
 
**Run the container**, passing the database connection as environment
variables — Spring Boot maps these automatically, no properties file needed:
 
```bash
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<db_name> \
  -e SPRING_DATASOURCE_USERNAME=<db_user> \
  -e SPRING_DATASOURCE_PASSWORD=<db_password> \
  incident-intelligence-engine
```
 
Alternatively, put the same three variables in a local `.env` file (not
committed) and run:
 
```bash
docker run -p 8080:8080 --env-file .env incident-intelligence-engine
```
 
> Note: if your PostgreSQL instance runs on your host machine rather than in
> a container, `localhost` inside the container refers to the container
> itself, not your host. Use `host.docker.internal` (Docker Desktop on
> Mac/Windows) or a shared Docker network/`docker-compose` setup instead.
 

## Future Improvements

- **Cross-service, time-window correlation of low-severity events.** Instead of suppressing low-severity patterns outright, pull in any event (any service, any severity) that falls within the same time window as a detected incident, and attach it as supporting evidence rather than discarding it.
- **Smarter root-cause heuristic.** Currently the flagged service is the incident's service. A more realistic heuristic would consider which service failed first across a correlated group, or use fan-in/fan-out across services as a signal.
- **Dependency-aware grouping.** Model which services depend on which, so a downstream service's errors can be attributed to an upstream root cause rather than treated as a separate incident.
- **LLM-generated diagnosis summaries.** Turn the structured incident + evidence list into a short human-readable narrative via an LLM call, as a presentation layer on top of the existing structured data — not a replacement for it.
- **Configurable thresholds.** Move detection window, error threshold, and cleanup cutoff into `application.properties` instead of hardcoded constants.
- **Integration tests.** Full event → incident pipeline integration tests (currently only unit and web-layer tests exist) using an embedded/test database.

## Configuration

This project does not ship with any credentials. Copy the example values below into your own `application.properties` and fill in your local PostgreSQL details — do not commit this file with real values.

```
spring.datasource.url=jdbc:postgresql://localhost:5432/DB_NAME
spring.datasource.username=DB_USER
spring.datasource.password=DB_PASSWORD
spring.jpa.hibernate.ddl-auto=update
```

Alternatively, reference environment variables instead of hardcoding values, and set them in your shell or IDE run configuration:

```
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
```

`application.properties` (or any file containing real credentials) should be listed in `.gitignore` and never pushed to the repository.

## How to Run

**1. Start PostgreSQL and create the database.**

```
createdb DB_NAME
```

See Configuration above for setting your connection details.

**2. Start the Spring Boot application.**

```
mvn spring-boot:run
```

Wait until you see `Started ProductionIncidentIntelligenceApplication` in the console — this confirms the API and the scheduled jobs (detection+correlation, cleanup) are running. By default, Spring Boot serves the API on port 8080 unless overridden via `server.port` in `application.properties`.

**3. In a separate terminal, run the synthetic event generator.**

```
javac EventSenderClient.java -d out
java -cp out EventSenderClient
```

This sends ~60 seconds of normal traffic across `payment-service`, `auth-service`, and `inventory-service`, then fires a burst of ERROR events targeting `payment-service` (switching to `auth-service` partway through), simulating a cascading failure. It repeats this cycle continuously.

**4. Query the API.**

```
# List all incidents
curl http://localhost:PORT/incidents

# Get one incident with its full evidence trail
curl http://localhost:PORT/incidents/1
```

**5. (Optional) Run via Docker instead of steps 2–3.**

```
docker build -t incident-intelligence-engine .
docker run -p 8080:8080 --env-file .env incident-intelligence-engine
```

## Sample Output

Console, during a detected burst:

```
warning suppressed due to Low severity in auth-service
Potential incident detected in payment-service : 15 errors found in last 60 seconds
CLEAN UP: Deleted 18 stale events
warning suppressed due to Low severity in auth-service
```

`GET /incidents/381` response:

```json
{
  "incident": {
    "id": 381,
    "serviceName": "payment-service",
    "severity": "CRITICAL",
    "cause": "DATABASE CONNECTION FAILURE:Pool exhausted",
    "status": "OPEN",
    "createdAt": "2026-08-20T11:18:30.518909Z"
  },
  "evidence": [
    {
      "id": 5613,
      "incidentId": 381,
      "timestamp": "2026-08-20T11:18:30.047198Z",
      "serviceName": "payment-service",
      "logLevel": "ERROR",
      "message": "DATABASE CONNECTION FAILURE:Pool exhausted"
    },
    {
      "id": 5628,
      "incidentId": 381,
      "timestamp": "2026-08-20T11:18:30.109005Z",
      "serviceName": "auth-service",
      "logLevel": "ERROR",
      "message": "AUTHENTICATION SERVER TIMEOUT"
    }
  ]
}
```

(Trimmed for readability — a real response includes every linked event in the evidence array, as shown by the id count above.)

## Author
**Korivi Sukanya**
Backend developer focused on Java / Spring Boot systems design.

- GitHub: [SukanyaKorivi](https://github.com/SukanyaKorivi)
- LinkedIn: https://www.linkedin.com/in/korivi-sukanya-700360270
