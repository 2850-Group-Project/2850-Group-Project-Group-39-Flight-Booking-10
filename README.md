# Flight Booking System

**Group:** Group 39 / Flight Booking 10

## Team Members

- Weiming Chen
- Patrick N. Butuza
- Ephraim G. Chen
- Dima Ivanovs

A Kotlin/Ktor web application for searching flights, booking seats, taking payments, managing loyalty points, and supporting staff workflows for a flight booking service.

## Overview

This project is a group flight booking system built with Ktor on the server side and Pebble templates for the UI. It supports both passenger-facing journeys and staff-facing administration pages.

Passenger users can register, log in, search for flights, select fares, enter passenger details, choose seats, pay for bookings, earn or redeem loyalty points, view bookings, request booking changes, and submit complaints.

Staff users can register and log in through separate staff routes, view a dashboard, manage flights, manage bookings, handle notifications, and respond to customer inquiries.

## Features

### Passenger Features

- User registration, login, logout, and session handling.
- Flight search by origin, destination, trip type, dates, and passenger counts.
- Airport autocomplete backed by the airport table.
- Outbound and return flight selection with fare options.
- Passenger detail collection for adults, children, and infants.
- Seat selection for outbound and return journeys.
- Card payment flow with booking confirmation.
- Loyalty points earning, redemption, and membership status tracking.
- Profile page with points activity.
- Booking history, cancellation, deletion, and change request workflows.
- Complaint submission and customer-visible response tracking.

### Staff Features

- Staff registration, login, logout, and staff session handling.
- Staff dashboard.
- Flight creation, update, and deletion.
- Booking creation and update.
- Staff notifications with status and deletion actions.
- Inquiry management for customer complaints and complaint responses.

## Tech Stack

- Kotlin 1.9.22
- JVM toolchain 21
- Ktor 2.3.7
- Ktor Netty server
- Pebble templates
- SQLite
- JetBrains Exposed SQL library
- Kotlinx serialization
- Jackson Kotlin module
- Gson
- jBCrypt for password hashing
- JUnit 5 and Ktor test host
- ktlint and detekt for code quality checks

## Getting Started

### Prerequisites

- JDK 21
- A shell that can run the Gradle wrapper

The repository includes the Gradle wrapper, so a separate Gradle installation is not required.

### Run the Application

From the project root:

```bash
./gradlew run
```

By default the application runs on:

```text
http://localhost:8080
```

The configured host is `0.0.0.0`, and the default port is `8080`. The port can be overridden with the `PORT` environment variable through `src/main/resources/application.conf`.

### Health Check

The application exposes a simple health endpoint:

```text
GET /__health
```

Expected response:

```text
ok
```

## Testing and Quality Checks

Run the test suite:

```bash
./gradlew test
```

Run ktlint:

```bash
./gradlew ktlintCheck
```

Run detekt:

```bash
./gradlew detekt
```

The GitHub Actions workflow in `.github/workflows/ci.yml` runs ktlint and detekt on pushes and pull requests to `main`.

## Application Structure

```text
src/main/kotlin/com/flightbooking
- access      Database access classes built on Exposed
- api         AviationStack API client and API response models
- constants   Shared constants used across tables and services
- database    Database initialisation
- mappers     ResultRow-to-model mapping helpers
- models      Session, domain, and table model data classes
- routes      Ktor route registration and request handlers
- service     Authentication, import, and loyalty points services
- tables      Exposed table definitions
- util        Shared utilities
```

```text
src/main/resources
- application.conf   Ktor application configuration
- static             CSS and JavaScript assets
- templates          Pebble templates
- airports.csv       Airport reference data
- countries.csv      Country reference data
```

```text
src/test/kotlin/com/flightbooking
```

Contains integration and route tests. Tests use temporary SQLite database files so they do not write to the main local database.

## Main Routes

### Public and Passenger Routes

- `GET /` redirects to `/login`.
- `GET /register` and `POST /register` handle passenger registration.
- `GET /login` and `POST /login` handle passenger login.
- `GET /logout` logs out a passenger.
- `GET /home` renders the passenger home/search page.
- `GET /airports/search` returns airport autocomplete results.
- `GET /flights/search` shows matching outbound and return flights.
- `POST /flights/select` stores the selected flight and fare in the booking session.
- `GET /flights/passengers` collects passenger details.
- `POST /flights/passengers/submit` saves passenger details.
- `GET /flights/seats` and `POST /flights/seats` handle outbound seat selection.
- `GET /flights/seats/return` and `POST /flights/seats/return` handle return seat selection.
- `GET /payment` and `POST /payment` handle payment and loyalty point redemption.
- `GET /confirmation` shows the final booking confirmation.
- `GET /profile` shows profile and loyalty point information.
- `GET /profile/bookings` shows booking history.
- `POST /profile/bookings/cancel` cancels a booking.
- `POST /profile/bookings/delete` deletes a booking from the user view.
- `GET /profile/bookings/change` and `POST /profile/bookings/change` handle booking change requests.
- `GET /complaints` and `POST /complaints/submit` handle complaint submission.
- `GET /profile/complaints` shows submitted complaints.
- `POST /profile/complaints/view-responses` marks complaint responses as viewed.

