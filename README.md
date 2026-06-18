# Flight Reservation

A small Spring Boot service for searching flights and holding seats on them. It's Java 21
on Spring Boot 3.3.5, backed by an in-memory H2 database - runs with no external setup

## Running

There are two ways to run the service. Either way it comes up on
http://localhost:8080. No database or other infrastructure is needed — storage is an
in-memory H2 instance created on startup.

Option A — Maven (no Docker)

Requires JDK 21 and Maven on your machine.

```bash
mvn spring-boot:run
```

### Option B — Docker

Requires Docker. The Dockerfile is a multi-stage build: it compiles the jar in a Maven
image, then runs it on a slim JRE image, so you don't need a local JDK or Maven.

If Docker isn't installed, get Docker Desktop (`brew install --cask docker-desktop` on a
Mac, or download it from docker.com) and **make sure it's running** before building —
the engine has to be up, not just the CLI installed, or the build fails with "Cannot
connect to the Docker daemon."

```bash
docker build -t flight-reservation-system .       # builds the jar and the image
docker run -p 8080:8080 flight-reservation-system # runs it, publishing port 8080
```

`-p 8080:8080` maps the container's port to your machine. Stop it with `Ctrl+C`, or add
`-d` to `docker run` to run it in the background. Because storage is in-memory, every
`docker run` starts with an empty database.

### Smoke test

With the service running (either option), in another terminal:

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
mvn test
```

Unit tests cover the service rules (booking window, capacity, hold lifecycle, zone
handling) with an injected fixed `Clock`. An integration test (`@SpringBootTest`)
exercises the no-oversell invariant under real concurrency.

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

## Project layout
```
    controller/   HTTP layer - request/response, no business logic
    dto/          request/response shapes (kept separate from entities)
    error/        global exception handling + the shared error-response shape
    model/        JPA entities + enum
    repository/   Spring Data JPA interfaces
    service/      business rules, transactions, the booking/hold logic
```

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
A flight's free seats are `totalSeats − count(bookings in HELD or CONFIRMED)`. The nice payoff is
that cancelling or expiring a hold needs no special handling: the seat is free again simply
because that booking no longer counts. A booking's whole life is one status moving along:
`HELD → CONFIRMED | CANCELLED | EXPIRED`.

**No oversell, via a pessimistic lock on the flight row.**
`book()` loads the flight with `SELECT … FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) inside a
`@Transactional` method, then checks capacity and inserts the hold. So two bookings for the same flight can't both read "1 seat left" and both take it,
the second one waits for the first to finish and sees the updated count. The `concurrentBookingsNeverOversell` integration test fires 20 simultaneous bookings at a
3-seat flight and asserts exactly 3 succeed.

**Holds expire on a background sweep.**
A new booking is `HELD` with a `holdExpiresAt` of now + `hold-minutes`. A `@Scheduled` task
runs every `sweep-ms` and flips any `HELD` booking past its expiry to `EXPIRED`, which
returns the seat to the pool. Simple and predictable; see Trade-offs for the cost.

**Time goes through an injected `Clock`.**
All time-sensitive logic reads from a `Clock` bean, so the window and hold-expiry behaviour
is tested with a fixed clock.

**Entities and DTOs are separate.**
JPA entities are mutable classes (Hibernate needs a no-arg constructor and field mutation
for dirty-checking). API payloads are immutable `record`s. Keeping them apart also avoids a common lazy-loading error 
when converting entities to JSON, and lets the API's shape evolve independently of the database tables.

## Trade-offs and limitations

These are deliberate scope choices for a weekend exercise, not oversights:

**In-memory H2.** Data resets on restart. Swapping in Postgres is a config change. The
pessimistic-lock/no-oversell logic already leans on real database row locking,
it carries over cleanly.

**Hold release lags by up to one sweep interval.** Between a hold expiring and the next
sweep, its seat still reads as taken. A read-time "ignore expired holds" check would be
exact; the sweep was chosen for simplicity. Note this only affects the displayed availability,
confirm() independently re-checks holdExpiresAt and rejects an expired hold even 
before the sweep runs, so a stale hold can never be confirmed in that gap.

**Pessimistic locking serializes bookings per flight.** Easy to reason about and obviously correct. A
high-throughput system might prefer optimistic locking with a retry, or a single atomic
conditional UPDATE, to avoid holding a row lock for the length of the transaction.

**`/admin` endpoints are unauthenticated.** Out of scope here.

**Search filters in memory** It loads the flights and filters them in a stream, which is
fine at this size but belongs in the query once there's real data behind it.


