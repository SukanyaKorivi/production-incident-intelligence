![Java](https://img.shields.io/badge/Java-25.0.4-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18.6-blue)
![Status](https://img.shields.io/badge/status-active-success)

# Production Incident Intelligence Engine

This backend service ingests application events, detects abnormal error
patterns per service in real time, correlates related events into a single
incident, and produces a structured, evidence-backed diagnosis — end to end,
with no manual intervention.


The system's root-cause reasoning is intentionally rule-based rather than
ML-driven: a design choice that keeps detection logic transparent and
explainable, the same approach many production observability tools actually
ship as v1. More advanced correlation (cross-service evidence linking,
dependency-aware root cause) is scoped for future iterations — see
[Future Improvements](#future-improvements) below.

**What this is:** a focused backend component that turns a noisy stream of
events into one structured, evidence-backed incident record — the part of
an incident-intelligence system that's actually hard.

**What this isn't (yet):** a full observability platform, an ML-based
anomaly detector, or a system with UI/dashboarding. Those are explicitly out
of scope for this iteration.

## Tech Stack

- Java 25.0.4 / Spring Boot 4.1.0
- PostgreSQL 18.6
- Spring Data JPA (Hibernate 7.4.1.Final)
- Spring `@Scheduled` for background jobs (detection + cleanup)
- Plain `java.net.http.HttpClient` synthetic event generator (no external deps)


## Text-based flow

```
EventSenderClient (synthetic generator)
        |
        v
   POST /events  ---------------------->  events table (incident_id = NULL)
                                                   |
                                                   v
                              Detection job (every N seconds)
                              - scans last 60s of ERROR events
                              - groups by service
                              - flags services over threshold
                                                   |
                                                   v
                              Correlation job
                              - creates an Incident row
                              - links matching Event rows via incident_id
                                                   |
                                                   v
                              Cleanup job (every N minutes)
                              - deletes stale events never linked to an incident
                              - never deletes incidents themselves
                                                   |
                                                   v
                       GET /incidents  /  GET /incidents/{id}
                       (thin list)        (incident + nested evidence events)
```



Ingestion and detection are deliberately separate concerns: `POST /events`
only ever validates and saves — it never judges whether an event is "bad."
All pattern-recognition logic lives in the scheduled jobs, which read from
the database independently of any HTTP request.

## Key Design Decisions

**Ingestion never judges content.** `POST /events` saves whatever valid
event it receives — an `ERROR` and an `INFO` event are both just "successful
writes" from the API's point of view. Deciding whether an event is
significant is entirely the detection job's responsibility. This keeps the
ingestion path fast, simple, and easy to reason about, and keeps the two
concerns testable independently.

**incident_id` starts `NULL` and is filled in later, never at creation.**
An event doesn't know what incident it belongs to when it's created — that's
only discoverable after the fact, once the detection/correlation jobs have
looked at a window of events together. Events are only ever retroactively
linked to an incident.

**Threshold-based detection instead of ML/statistical anomaly detection.**
A service crossing N errors in a fixed time window is treated as a
potential incident. This is a conscious scope decision: rule-based detection
is legitimate, explainable, and is genuinely what many real observability
tools ship as v1. Statistical/ML-based detection is listed under Future
Improvements rather than attempted here.

**Severity-based suppression, with a known limitation.** Low-severity-only
patterns are currently suppressed and don't generate an incident on their
own. During development I noticed a real gap in this approach: a low-severity
warning that happens *near in time* to a genuine incident (e.g. an
`auth-service` warning occurring seconds after a `payment-service` error
spike) is likely *related evidence*, not independent noise — but the current
suppression logic discards it rather than attaching it to the incident. I
chose to ship the simpler version and document this rather than build full
time-window cross-service correlation, to keep the project shippable within
scope. See Future Improvements.

**Detail endpoint returns nested evidence; list endpoint stays thin.**
`GET /incidents/{id}` returns the incident plus its full list of linked
events, because the evidence trail is the actual point of this project — a
caller viewing one incident wants the diagnosis and its evidence together,
not a second round trip. `GET /incidents` (the list) intentionally returns
summary fields only, since nesting every incident's full event list in a
list view doesn't scale and isn't what a list view is for.

**Raw events are pruned; incidents are permanent.** A scheduled cleanup job
deletes events that are older than a cutoff window *and* were never linked
to an incident — this is disposable noise. Incidents themselves are never
deleted; they're the system's structured memory of "something happened
here," which is the actual value this project produces.

**Fixed thresholds and windows, not yet configurable.** Detection window
(60s), error threshold, and cleanup cutoff (10min) are currently hardcoded
constants rather than externalized to `application.properties`. This was a
deliberate scope cut to prioritize finishing the core detect → correlate →
serve → cleanup loop end-to-end before adding configurability.

## Testing

Current verification is manual/functional: the included `EventSenderClient`
generates a repeatable traffic pattern (baseline load, then a targeted error
burst) so the full detect → correlate → serve → cleanup loop can be observed
end-to-end via console logs and the `/incidents` endpoints, as shown in
[Sample Output](#sample-output) below.

Automated unit and integration tests (e.g. for the detection threshold logic
and the correlation job's linking behavior) are not yet included and are the
next priority for this project ahead of further features.

## Future Improvements

- **Cross-service, time-window correlation of low-severity events.** Instead
  of suppressing low-severity patterns outright, pull in any event (any
  service, any severity) that falls within the same time window as a
  detected incident, and attach it as supporting evidence rather than
  discarding it.
- **Smarter root-cause heuristic.** Currently the flagged service *is* the
  incident's service. A more realistic heuristic would consider which
  service failed *first* across a correlated group, or use fan-in/fan-out
  across services as a signal.
- **Dependency-aware grouping.** Model which services depend on which, so a
  downstream service's errors can be attributed to an upstream root cause
  rather than treated as a separate incident.
- **LLM-generated diagnosis summaries.** Turn the structured incident +
  evidence list into a short human-readable narrative via an LLM call, as a
  presentation layer on top of the existing structured data — not a
  replacement for it.
- **Configurable thresholds.** Move detection window, error threshold, and
  cleanup cutoff into `application.properties` instead of hardcoded
  constants.
- **Automated test coverage.** Unit tests for detection/correlation logic
  and integration tests for the full event → incident pipeline.

## Configuration

This project does not ship with any credentials. Copy the example values
below into your own `application.properties` and fill in your local
PostgreSQL details — do not commit this file with real values.

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/DB_NAME
spring.datasource.username=DB_USER
spring.datasource.password=DB_PASSWORD
spring.jpa.hibernate.ddl-auto=update
```

Alternatively, reference environment variables instead of hardcoding values,
and set them in your shell or IDE run configuration:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
```

`application.properties` (or any file containing real credentials) should be
listed in `.gitignore` and never pushed to the repository.

## How to Run

**1. Start PostgreSQL and create the database.**

```bash
createdb DB_NAME
```

See [Configuration](#configuration) above for setting your connection
details.

**2. Start the Spring Boot application.**

```bash
mvn spring-boot:run
```

Wait until you see `Started ProductionIncidentIntelligenceApplication` in the
console — this confirms the API and the scheduled jobs (detection + cleanup)
are running. By default, Spring Boot serves the API on port `8080` unless
overridden via `server.port` in `application.properties`.

**3. In a separate terminal, run the synthetic event generator.**

```bash
javac EventSenderClient.java -d out
java -cp out EventSenderClient
```

This sends ~60 seconds of normal traffic across `payment-service`,
`auth-service`, and `inventory-service`, then fires a burst of `ERROR`
events targeting `payment-service` (switching to `auth-service` partway
through), simulating a cascading failure. It repeats this cycle
continuously.

**4. Query the API.**

```bash
# List all incidents
curl http://localhost:8080/incidents

# Get one incident with its full evidence trail
curl http://localhost:8080/incidents/1
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

*(Trimmed for readability — a real response includes every linked event in
the evidence array, as shown by the `id` count above.)*

## Author

**Korivi Sukanya**
Backend developer focused on Java / Spring Boot systems design.

- GitHub: [SukanyaKorivi](https://github.com/SukanyaKorivi)
- LinkedIn: https://www.linkedin.com/in/korivi-sukanya-700360270
