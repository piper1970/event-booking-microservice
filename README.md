# Event-Booking Service

_**Event-Booking Service**_ is a cloud-based booking platform that utilizes Spring Boot, Spring Cloud, and Kafka messaging components in a reactive, distributed, microservices architecture.

---

## Basic Application Flow

- All API access goes through the api, at address `http://localhost:8080` _(`https://localhost:9000` for SSL version)_
- Events get created and accessed via the event-service api
    - The basic access point for accessing events is at `http://localhost:8080/api/events` _(`https://localhost:9000/api/events` for SSL version)_
    - Events can be created, retrieved, updated, and cancelled, using `POST`, `GET`, `PUT`, and `PATCH` methods
    - In order to modify an event, the user must be the owner of the event, and have the role `PERFORMER`.
    - In order to view an event, the user must be registered as a `MEMBER`. (`PERFORMER` infers `MEMBER` association)
    - Anyone who signs up via the OAuth2 server automatically is given `MEMBER` access
- If someone wishes to book an event, this is handled by the booking-service api
    - The basic access point for the booking service is at `http://localhost:8080/api/bookings` _(`https://localhost:9000/api/bookings` for SSL version)_
    - Bookings can be created, viewed, updated, and cancelled, using `POST`, `GET`, `PUT`, and `PATCH` methods.
    - Only the creator of the booking can access it.
    - Creating a booking triggers a notification email with a unique url to confirm the booking.
    - Bookings must be confirmed within the hour, or the slot is lost.
    - If an event is booked which does not have an available slot, the booking will be cancelled.
