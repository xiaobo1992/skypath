# SkyPath

A flight connection search engine: given an origin, destination, and date, it
returns valid direct, 1-stop, and 2-stop itineraries — with layover and
timezone rules enforced — sorted by total travel time. Built for the
take-home spec in [instructions.md](instructions.md).

## Stack

```
frontend (Next.js, TypeScript)  --HTTP-->  backend (Micronaut, Java 25)  -->  Postgres
        :3000                                      :8080                       :5432
```

- **Frontend** — Next.js 16 / React 19, App Router, TypeScript, CSS Modules.
- **Backend** — Micronaut 5 (Netty), Java 25, Hibernate JPA, Flyway migrations.
- **Database** — Postgres 16.
- **Orchestration** — Docker Compose runs all three services together.

## Project structure

```
skypath/
├── flights.json          # seed dataset (airports + flights)
├── instructions.md       # the take-home spec
├── CODE_STRUCTURE.md      # deeper reference: file-by-file notes, algorithm details, gotchas
├── docker-compose.yml
├── backend/
│   └── src/main/java/com/skypath/
│       ├── controller/    # REST endpoints (/health, /airports, /itineraries)
│       ├── service/       # interface + Impl per service (airport lookup, itinerary precompute job, itinerary search)
│       ├── repository/    # Micronaut Data repositories
│       ├── entity/        # JPA entities (Airport, Flight, Itinerary)
│       ├── dto/            # API response shapes
│       ├── exception/      # error -> HTTP response mapping
│       └── resources/db/migration/  # Flyway schema + seed data
└── frontend/
    └── src/
        ├── app/            # search page (form + results, loading/empty/error states)
        ├── components/     # SearchForm, ItineraryList, ItineraryCard, LoadingBar
        └── lib/            # API client + timezone-safe formatting helpers
```

