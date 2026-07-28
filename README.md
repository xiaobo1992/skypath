# SkyPath

A flight connection search engine: given an origin, destination, and date, it
returns valid direct, 1-stop, and 2-stop itineraries — with layover and
timezone rules enforced — sorted by total travel time. Built for the
take-home spec in [instructions.md](instructions.md).

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

## Architecture 

```
frontend (Next.js, TypeScript)  --HTTP-->  backend (Micronaut, Java 25)  -->  Postgres
        :3000                                      :8080                       :5432
```

- **Frontend** — Next.js 16 / React 19, App Router, TypeScript, CSS Modules.
- **Backend** — Micronaut 5 (Netty), Java 25, Hibernate JPA, Flyway migrations.
- **Database** — Postgres 16.
- **Orchestration** — Docker Compose runs all three services together.

**Why Micronaut for the backend:**
1. **Followed the existing backend framework** rather than swapping it out.
   The repo's scaffold (`build.gradle`, `Dockerfile`, `application.properties`)
   was already set up on Micronaut 5 before this feature work started, so
   sticking with it avoided a framework migration that wouldn't have added
   value to the actual take-home requirements.
2. **Integrates with the database very well.** `micronaut-data-hibernate-jpa`
   gives repository interfaces with derived queries (e.g.
   `ItineraryRepository.findByOriginCodeAndDestinationCodeAndDepartureDate
   OrderByTotalDurationMinutesAsc`) without hand-written SQL or boilerplate
   DAO code, and `micronaut-jdbc-hikari` wires in HikariCP connection
   pooling out of the box — both just declarative dependencies, no manual
   setup.
3. **Integrates with Flyway for schema upgrades.** The `micronaut-flyway`
   module runs versioned SQL migrations (`V1__...`, `V2__...`, `V3__...`)
   automatically on startup before the app accepts traffic, and pairs with
   `hibernate.hbm2ddl.auto=validate` so Hibernate only checks the schema
   matches rather than silently auto-generating it — schema changes have
   to go through an explicit migration file, which keeps schema evolution
   auditable and reproducible across environments.

**Why Next.js for the frontend:** it follows React, the major/most widely
used frontend framework, and I don't have deep frontend experience — but
Next.js is straightforward to pick up (file-based routing, `'use client'`
for interactive components, CSS Modules) without needing to hand-configure
a bundler or router first, which mattered more here than picking the
"most powerful" option.

**Why Postgres for the database:** it's relational and supports composite
indexes, which this search is built around — the `itineraries` table's
`idx_itineraries_search` index covers `(origin_code, destination_code,
departure_date, total_duration_minutes)` in one index, so a search is a
single index scan that's already sorted by duration, and `flights` has a
similar `(origin_code, destination_code, departure_time)` index for the
precompute job's own lookups. A NoSQL store like DynamoDB is built around
a single partition key + sort key (plus a limited number of secondary
indexes); it doesn't support an arbitrary multi-column composite index like
this one, so a query filtering/sorting on four columns at once would need
either a much wider partition key baked in ahead of time or a scan — worse
fit for a search shaped like this one.

## TradeOffs
- **Precomputed itineraries vs. live graph traversal per search.**
  - Chose precompute: walk the flight graph once at startup (grouped by
    origin airport, not a full cross-product), apply the
    layover/timezone/domestic-international rules once, and write every
    valid direct/1-stop/2-stop itinerary into an indexed table. The search
    endpoint then does a single indexed read + sort, with no rule
    evaluation, graph walk, or timezone/duration math at request time —
    every `Instant` conversion and layover calculation already happened
    once at precompute time instead of being redone on every API call.
  - Schema shape: a single wide, denormalized itinerary row
    (`flight_1_id`..`flight_3_id`, `layover_1_minutes`/`layover_2_minutes`)
    that carries the relevant flight legs and layovers directly, so the
    search endpoint reads one row straight off the index with no extra
    join — that's what keeps read latency low. The cost is some rigidity
    (capped at 2 stops, a few nullable columns), which is acceptable since
    the spec caps connections at 2 stops anyway.
  - Cost: staleness risk — the table is only as fresh as the last
    recompute. Currently sidestepped by wiping and rebuilding the whole
    table on every boot, which is fine because the dataset is fixed and
    seeded once via Flyway (~2.8k itineraries from 302 flights, well under
    a second).
  - Doesn't scale indefinitely: 2-stop itineraries grow combinatorially
    with the flight count, so a much larger or rolling schedule would blow
    up both the precompute time and the table size.
  - Where I'd draw the line differently: if flights were being added or
    edited live rather than loaded once at boot, I'd either trigger an
    incremental recompute on write (reprocessing only the itineraries that
    touch the changed flight, via the `flight_id` indexes already in
    place) or fall back to computing on demand with an in-memory route
    index and caching hot origin/destination/date combinations.