- Creating users to play with system
    - The OAuth2 Server (Keycloak) allows for users to signup while accessing the login page.
    - The signup page asks for name and email information.  There is no verification process for the email.
    - The new user will be given `MEMBER` access rights, which allow the user to view events and make bookings.
    - When the new user attempts to book an event, an email will be sent out to the provided email address for confirmation.
    - In order to bump a user to `PERFORMER` status, the Keycloak Admin will need to manually make the change.
    - The keycloak console can be reached at `http://localhost:8180` _(`https://localhost:8443 for SSL version)_ with the credential _KC_ADMIN_ and _KC_ADMIN_PASSWORD_ provided in the _.env_ file

---

## System Architecture

This system a demonstrates a comprehensive event-driven microservices architecture with:
- **API Gateway**: Proxy for microservice access
- **Service Discovery**: Automatic registration and discovery of services
- **Config Server**: Centralized configuration management (Currently running on local FS for visibility)
- **Event-Driven Architecture**: Asynchronous, message-based communication via Kafka, Schema Registry, and Avro
- **Oauth2/OpenId Security**: Authentication and authorization via KeyCloak
- **Database per Service**: Each service backed by its own database
- **Resilience Patterns**: Circuit breaker, retries, and timeouts
- **Reactive Components**: Webflux for reactive api endpoints, R2DBC for reactive database access, and Reactive Kafka by Project Reactor

### Microservices

1. **event-service**: Manages event information, creation and management
2. **booking-service**: Handles booking requests associated with event-service events
3. **notification-service**: Sends booking confirmations and other notifications via email
4. **api-gateway**: API Frontend that routes client requests to appropriate services
5. **discovery-server**: Provides service discovery for microservices
6. **event-service-config**: Provides centralized configuration

### External Services Used

1. **postgres**: backend rdbms database system
2. **redis**: low-latency caching used for rate-limiting in api-gateway
3. **zipkin**: distributed log tracing across microservices
4. **keycloak**: oauth2/openid authentication and authorization server
5. **kafka**: message broker for asynchronous microservice communication
6. **schema-registry**: centralized schema management server for kafka 
7. **zookeeper**: kafka dependency for distributed configuration management
8. **prometheus**: metrics monitoring
9. **grafana**: visualization of metrics
10. **logstash**: part of ELK stack, used to forward log messages to storage
11. **elasticsearch**: part of ELK stack, used as storage and search engine for logs
12. **kibana**: part of ELK stack, providing web ui used for accessing and visualization of logs

### Technology Stack

- **Spring Boot**: Service Implementation
- **Spring Cloud Gateway**: API Gateway
- **Spring Cloud Config**: Configuration Management
- **Spring Cloud Netflix Eureka**: Service Discovery
- **Spring Security w/Oath2**: Security Implementation
- **Kafka**: Message brokering for event-driven architecture
- **Postgres**: Database-per-microservice design
- **Redis**: In-Memory Database for API Gateway Rate-limiting
- **Webflux**: Reactive web architecture utilizing Project Reactor
- **R2DBMS**: Reactive database access
- **TestContainers**: Usage of docker containers during integration tests
- **Resilience4J**: Circuit breaking and fault tolerance
- **Prometheus/Grafana**: Monitoring and Visualization
- **Logstash/ElasticSearch/Kibana(ELK)**: Centralized log aggregation and searching
- **OpenAPI/Swagger**: API Documentation and Test Access

---

## Setting Up Project

See [data/documentation/project-setup.md](data/documentation/project-setup.md) for project setup details.

### Running Docker Compose

This application can run in four different ways: 
1) All microservices are run locally (with SSL disabled)
2) All microservices are run within docker environment (with SSL disabled)
3) All microservices are run locally (with SSL enabled)
4) All microservices are run within docker environment (with SSL enabled)

- See [data/documentation/minimal_compose_documentation.md](data/documentation/minimal_compose_documentation.md) for running microservices locally with SSL disabled.
- See [data/documentation/full_compose_documentation.md](data/documentation/full_compose_documentation.md) for running everything within docker environment with SSL disabled.
- See [data/documentation/minimal_compose_ssl_documentation.md](data/documentation/minimal_compose_ssl_documentation.md) for running microservices locally with SSL enabled.
- See [data/documentation/full_compose_ssl_documentation.md](data/documentation/full_compose_ssl_documentation.md) for running everything within docker environment with SSL enabled.

### Setting up environment for locally-run microservices

Docker-Compose automatically extracts environment variables from the _.env_ file.  
All locally-run microservices also need to be using the environment variables from the _.env_ file to stay in sync.

The following commands will load the environment variables from _.env_ into a shell environment,
depending on which system you are running under. Run them from the project directory:

#### For Mac/Linux users (using Bash):

- `export $(sed '/^$/d' .env | sed '/^#/d' | xargs)`

#### For Windows Users (using Powershell):

- `Get-Content .env | Where-Object {$_ -match '\S' -and $_ -notmatch '^#'} | ForEach-Object {$name,$value = $_ -split '=',2; Set-Item "env:$name" $value}`

### Prepping the environment for Keycloak JWT Access

_**Important**_: due to naming issues with OAuth2 JWT tokens distributed by Keycloak,
an alias - _**127.0.0.1 keycloak**_ - needs to be added to end of the _**hosts**_ file.<br>
This ensures the address located within the JWT token matches the keycloak server address.

For __Windows__, the _host_ file can be found at '__C:\Windows\System32\drivers\etc\hosts__'.

For __Linux__, the _host_ file can be found at '__/etc/hosts__'.

For __macOS__, the _host_ file can be found at '__/private/etc/hosts__'.

___

## Accessing the system once running

Once everything is up and running there are a couple of ways to access it.
- Swagger (preferred method)
- Postman

### Accessing the API via Swagger

Swagger provides documentation for the system and easy access to try out the endpoints.

To use the Swagger UI while the system is running:

- Using http: 
  - Go to [http://localhost:8080/v3/swagger-ui/index.html](http://localhost:8080/v3/swagger-ui/index.html).
- Using https:
  - Go to [https://localhost:9000/v3/swagger-ui/index.html](https://localhost:9000/v3/swagger-ui/index.html);

### Accessing the API via Postman

If you want to run this system using __Postman__, there are four JSON files in the _data/postman_ directory: two for local (http/https) use and the other two for with fully dockerized use (http/https).

All Postman configurations require the following properties to be set as secrets in the Postman Environments area: `oauth_client_id` and `oauth_client_secret`.

Refer to the `OAUTH2_CLIENT_ID` and `OAUTH2_CLIENT_SECRET` properties in the `.env` file.

---

## Architectural Diagrams

Additional diagrams can be found in the [data/diagrams](./data/diagrams) directory.


