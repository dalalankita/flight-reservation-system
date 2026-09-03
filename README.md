# Flight Reservation

A small Spring Boot service for searching flights and holding seats on them. It's Java 21
on Spring Boot 3.3.5, backed by PostgreSQL with Flyway-managed schema migrations.

## Running

The service comes up on http://localhost:8080. It needs a PostgreSQL database, which
runs in Docker via the included `docker-compose.yml`.

### Prerequisites
- JDK 21
- Docker Desktop, installed **and running** (the engine must be up, not just the CLI)

### 1. Start the database

`````bash
docker compose up -d          # starts PostgreSQL in the background
docker compose ps             # confirm the db service is "Up"
`````

### 2. Run the app

`````bash
./mvnw spring-boot:run
`````

On startup, Flyway applies the schema migrations, then the app connects and serves on
port 8080. Data persists in Postgres across restarts.

### Smoke test

With the both database and service running, in another terminal:

```bash
# Create a flight — POST /admin/flights
curl -X POST localhost:8080/admin/flights \
  -H 'Content-Type: application/json' \
  -d '{
    "flightNumber": "EI123",
    "origin": "DUB",
    "destination": "LHR",
    "departureCity": "Dublin",
    "scheduledDeparture": "2030-01-01T14:30:00",
    "departureZone": "Europe/Dublin",
    "totalSeats": 2
  }'


# List available flights — GET /flights
curl localhost:8080/flights
curl "localhost:8080/flights?origin=DUB&destination=LHR&date=2030-01-01"

# Reserve (hold) a seat — POST /flights/{id}/bookings
curl -X POST localhost:8080/flights/1/bookings \
  -H 'Content-Type: application/json' \
  -d '{
    "passengerName": "Ada Lovelace",
    "passengerEmail": "ada@example.com"
  }'
  
# Confirm a reservation — POST /bookings/{id}/confirm
curl -X POST localhost:8080/bookings/1/confirm

# Cancel a reservation — DELETE /bookings/{id}
curl -X DELETE localhost:8080/bookings/1

# Remove a flight — DELETE /admin/flights/{id}
curl -X DELETE localhost:8080/admin/flights/1
```

### Tests

```bash
./mvnw test
```
Tests run against in-memory H2 (in PostgreSQL-compatibility mode), so no database or
Docker is needed to run them. Three tiers:

- **Unit tests** (`service/`, `model/`) — service rules (booking window, capacity, hold
  lifecycle, zone handling) with mocked dependencies and an injected fixed `Clock`.
- **Repository integration tests** (`integrationtests/repository/`, `@DataJpaTest`) —
  the custom queries (expiry-aware seat count, the grouped availability search) against a
  real database.
- **Full integration tests** (`integrationtests/`, `@SpringBootTest`) — the no-oversell
  invariant under real concurrency (20 bookings at a 3-seat flight, exactly 3 succeed),
  and the flight-deletion rules.

## API

| Method | Path | Purpose |
|--------|------|---------|
| GET    | `/flights` | List available flights; optional `?origin=&destination=&date=YYYY-MM-DD` |
| POST   | `/flights/{id}/bookings` | Hold a seat (passenger name + email) |
| POST   | `/bookings/{id}/confirm` | Confirm a held booking |
| DELETE | `/bookings/{id}` | Cancel a booking and release the seat |
| POST   | `/admin/flights` | Create a flight |
| DELETE | `/admin/flights/{id}` | Remove a flight (refused if it has active bookings) |

`GET /flights` returns only flights that still have a free seat — "available" is read as
bookable. Each response includes a live `seatsAvailable` count.

## Configuration

Set in `application.yml`:

| Key | Default | Meaning |
|-----|---------|---------|
| `app.booking-window-minutes` | `45` | Bookings refused once within this many minutes of departure |
| `app.hold-minutes` | `10` | How long an unconfirmed hold survives before it can be released |
| `app.sweep-ms` | `60000` | How often the background sweep releases expired holds |

## Database & migrations

- **PostgreSQL** is the datastore, run locally via `docker-compose.yml`.
- **Flyway** owns the schema. Versioned SQL migrations live in
  `src/main/resources/db/migration/` (`V1__init.sql`). Flyway runs them on startup and
  records what it has applied, so the schema is reproducible and versioned rather than
  auto-generated.
- **Hibernate is set to `ddl-auto: validate`** — it never creates or alters tables, it
  only checks that the entities match the Flyway-built schema, failing fast on drift.
- **Tests use H2** in PostgreSQL-compatibility mode with `create-drop`, so they stay fast
  and need no container.

