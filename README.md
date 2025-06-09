# Event-Booking Service (POC, WIP)

_A microservice-based event management and booking system, utilizing Spring Boot and Spring Cloud components._

This project is a POC, to demonstrate understanding of current Spring Boot and Spring Cloud development. 

---

## Architecture

This system a demonstrates a comprehensive event-driven microservices architecture with:
- **API Gateway**: Single entry point for all requests
- **Service Discovery**: Automatic registration and discovery of services
- **Config Server**: Centralized configuration management (Currently running on local FS for visibility)
- **Event-Driven Architecture**: Asynchronous, message-based communication via Kafka, Schema Registry, and Avro
- **Oauth2/OpenId Security**: Authentication and authorization via KeyCloak
- **Database per Service**: Each service backed by its own database
- **Resilience Patterns**: Circuit breaker, retries, and timeouts

## Services Overview
1. **event-service**: Manages event information and creation
2. **booking-service**: Handles booking requests associated with event-service events
4. **notification-service**: Sends mock booking confirmation emails and other notification emails
5. **event-service-gateway**: Routes client requests to appropriate services
6. **discovery-server**: Provides service discovery for microservices
7. **event-service-config**: Centralized configuration

## External Services Employed
1. **postgres**: backend rdbms database system
2. **redis**: low-latency caching used for rate-limiting in api-gateway
3. **zipkin**: distributed log tracing across microservices
4. **keycloak**: oauth2/openid authentication and authorization
5. **kafka**: message broker for asynchronous microservice communication
6. **schema-registry**: centralized schema management for kafka 
7. **zookeeper**: kafka dependency for distributed configuration management
8. **prometheus**: metrics monitoring
9. **grafana**: visualization of metrics
10. **logstash**: part of ELK stack, used to forward log messages to storage
11. **elasticsearch**: part of ELK stack, used as storage and search engine for logs
12. **kibana**: part of ELK stack, providing web ui used for accessing and visualization of logs

## Technology Stack
- **Spring Boot**: Service Implementation
- **Spring Cloud Gateway**: API Gateway
- **Spring Cloud Config**: Configuration Management
- **Spring Cloud Netflix Eureka**: Service Discovery
- **Spring Security w/Oath2**: Security Implementation
- **Kafka**: Message brokering for event-driven architecture
- **Postgres**: Database-per-microservice design
- **Webflux**: Reactive web architecture utilizing Project Reactor
- **R2DBMS**: Reactive database access
- **TestContainers**: Usage of docker containers during integration tests
- **Resilience4J**: Circuit breaking and fault tolerance
- **Prometheus/Grafana**: Monitoring and Visualization
- **OpenAPI/Swagger**: API Documentation and Test Access

## Architecture Diagrams

For system-architecture diagrams, see [System Architecture](./data/diagrams/system-architecture.mermaid)
For message-flow diagram, see [Message Flow](./data/diagrams/message-flow-diagram.mermaid)

## Accessing API via Swagger

Swagger provides documentation for the system and easy access to try out the endpoints.

To use the Swagger UI while the system is running, go to [Swagger UI](http://localhost:8080/swagger-ui/index.html).

**Note**.  This feature should only be used for non-production settings.  To disable,
Add `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false` to booking-service,
event-service, and api-gateway in a property files setup for production.


## Setting Up Project

### Prerequisites

- JDK 21+
- Docker and Docker Compose

### Required Environmental Properties

A number of environmental properties are needed to run the entire application.

When running the entire application from a single docker-compose file (__NOT_YET_IMPLEMENTED__),
these properties should all be located in an __.env__ file located in the root directory. _docker-compose_
reads from this file automatically.

If running services outside of the _docker-compose_ environment,
then the environmental properties will need to be set in the shell environment being run.

The __.env__ file gets ignored by _git_, so there should be no worries over leaked properties.
When running

The following environmental variables need to be set prior to running the full app:
- **CONFIG_USERNAME** : used by _event-service-config_, _api-gateway_, _booking-service_,

### Building Maven Artifacts

All projects share the same parent modules.  As such, a single command from the project's
root directory can be used build the necessary jar files for the docker-compose file to work.

NOTE: _TestContainers_ are used in the Unit/Integration tests for a number of sub-modules.  Because of this,
the command below should only be run when _Docker Desktop_ (or whatever flavor you have running) is up and running.

To package all necessary modules into jar files, run the following command (Windows users, reverse the slash):

`./mvnw clean package`

Note: if running on a MacOS system with a Apple Silicon M1+ processor, an extra dependency is needed to work properly.
For this scenario, run the command with the _macos-arm_ profile,
as such:

`./mvnw clean package -P macos-arm`

#### Running Integration Tests with Maven

The major microservices all have integration tests around the controller logic, relying primarily on
Testcontainers (keycloak is mocked, while the other services are live)

To run these integration tests, you must have Docker Running and have Docker Compose.
Also, since the ports used are the same as the regular applications, the services from the root _docker-compose.yaml_
file should not be running.

The following command will run all integration tests:

`./mvnw verify` (or `./mvnw verifiy -P macos-arm` if using a Mac with an Apple Silicon M1+ processor)

**Note:** *these tests take some time*

### Setting Up Databases For Each Service

The postgres container in the docker-compose file runs the initialization bash shell scripts from the
_./data/scripts_ directory.  Once done, it closes the container. The container will need to
be restarted again.

I suggest just doing the following command sequence initially to ensure the needed
databases are set up prior to running the other containers:
1. `docker compose up postgres -d` (_prime the database engine_)
2. `docker compose down`
3. `docker compose up -d`

### Setting Up Keycloak

Initially, _keycloak_ is set to import settings from the file _./data/keycloak/piper1970-realm.json_.

However, after the first run of the _keycloak_ container, it should rely on
the _postgres_ database that holds the settings.

__IMPORTANT__: Before running entire system, a client secret needs to be created for the primary client,
_event-service-client_.  See [Defining user credentials](https://www.keycloak.org/docs/latest/server_admin/index.html#ref-user-credentials_server_administration_guide)
for instructions on how to assign a client secret.
Once this secret has been created, the **OAUTH2_CLIENT_SECRET** property in the _.env_ file needs to be set with the new value.


#### Current KeyCloak Setup

The realm in use, piper1970, has 3 default users (user/pass are the same):
- test-member
- test-performer
- test-admin

These users apply to the current authorities/roles in play for this realm:
- MEMBER
- PERFORMER
- ADMIN

- test-member has MEMBER authority
- test-performer has MEMBER and PERFORMER authorities
- test-admin has all three authorities

Keycloak allows for new users to signup. On successful signup, they are given MEMBER authority.

### Running Docker Compose

The _docker-compose.yml_ file is set up to build all local
docker images used in the system. These files require that
maven be called on each project.

The simplest choice here is to run the following command
from the root directory:

`docker compose up -d`

Once done running the application, it called be closed with the following:

`docker compose down`