- **All timezone math goes through each airport's IANA zone, never through
  the raw timestamp.** Both `Flight` and `Itinerary` store times as
  `LocalDateTime` — local wall-clock time with no offset, matching the
  dataset. Every duration/layover calculation converts through
  `Instant`s using `Airport.timezone` first. This is what makes the
  `SYD→LAX` date-line-crossing case (arrival appears earlier than
  departure in local time) resolve to the correct elapsed duration instead
  of a negative or nonsensical one — the tradeoff is that every touch
  point has to remember to convert through the zone rather than compare
  timestamps directly, which is easy to get wrong if a future change adds
  a new time calculation without following the same pattern.
- **Invalid data handling: reject at the database, not filter at read
  time.** The `itineraries` table has a `chk_stop_count_matches_flights`
  CHECK constraint tying `stop_count` to which `flight_2_id`/`flight_3_id`
  columns are populated, and `origin_code`/`destination_code` are foreign
  keys into `airports`. I'd rather an invalid row fail to insert at
  precompute time than exist in the table and need to be filtered out (or
  worse, silently shown) later — there's no code path where a user can end
  up choosing between a valid and an invalid itinerary, because an invalid
  one can never be persisted in the first place. The cost is that the
  constraint has to be kept in sync with the precompute logic by hand (a
  code change that produces a row shaped differently would fail loudly at
  insert time rather than being caught by a type system), but a loud
  failure at the source beats quietly wrong data reaching the API.

## What I'd improve with more time

- **Automated tests.** There's currently one backend smoke test (the app
  boots) and no coverage for the precompute algorithm's edge cases
  (layover boundary values, the no-revisit-an-airport rule, date-line
  crossing) or any frontend tests. All of this session's verification was
  manual/browser-driven rather than in a test suite.
- **Pagination on `/itineraries`.** Not needed at this dataset's scale, but
  a route like `BOS→SEA` with many valid connections would benefit from it
  at a larger scale.
- **Trip-level result grouping/filtering in the UI** (e.g. filter by number
  of stops, max price, airline) — the API already returns everything
  needed to build this, just not exposed in the UI yet.
- **An application-level caching layer**, if the dataset or query volume
  grew enough that the indexed Postgres read stopped being fast enough on
  its own — not needed today, but worth revisiting under load.
- **Seat availability** (seats left, or sold out) isn't modeled anywhere in
  the dataset or the API today — every itinerary is treated as bookable.
  Adding it would touch the schema, the precompute job, and the UI; left
  as a future discussion rather than a today decision.
- **Disable past dates in the date picker.** The date input has no `min`
  today, so a user can pick a date before "now" even though a flight
  can't be booked in the past. Not wired up yet because the seed dataset
  is pinned to a single fixed date (`2024-03-15`) that's already in the
  past relative to the real calendar — restricting the picker to
  "today or later" would make the only working demo date unselectable.
- **Search by city instead of just airport code.** A city like New York
  has multiple airports (JFK, LGA, EWR); today a search only matches the
  exact airport code entered, so a user has to already know which one
  they want. Searching by city would need to expand to every airport in
  that city and merge/dedupe the resulting itineraries — the `airports`
  table already has a `city` column, so the main work would be in the
  search service and the query, not the schema.

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