### Staff Routes

- `GET /staff/register` and `POST /staff/register` handle staff registration.
- `GET /staff/login` and `POST /staff/login` handle staff login.
- `GET /staff/logout` logs out a staff member.
- `GET /staff/dashboard` renders the staff dashboard.
- `GET /staff/flights` shows staff flight management.
- `POST /staff/flights/create` creates a flight.
- `POST /staff/flights/update` updates a flight.
- `POST /staff/flights/delete` deletes a flight.
- `GET /staff/bookings` shows staff booking management.
- `POST /staff/bookings/create` creates a full booking.
- `POST /staff/bookings/update` updates booking, segment, and seat assignment details.
- `GET /staff/notifications` shows staff notifications.
- `POST /staff/notifications/status` updates notification status.
- `POST /staff/notifications/delete` deletes a notification.
- `GET /staff/inquiries` shows customer inquiries.
- `POST /staff/inquiries/respond` creates a complaint response.
- `POST /staff/inquiries/status` updates complaint status.
- `POST /staff/inquiries/delete` deletes a complaint.
- `POST /staff/inquiries/delete-response` deletes a complaint response.

## Database

The default application database is:

```text
data/flight_booking_DB.db
```

`DBFactory` connects to SQLite using:

```text
jdbc:sqlite:data/flight_booking_DB.db
```

At startup it uses Exposed `SchemaUtils.create` to ensure the required tables exist. This creates missing tables but is not a full migration system.

The main tables are:

- `airport`
- `flight`
- `fare_class`
- `flight_fare`
- `user`
- `booking`
- `payment`
- `passenger`
- `booking_segment`
- `seat`
- `seat_assignment`
- `staff`
- `complaint`
- `complaint_response`
- `notification`
- `change_request`
- `user_points`
- `points_transaction`

## Data and Imports

The API client in `src/main/kotlin/com/flightbooking/api` talks to AviationStack for airport and flight data.

Airport importing:

- Fetches paginated airport data from AviationStack.
- Keeps only airports with an IATA code.
- Inserts new airports or updates existing airports by IATA code.
- Uses fallback labels such as `Unknown Airport`, `Unknown City`, and `Unknown Country` when the API response is incomplete.

Flight importing:

- Fetches paginated flight data from AviationStack.
- Validates each flight against known origin and destination airports.
- Skips flights with missing IATA codes or airports not present in the database.
- Rebuilds the flight table before inserting imported flights.

The `airports.csv` and `countries.csv` resources are included as local reference data. Earlier development notes used these files to enrich missing city and country information where API data was incomplete.

## Loyalty Points

The loyalty points system is implemented in `PointsService` and `PointsTableAccess`.

- Points are awarded after paid bookings.
- Earning is based on booking amount, fare-class earn rate, and membership tier.
- Points can be redeemed during checkout for a discount.
- Point transactions are recorded for audit/history.
- Membership status is derived from total earned points.

## Authentication and Sessions

Passenger authentication is handled by `AuthService`.

Staff authentication is handled by `StaffAuthService`.

Passwords are hashed with jBCrypt before being stored. Ktor cookie sessions are used for:

- `UserSession`
- `StaffSession`
- `BookingSession`

## Documentation Notes

- Project-level usage and architecture documentation belongs in this README.
- API integration details belong in `src/main/kotlin/com/flightbooking/api/README.md`.
- Database access layer guidance belongs in `src/main/kotlin/com/flightbooking/access/README.md`.
- User stories and pull request traceability are maintained in the GitHub Wiki. Pull requests are linked to user stories using GitHub labels in the format `story:US<number>-<short-name>`.
- KDoc should be used for non-obvious functions, public helpers, service rules, and route handlers where behaviour is not immediately clear from the code.

## Known Limitations

- The AviationStack access key is currently owned by the API client code. A production version should load this from configuration or environment variables.
- Database creation uses `SchemaUtils.create`; it does not handle schema migrations or destructive schema changes.
- Some imported AviationStack records may be missing city, country, schedule, aircraft, or flight-number data.
- The payment flow stores a simplified card/provider reference for coursework purposes and is not a real payment gateway.
- The profile notifications route currently redirects to the shared 404 page until user notification display is implemented.
