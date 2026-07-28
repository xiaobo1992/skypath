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

- **Frontend**: Next.js 16 / React 19, App Router, TypeScript, CSS Modules
  (no UI/CSS framework dependency).
- **Backend**: Micronaut 5 (Netty runtime), Java 25, Hibernate JPA via
  `micronaut-data-hibernate-jpa`, Flyway migrations, Postgres driver + HikariCP.
- **Database**: Postgres 16, schema managed by Flyway (not Hibernate
  auto-ddl — `hibernate.hbm2ddl.auto=validate` only checks the schema matches).
- **Search strategy**: itineraries are **precomputed**, not traversed live.
  A startup job walks the flight graph once, applies all connection rules,
  and writes every valid direct/1-stop/2-stop itinerary into its own table.
  The search endpoint is then a plain indexed read + sort — no graph logic
  at request time. See "Itinerary precompute" below.
- **Orchestration**: `docker-compose.yml` at repo root starts all three
  services; `flights.json` is bind-mounted read-only into the backend
  container at `/app/flights.json` (env var `FLIGHTS_DATA_PATH`), though
  nothing reads it at runtime yet (see "still open").

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
│       ├── Application.java                     # Micronaut entrypoint (Micronaut.run)
│       ├── controller/
│       │   ├── HealthController.java            # GET /health -> {status, service}
│       │   ├── AirportController.java           # GET /airports -> List<AirportResponse>
│       │   └── ItineraryController.java         # GET /itineraries?origin&destination&date
│       ├── dto/
│       │   ├── AirportResponse.java              # @Serdeable record, entity -> API shape
│       │   ├── FlightSegmentResponse.java        # one flight leg, entity -> API shape
│       │   └── ItineraryResponse.java            # segments + layovers + totals
│       ├── entity/
│       │   ├── Airport.java                      # @Entity, PK = IATA code (String)
│       │   ├── Flight.java                       # @Entity, ManyToOne -> Airport (origin/destination)
│       │   └── Itinerary.java                    # @Entity, maps to precomputed `itineraries` table
│       ├── exception/
│       │   └── InvalidSearchExceptionHandler.java # IllegalArgumentException -> 400 {"error": "..."}
│       ├── repository/
│       │   ├── AirportRepository.java            # CrudRepository<Airport, String>
│       │   ├── FlightRepository.java             # CrudRepository<Flight, Long>
│       │   └── ItineraryRepository.java          # + derived search/sort query
│       └── service/
│           ├── ItineraryPrecomputeService.java        # builds the itineraries table (see below)
│           ├── ItineraryPrecomputeStartupListener.java # runs it once on every boot
│           └── ItinerarySearchService.java             # validation + repository read -> DTOs
│       resources/
│       ├── application.properties      # server port, CORS, datasource, flyway, jpa config
│       ├── logback.xml
│       └── db/migration/
│           ├── V1__create_airports_and_flights.sql   # Flyway baseline schema
│           ├── V2__seed_airports_and_flights.sql      # data generated from flights.json
│           └── V3__create_itineraries.sql             # precomputed itinerary table (see below)
│   └── src/test/java/com/skypath/BackendTest.java     # smoke test: app boots
└── frontend/
    └── src/
        ├── app/
        │   ├── layout.tsx           # root layout, metadata, fonts
        │   ├── page.tsx             # 'use client' — search page: state, fetch, loading/empty/error UI
        │   ├── page.module.css
        │   └── globals.css          # design tokens (--surface, --border, --accent, etc.), light/dark
        ├── components/
        │   ├── SearchForm.tsx        # origin/destination/date inputs + client-side validation
        │   ├── ItineraryList.tsx     # renders a list of ItineraryCard
        │   └── ItineraryCard.tsx     # one itinerary: segments, layovers, duration, price
        └── lib/
            ├── api.ts                # typed fetch client (fetchAirports, searchItineraries, ApiError)
            └── format.ts             # time/date/duration/price formatting (timezone-safe, see below)
