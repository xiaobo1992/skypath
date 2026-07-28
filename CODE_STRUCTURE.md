# SkyPath — Code Structure & Notes

Reference doc for navigating this repo and picking work back up later. See
[instructions.md](instructions.md) for the full take-home spec (flight
connection search engine: origin/destination/date → direct + 1-stop + 2-stop
itineraries, with layover and timezone rules).

## Stack & high-level architecture

```
frontend (Next.js, TS)  --HTTP-->  backend (Micronaut, Java 25)  -->  Postgres
        :3000                              :8080                       :5432
```

- **Frontend**: Next.js 16 / React 19, App Router, TypeScript.
- **Backend**: Micronaut 5 (Netty runtime), Java 25, Hibernate JPA via
  `micronaut-data-hibernate-jpa`, Flyway migrations, Postgres driver + HikariCP.
- **Database**: Postgres 16, schema managed by Flyway (not Hibernate
  auto-ddl — `hibernate.hbm2ddl.auto=validate` only checks the schema matches).
- **Orchestration**: `docker-compose.yml` at repo root starts all three
  services; `flights.json` is bind-mounted read-only into the backend
  container at `/app/flights.json` (env var `FLIGHTS_DATA_PATH`).

## Directory layout

```
skypath/
├── flights.json                # seed dataset: airports[] + flights[] (~260 flights, 25 airports)
├── instructions.md             # the take-home challenge spec — source of truth for requirements
├── docker-compose.yml          # postgres + backend + frontend
├── backend/
│   ├── build.gradle            # Micronaut app plugin, deps, Java 25 toolchain
│   ├── Dockerfile
│   └── src/main/java/com/skypath/
│       ├── Application.java            # Micronaut entrypoint (Micronaut.run)
│       ├── controller/
│       │   ├── HealthController.java   # GET /health -> {status, service}
│       │   └── AirportController.java  # GET /airports -> List<AirportResponse>
│       ├── dto/
│       │   └── AirportResponse.java    # @Serdeable record, entity -> API shape
│       ├── entity/
│       │   ├── Airport.java            # @Entity, PK = IATA code (String)
│       │   └── Flight.java             # @Entity, ManyToOne -> Airport (origin/destination)
│       └── repository/
│           ├── AirportRepository.java  # CrudRepository<Airport, String>
│           └── FlightRepository.java   # CrudRepository<Flight, Long>
│       resources/
│       ├── application.properties      # server port, CORS, datasource, flyway, jpa config
│       ├── logback.xml
│       └── db/migration/
│           ├── V1__create_airports_and_flights.sql   # Flyway baseline schema
│           └── V2__seed_airports_and_flights.sql      # data generated from flights.json
│   └── src/test/java/com/skypath/BackendTest.java     # smoke test: app boots
└── frontend/
    └── src/app/                # default create-next-app scaffold — not yet built out
        ├── layout.tsx
        └── page.tsx
```

## Backend details

- **Entities** (`entity/`): `Airport` is keyed by its 3-letter `code`.
  `Flight` stores `departureTime`/`arrivalTime` as `LocalDateTime` (i.e.
  **local airport time, no offset stored** — matches the dataset format).
  Timezone conversion for layover/duration math has to be done in code using
  `Airport.timezone` (an IANA zone id, e.g. `America/New_York`), not from the
  timestamp itself.
- **Repositories** (`repository/`): currently bare `CrudRepository` interfaces
  with no custom queries yet. The search feature will need query methods
  (e.g. find flights by origin + date range) or a service-layer graph
  traversal over an in-memory index.
- **Schema** (`db/migration/V1__...sql`): `airports` (PK `code`) and
  `flights` (auto-increment PK, FKs to `airports` on both origin and
  destination, indexes on `origin_code`, `destination_code`,
  `departure_time`, and a composite `(origin_code, destination_code,
  departure_time)` index sized for route+date lookups).
- **Seed data** (`db/migration/V2__seed_airports_and_flights.sql`): a
  Flyway migration that inserts all 25 airports and 302 of the 303 flights
  from `flights.json` directly (generated once from the JSON, not loaded at
  runtime — see "still open" below for the alternative). One row
  (`SP995`, origin `JKF`) is intentionally skipped: `JKF` isn't in the
  25-airport list (almost certainly a typo of `JFK` in the source data), and
  inserting it would violate the `fk_flights_origin` foreign key. This is one
  of the dataset's known "quirks" per `instructions.md` — noted in a comment
  at the bottom of the migration file rather than silently dropped.
- **Config** (`application.properties`): CORS is locked to
  `http://localhost:3000` (the frontend dev/compose origin). Flyway runs
  migrations on startup (`baseline-on-migrate=true`); Hibernate is set to
  `validate` only, so schema changes must go through a new Flyway migration
  file, not entity annotation changes alone.
- **Health check**: `endpoints.health.enabled=false` disables Micronaut's
  built-in `/health` management endpoint — `HealthController` is a hand-rolled
  replacement at the same path.
- **DTOs** (`dto/`): API responses use `@Serdeable` records rather than
  serializing JPA entities directly (the project only has
  `micronaut-serde-jackson`, not Jackson databind, so DTOs need
  `@Serdeable` to be serializable at all). `AirportController` maps
  `Airport` → `AirportResponse` via `AirportResponse.fromEntity(...)`.
  Follow the same pattern for a future `FlightResponse` /
  itinerary response shape rather than exposing `Flight` directly (it holds
  lazy `@ManyToOne` associations that would need explicit DTO mapping
  anyway).

## What's implemented vs. still open

Done: DB schema + Flyway migrations (schema + seed data), JPA
entities/repositories for airports/flights, Docker Compose wiring, health
endpoint, `GET /airports` listing endpoint (now returns real data since
`V2__seed_airports_and_flights.sql` runs on startup).

Not yet implemented (per `instructions.md` requirements):
- The seed data is baked into a migration rather than read from
  `flights.json` at runtime — `FLIGHTS_DATA_PATH` /
  `./flights.json:/app/flights.json:ro` in `docker-compose.yml` are wired up
  but nothing reads that path yet. Fine for this fixed dataset, but if
  `flights.json` should be swappable without a new migration, a runtime
  loader would be needed instead (or in addition).
- The actual search endpoint (origin/destination/date → itineraries) and the
  connection-building logic (direct / 1-stop / 2-stop, layover min/max,
  domestic vs. international 45/90-minute rule, same-airport-only
  connections, timezone-aware duration calc, sort by total travel time).
- Frontend is still the default `create-next-app` starter page — no search
  form or results UI yet.
- No API client / fetch layer in the frontend, and no
  `NEXT_PUBLIC_API_BASE_URL` usage yet (the env var is already passed in via
  `docker-compose.yml`).

## Running locally

```bash
docker-compose up
```
Frontend → http://localhost:3000, backend → http://localhost:8080,
Postgres → localhost:5432 (db/user/pass: `skypath`/`skypath`/`skypath`).

Backend alone: `cd backend && ./gradlew run` (needs `datasources.default.url`
etc. supplied via env, matching the compose file, since there's no local
Postgres fallback configured).