## Project layout
`````
    controller/                 HTTP layer - request/response, no business logic
    dto/                        request/response shapes (kept separate from entities)
    error/                      global exception handling + shared error-response shape
    model/                      JPA entities + enum
    repository/                 Spring Data JPA interfaces + query projection
    service/                    business rules, transactions, booking/hold logic
    resources/db/migration/     Flyway schema migrations (V1__init.sql)
`````

## Errors

All errors return the same shape:

```json
{
  "timestamp": "2026-06-18T09:12:04Z",
  "status": 409,
  "error": "Conflict",
  "message": "Flight is full",
  "path": "/flights/1/bookings"
}
```

Validation errors add a `fieldErrors` map showing what was wrong:

```json
{
  "timestamp": "2026-06-18T09:12:04Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/admin/flights",
  "fieldErrors": { "totalSeats": "must be greater than or equal to 1" }
}
```

A single `@RestControllerAdvice` handles every error — thrown status exceptions,
validation failures, and malformed JSON — so the shape is always consistent.

## Design decisions

**Booking window is computed against the airport's local clock.**
A flight stores its scheduled departure as a `LocalDateTime` plus a `departureZone`
(e.g. `Europe/Dublin`), not a single fixed instant. The 45-minute cutoff is based on
local time through the zone, so a flight departing Dublin at 14:30 stops
accepting bookings at 13:45 Dublin time, and it is handled by `java.time` rather than by
hand.

**Seat availability is calculated, never stored.**
A flight's free seats are `totalSeats − count(active bookings)`, where "active" means
CONFIRMED or a HELD hold that hasn't yet expired (`holdExpiresAt > now`). Cancelling or
expiring a hold needs no special handling — the seat is free again simply because that
booking no longer counts. Availability and the flight search are computed in a single
grouped SQL query, not per-flight, so listing flights is one round trip regardless of how
many flights there are.

**No oversell, via a pessimistic lock on the flight row.**
`book()` loads the flight with `SELECT … FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) inside a
`@Transactional` method, then checks capacity and inserts the hold. So two bookings for the same flight can't both read "1 seat left" and both take it,
the second one waits for the first to finish and sees the updated count. The `concurrentBookingsNeverOversell` integration test fires 20 simultaneous bookings at a
3-seat flight and asserts exactly 3 succeed.

**Holds expire, and a background sweep tidies them up.**
A new booking is `HELD` with a `holdExpiresAt` of now + `hold-minutes`. Seat availability
ignores holds past their expiry at read time, so an expired hold frees its seat
immediately — no waiting on the sweep. A `@Scheduled` task then runs every `sweep-ms` and
flips lapsed `HELD` rows to `EXPIRED` so the stored status matches reality; it's
housekeeping, not correctness.

**Time goes through an injected `Clock`.**
All time-sensitive logic reads from a `Clock` bean, so the window and hold-expiry behaviour
is tested with a fixed clock.

**Entities and DTOs are separate.**
JPA entities are mutable classes (Hibernate needs a no-arg constructor and field mutation
for dirty-checking). API payloads are immutable `record`s. Keeping them apart also avoids a common lazy-loading error 
when converting entities to JSON, and lets the API's shape evolve independently of the database tables.

## Trade-offs and limitations

These are deliberate scope choices, not oversights:

**Pessimistic locking serializes bookings per flight.** `book()` holds a row lock on the
flight for the length of the transaction. It's easy to reason about and obviously correct,
but a high-throughput system might prefer optimistic locking with a retry, or a single
atomic conditional UPDATE, to avoid holding the lock.

**Confirm and the sweep aren't guarded against a concurrent status change.** Both move a
HELD booking to a new status, and neither takes a row lock on the booking, so a hold
expiring at the same instant it's confirmed could in principle lose one update. The window
is narrow and `confirm()` independently re-checks `holdExpiresAt` (so a clearly-expired
hold can never be confirmed), which makes a bad outcome low-risk but not impossible. A
`@Version` column or a row lock on the booking would close it fully.

**Response mapping assumes the flight is already loaded.** `BookingResponse.of` reads the
flight off the booking (`getFlight().getId()`) after the transaction has closed. It's safe
on the current paths because `book()` loads the flight for its capacity check, so the
association is populated — but a future read endpoint that fetched a booking without its
flight would hit a lazy-initialization error. A fetch-join or mapping inside the
transaction would make it robust.

**`/admin` endpoints are unauthenticated.** Access control is out of scope here; in a real
deployment these would sit behind authentication and an admin role.