```

## Backend details

- **Entities** (`entity/`): `Airport` is keyed by its 3-letter `code`.
  `Flight` and `Itinerary` store all times as `LocalDateTime` (i.e.
  **local airport time, no offset stored** — matches the dataset format).
  Timezone conversion for layover/duration math is done in code using
  `Airport.timezone` (an IANA zone id, e.g. `America/New_York`), not from the
  timestamp itself.
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
- **Itineraries schema** (`db/migration/V3__create_itineraries.sql`): one
  wide table (`origin_code`, `destination_code`, `departure_date`,
  `stop_count`, `flight_1_id`..`flight_3_id`, `layover_1_minutes`,
  `layover_2_minutes`, `departure_time`, `arrival_time`,
  `total_duration_minutes`, `total_price`). `flight_2_id`/`flight_3_id` and
  the corresponding layover columns are nullable, with a CHECK constraint
  (`chk_stop_count_matches_flights`) tying their null-ness to `stop_count`
  so the two can never drift apart. A composite index on
  `(origin_code, destination_code, departure_date, total_duration_minutes)`
  covers the search endpoint's filter + sort in one index scan.
- **Itinerary precompute** (`service/ItineraryPrecomputeService.java` +
  `ItineraryPrecomputeStartupListener.java`): runs once per boot via a
  Micronaut `StartupEvent` listener. Loads all flights, groups them by
  origin airport, and walks direct → 1-stop → 2-stop combinations using that
  index (not a full cross product). For each candidate connection it:
  - Converts both the arriving flight's arrival and the departing flight's
    departure to `Instant` via each airport's IANA timezone, and computes
    the layover from that (never from the raw local-time strings).
  - Classifies the connection domestic/international by checking whether
    the arriving flight's origin country and the departing flight's
    destination country both match the connecting airport's country —
    this is the concrete rule chosen to satisfy instructions.md's
    "JFK→ORD→LAX = domestic, JFK→LHR→CDG = international" example; it's an
    interpretation, not something the spec spells out mechanically, so
    worth re-checking if the take-home is discussed in an interview.
  - Applies the 45/90-minute minimum and 6-hour maximum layover rules.
  - Rejects any 2nd/3rd leg that would revisit an airport already on the
    itinerary (not explicitly required by the spec, but prevents
    nonsensical loop itineraries like A→B→A→C).
  - `precompute()` truncates and rebuilds the whole table every boot
    (`itineraryRepository.deleteAll()` then `saveAll(...)`) — fine at this
    dataset size (~2.8k itineraries from 302 flights), and correct since
    Flyway seed data doesn't change at runtime. Runs inside a single
    `@Transactional` method so the Hibernate session (and therefore the
    lazily-loaded `Airport`/`Flight` associations it touches while walking
    the graph) stays open for the whole computation.
- **Search endpoint** (`ItineraryController` → `ItinerarySearchService`):
  `GET /itineraries?origin=&destination=&date=`. `ItinerarySearchService`
  uppercases both codes, returns an empty list for same-airport searches
  (test case: `JFK→JFK`), throws `IllegalArgumentException` for an unknown
  code (caught by `InvalidSearchExceptionHandler` → `400 {"error": "..."}`
  instead of the framework's default 500), then does one indexed repository
  read sorted by `total_duration_minutes` and maps entities to
  `ItineraryResponse` DTOs. Also `@Transactional`, for the same
  lazy-association reason as the precompute service — `ItineraryResponse.
  fromEntity` touches `Flight`/`Airport` associations on the `Itinerary`
  entity, which throws `LazyInitializationException` outside an open
  session.
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
  `@Serdeable` to be serializable at all). `ItineraryResponse` is also
  annotated `@JsonInclude(Include.ALWAYS)` — without it, Micronaut Serde's
  default inclusion policy silently drops the `layoverMinutes` key entirely
  from the JSON for direct (0-layover) itineraries instead of emitting
  `[]`, which broke the frontend (see below). Worth remembering if another
  DTO gets an optionally-empty collection field.

## Frontend details

- **Data flow**: everything is client-side. `app/page.tsx` is a `'use
  client'` component that fetches `GET /airports` on mount (to populate the
  origin/destination `<datalist>`) and calls `searchItineraries(...)` from
  `lib/api.ts` on form submit — no Next.js server-side fetching or route
  handlers involved, since `NEXT_PUBLIC_API_BASE_URL` is a browser-facing
  URL (`http://localhost:8080`) that wouldn't resolve correctly from
  inside the frontend *container* if used in a Server Component.