For a file-by-file breakdown (what each class does, the precompute
algorithm's rules, and gotchas hit along the way), see
[CODE_STRUCTURE.md](CODE_STRUCTURE.md).

## Running locally

The only prerequisite is Docker.

```bash
git clone <this-repo>
cd skypath
docker-compose up
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Postgres: `localhost:5432` (db/user/pass: `skypath`/`skypath`/`skypath`)

On first boot, Flyway creates the schema and seeds all 25 airports and 302
flights from `flights.json`, then the backend precomputes every valid
direct/1-stop/2-stop itinerary into its own table before it starts accepting
requests — so the first search after startup is already a fast indexed read,
not a live graph traversal.

> If you change backend or frontend code and `docker-compose up --build`
> doesn't seem to pick it up, force it:
> `docker compose up -d --force-recreate backend frontend`. Plain `--build`
> doesn't always recreate an already-running container even when the image
> changed (see CODE_STRUCTURE.md).

### Running a service outside Docker

**Backend** (needs a local **Java 25** toolchain, which may not be
pre-installed — the Docker image pins `eclipse-temurin:25`):

```bash
cd backend
DATASOURCES_DEFAULT_URL=jdbc:postgresql://localhost:5432/skypath \
DATASOURCES_DEFAULT_USERNAME=skypath \
DATASOURCES_DEFAULT_PASSWORD=skypath \
./gradlew run
```

(Needs Postgres reachable at that URL — e.g. `docker compose up postgres`.)

**Frontend:**

```bash
cd frontend
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 npm run dev
```

## API

- `GET /airports` → list of `{ code, name, city, country, timezone }`.
- `GET /itineraries?origin=JFK&destination=LAX&date=2024-03-15` → list of
  itineraries, each with `segments[]` (flight number, airline, times,
  price, aircraft), `layoverMinutes[]`, `totalDurationMinutes`, and
  `totalPrice`, sorted by duration ascending.
  - Unknown airport code → `400 { "error": "..." }`.
  - Same origin and destination → `200 []`.

## Architecture decisions & why

- **Every controller depends on a service interface, not a repository or a
  concrete class.** `AirportController` → `AirportService`,
  `ItineraryController` → `ItinerarySearchService`; each interface has
  exactly one `*Impl` bean that Micronaut DI wires in by type. Consistent
  controller → service (interface + impl) → repository layering
  end-to-end, since Micronaut Data repositories are already interfaces
  with a generated implementation.
- **Itineraries are precomputed, not traversed live.** A startup job walks
  the flight graph once (grouped by origin airport, so it's not a full
  cross product) and writes every valid connection into an `itineraries`
  table, applying the layover/timezone/domestic-international rules at
  write time. The search endpoint is then a single indexed read + sort —
  simple, fast, and easy to reason about — at the cost of a rebuild step
  whenever the underlying flight data changes (fine for a fixed dataset
  seeded once at startup; would need re-triggering on data changes in a
  system where flights are added/edited live).
- **All timezone math goes through each airport's IANA zone, never through
  the raw timestamp.** Both `Flight` and `Itinerary` store times as
  `LocalDateTime` — local wall-clock time with no offset, matching the
  dataset. Every duration/layover calculation converts through
  `Instant`s using `Airport.timezone` first. This is what makes the
  `SYD→LAX` date-line-crossing case (arrival appears earlier than
  departure in local time) resolve to the correct elapsed duration instead
  of a negative or nonsensical one.
- **DTOs, not entities, cross the API boundary.** `Flight`/`Itinerary`
  hold lazy JPA associations (`@ManyToOne` to `Airport`, `Flight`); response
  shapes are separate `@Serdeable` records built via `fromEntity(...)`,
  keeping persistence concerns out of the API contract.
- **Frontend fetches directly from the browser, not via Next.js server-side
  fetching.** `NEXT_PUBLIC_API_BASE_URL` is a browser-facing URL
  (`http://localhost:8080`); using it inside a Next.js Server
  Component/route handler running in the frontend container wouldn't
  resolve correctly (that container can't reach `localhost:8080` on the
  host). Keeping all data fetching in client components sidesteps that.

## Tradeoffs considered

- **Wide single-row itinerary schema vs. a normalized header+segments
  table.** Went with one row per itinerary (`flight_1_id`..`flight_3_id`,
  `layover_1_minutes`/`layover_2_minutes`) instead of a separate
  segments table, trading some rigidity (capped at 2 stops, a few nullable
  columns) for a read path with zero joins — reasonable given the spec caps
  connections at 2 stops.
- **Full rebuild vs. incremental recompute.** The precompute job truncates
  and rebuilds the whole `itineraries` table every boot rather than
  computing incrementally. Simpler and correct for this dataset size
  (~2.8k itineraries from 302 flights, well under a second); would need
  revisiting if the flight dataset were large or mutated frequently at
  runtime.
- **No caching layer.** Given the entire search space fits comfortably in
  one indexed Postgres table, adding an application-level cache would be
  premature — the database index is already the fast path.
- **Domestic vs. international connection rule.** instructions.md specifies
  this by example (`JFK→ORD→LAX = domestic`, `JFK→LHR→CDG = international`)
  rather than mechanically. The rule implemented — the arriving flight's
  origin country and the departing flight's destination country must both
  match the connecting airport's country — satisfies both examples but is
  an interpretation, not a spec quote; flagged in CODE_STRUCTURE.md as
  worth re-confirming.

## What I'd improve with more time

- **Automated tests.** There's currently one backend smoke test (the app
  boots) and no coverage for the precompute algorithm's edge cases
  (layover boundary values, the no-revisit-an-airport rule, date-line
  crossing) or any frontend tests. All of this session's verification was
  manual/browser-driven rather than in a test suite.
- **Read `flights.json` at runtime instead of baking it into a Flyway
  migration.** `FLIGHTS_DATA_PATH` is already wired through
  `docker-compose.yml` but nothing reads it yet — the seed data is
  generated once into `V2__seed_airports_and_flights.sql`. Fine for this
  fixed dataset; a runtime loader would be needed to make the dataset
  swappable without a new migration, and the precompute job would need to
  run after that load rather than only on `StartupEvent`.
- **Pagination on `/itineraries`.** Not needed at this dataset's scale, but
  a route like `BOS→SEA` with many valid connections would benefit from it
  at a larger scale.
- **Trip-level result grouping/filtering in the UI** (e.g. filter by number
  of stops, max price, airline) — the API already returns everything
  needed to build this, just not exposed in the UI yet.

## Test cases

The scenarios from instructions.md's test table were all manually verified
against the running stack (see CODE_STRUCTURE.md for the browser-testing
notes from that session):

| Search | Result |
|---|---|
| `JFK → LAX, 2024-03-15` | Direct + 1-stop + 2-stop itineraries, sorted by duration |
| `SFO → NRT, 2024-03-15` | International route, 90-minute minimum layover enforced |
| `BOS → SEA, 2024-03-15` | No direct flight — connections found |
| `JFK → JFK, 2024-03-15` | Empty result set |
| `XXX → LAX, 2024-03-15` | `400` with a clear error message |
| `SYD → LAX, 2024-03-15` | Date-line crossing resolves to the correct elapsed duration |
