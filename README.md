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
1. **event-service**: Manages event information, creation and management
2. **booking-service**: Handles booking requests associated with event-service events
3. **notification-service**: Sends booking confirmations and other notifications via email
4. **event-service-gateway**: API Frontend that routes client requests to appropriate services
5. **discovery-server**: Provides service discovery for microservices
6. **event-service-config**: Provides centralized configuration

## External Services Employed
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
- **Logstash/ElasticSearch/Kibana(ELK)**: Centralized log aggregation and searching
- **OpenAPI/Swagger**: API Documentation and Test Access

## Architecture Diagrams

A system-architecture diagram can be found at [System Architecture](./data/diagrams/system-architecture.mermaid).

A message-flow diagram can be found at [Message Flow](./data/diagrams/message-flow-diagram.mermaid).

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

See the __env-sample__ file in root directory for all possible environmental properties to use

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

### Initializing the Postgres database tables.

The postgres container in the docker-compose file runs the initialization shell scripts from the local
_./data/scripts_ directory, initializing all databases and users needed both for keycloak and the three main microservices. 
See [Database Initialization Scripts](./data/scripts).

### Setting Up Keycloak

Initially, _keycloak_ is set to import settings from the file _./data/keycloak/piper1970-realm.json_. See [Imported Keycloak Realm](./data/keycloak/piper1970-realm.json).

However, after the first run of the _keycloak_ container, it should rely on
the _postgres_ database that holds the settings.  
Before running the system, credentials need to be made for the client, _**event-service-client**_, in the _**piper1970**_ realm.  
Once the credentials have been made, they need to be stored in _**OAUTH2_CLIENT_SECRET**_ property in the _.env_ file in the project directory.

See [Defining user credentials](https://www.keycloak.org/docs/latest/server_admin/index.html#ref-user-credentials_server_administration_guide)
for instructions on how to assign a client secret.  

To access the keycloak server, go to _**http://localhost:8180**_ and login with credentials stored in _.env_ file (_**KC_ADMIN**_ and _**KC_ADMIN_PASSWORD**_).  


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

## Running microservices locally

_**Important**_: due to failfast logic tied to centralized configuration access, 
other than the _discovery-server_ and _event-service-config_ modules, all other services
may immediately fail until the _event-service-config_ is up and running.  
This behavior is normal.  
Just keep trying again until the microservice starts up normally.

### Setting up environment from _.env_ file 

Docker-Compose automatically extracts environment variables from the _.env_ file.  
All microservices need to also be using the environment variables from the _.env_ file to stay in sync.  
The following commands will load the environment variables from _.env_ into a shell environment,
depending on which system you are running under. Run them from the project directory:

#### For Mac/Linux users (using Bash):
- `export $(sed '/^$/d' .env | sed '/^#/d' | xargs)`

#### For Windows Users (using Powershell):
- `Get-Content .env | Where-Object {$_ -match '\S' -and $_ -notmatch '^#'} | ForEach-Object {$name,$value = $_ -split '=',2; Set-Item "env:$name" $value}`

Assuming all external containers are running via docker compose,
the following commands should be run, in the given order, from the project directory.  
For Windows users, make sure to adjust the paths to use backslashes instead of forward slashes.  
Also, consider using different shells for each service, with environment variables set in each shell individually. It makes it much easier.

1. discovery-server (running Eureka server for local discovery)
   1. `java -jar -Dspring.profiles.active=local_discovery ./discovery-server/target/discovery-server-0.1.1-SNAPSHOT.jar`
2. event-service-config (using profile `native` to using filesystem-based config backend)
   1. `java -jar -Dspring.profiles.active=native,local_discovery ./event-service-config/target/event-service-config-0.0.1-SNAPSHOT.jar`
3. booking-service (using Eureka client for local discovery)
   1. `java -jar -Dspring.profiles.active=local_discovery ./booking-service/target/booking-service-0.0.1-SNAPSHOT.jar`
4. event-service (using Eureka client for local discovery)
   1. `java -jar -Dspring.profiles.active=local_discovery ./event-service/target/event-service-0.0.1-SNAPSHOT.jar`
5. notification-service (using Eureka client for local discovery)
   1. `java -jar -Dspring.profiles.active=local_discovery ./notification-service/target/notification-service-0.0.1-SNAPSHOT.jar`
6. api-gateway (using Eureka client for local discovery)
   1. `java -jar -Dspring.profiles.active=local_discovery ./api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar`