- **`lib/api.ts`**: typed fetch wrappers (`fetchAirports`,
  `searchItineraries`) plus an `ApiError` class. Non-OK responses are
  parsed for either `{"error": "..."}` (our custom handler) or
  `{"message": "..."}` (Micronaut's default validation error shape for
  things like a missing query param) and re-thrown as `ApiError` so the UI
  always has a human-readable message.
- **`lib/format.ts`**: `formatTime`/`formatDate`/`formatDateTime` parse the
  API's `LocalDateTime` strings (e.g. `"2024-03-15T08:30:00"`) by splitting
  the string manually rather than via `new Date(...)`, since that string
  has no timezone offset and JS would interpret it as **browser-local**
  time — wrong, because it's actually local to whichever airport it
  belongs to. `formatDateTime` always shows the date alongside the time
  (not just the time) because a segment's departure and arrival, or two
  segments across a connection, can legitimately land on different
  calendar dates (overnight flights, or the `SYD→LAX` date-line-crossing
  case from instructions.md where arrival local-time is earlier than
  departure local-time on paper).
- **`SearchForm`**: plain `<input>`s with a shared `<datalist
  id="airport-codes">` for lightweight autocomplete (no UI library added).
  Client-side validates: both codes present, both are known airport codes
  (once the airport list has loaded), origin ≠ destination, date selected —
  covers instructions.md test cases #4/#5 in the UI before they'd even hit
  the API.
- **Results states** (`app/page.tsx`): loading (`isSearching`), empty
  (`results !== null && results.length === 0`), and error
  (`ApiError.message` surfaced directly) are all handled explicitly, per
  the spec's UX requirements.
- **Known Docker gotcha hit this session**: `docker compose up --build`
  does **not** reliably recreate an already-running container even when
  the underlying image changed — verify with
  `docker inspect <container> --format '{{.Image}}'` vs. `docker images
  <name>:latest --format '{{.ID}}'` if changes don't seem to be taking
  effect, and use `docker compose up -d --force-recreate <service>` to
  force it.

## What's implemented vs. still open

Done: DB schema + Flyway migrations (airports/flights/itineraries),
itinerary precompute job (direct/1-stop/2-stop, layover + timezone +
domestic-international rules), `GET /airports`, `GET /itineraries` search
endpoint with validation and error handling, full frontend (search form,
results list with segments/layovers/duration/price, loading/empty/error
states, light + dark mode), Docker Compose wiring end-to-end.

Not yet implemented / worth revisiting:
- The seed data is baked into a migration rather than read from
  `flights.json` at runtime — `FLIGHTS_DATA_PATH` /
  `./flights.json:/app/flights.json:ro` in `docker-compose.yml` are wired up
  but nothing reads that path yet. Fine for this fixed dataset, but if
  `flights.json` should be swappable without a new migration, a runtime
  loader would be needed instead (or in addition) — and the itinerary
  precompute would need to run after that load rather than only on the
  `StartupEvent`.
- The domestic/international connection rule (see "Itinerary precompute"
  above) is one reasonable reading of instructions.md's example, not a
  mechanically-specified rule — worth double-checking.
- No automated tests beyond the one backend smoke test (`BackendTest.java`
  — app boots). No frontend tests. No test coverage for the precompute
  algorithm's edge cases (date-line crossing, no-revisit rule, layover
  boundary values) beyond manual/browser verification done this session.
- No pagination/limit on `GET /itineraries` — fine at this dataset size
  (BOS→SEA style multi-stop searches return a few dozen results, worst
  case), but would matter at a larger scale.
- `README.md` (required by instructions.md: how to run, architecture
  decisions, tradeoffs, what's next) hasn't been written yet.

## Running locally

```bash
docker-compose up
```
Frontend → http://localhost:3000, backend → http://localhost:8080,
Postgres → localhost:5432 (db/user/pass: `skypath`/`skypath`/`skypath`).

Backend alone: `cd backend && ./gradlew run` (needs `datasources.default.url`
etc. supplied via env, matching the compose file, since there's no local
Postgres fallback configured). Note: compiling/running the backend directly
(outside Docker) requires a local **Java 25** toolchain — this repo's Docker
image pins `eclipse-temurin:25-jdk`/`25-jre`, but that version may not be
installed on the host machine.

Frontend alone: `cd frontend && npm run dev` (needs
`NEXT_PUBLIC_API_BASE_URL` pointing at a running backend, e.g.
`http://localhost:8080`).
