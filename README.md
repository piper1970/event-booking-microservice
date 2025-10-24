# Event-Booking Service (POC, WIP)

_Spring Boot & Kafka-based microservice for cloud-native event systems._

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
- **Redis**: In-Memory Database for API Gateway Rate-limiting
- **Webflux**: Reactive web architecture utilizing Project Reactor
- **R2DBMS**: Reactive database access
- **TestContainers**: Usage of docker containers during integration tests
- **Resilience4J**: Circuit breaking and fault tolerance
- **Prometheus/Grafana**: Monitoring and Visualization
- **Logstash/ElasticSearch/Kibana(ELK)**: Centralized log aggregation and searching
- **OpenAPI/Swagger**: API Documentation and Test Access

---

## Architecture Diagrams

A system-architecture diagram can be found at [System Architecture](./data/diagrams/system-architecture.mermaid).

A message-flow diagram can be found at [Message Flow](./data/diagrams/message-flow-diagram.mermaid).

---

## Basic Application Flow

- All API access goes through the api, at address `http://localhost:8080`
- Events get created and accessed via the event-service api
  - The basic access point for accessing events is at `http://localhost:8080/api/events`
  - Events can be created, retrieved, updated, and cancelled, using `POST`, `GET`, `PUT`, and `PATCH` methods
  - In order to modify an event, the user must be the owner of the event, and have the role `PERFORMER`.
  - In order to view an event, the user must be registered as a `MEMBER`.
  - Anyone who signs up via the OAuth2 server automatically is given `MEMBER` access
- If someone wishes to book an event, this is handled by the booking-service api
  - The basic access point for the booking service is at `http://localhost:8080/api/bookings`
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
    - The keycloak console can be reached at `http://localhost:8180` with the credential _KC_ADMIN_ and _KC_ADMIN_PASSWORD_ provided in the _.env_ file 

---

## Setting Up Project

### Prerequisites

- JDK 21+
- Docker and Docker Compose

### Required Environmental Properties

A number of environmental properties are needed to run the entire application.

When running the entire application from a single docker-compose file,
these properties should all be located in an __.env__ file located in the root directory. _docker-compose_
reads from this file automatically.

If running services outside the _docker-compose_ environment,
then the environmental properties will need to be set in the shell environment being run.

The __.env__ file gets ignored by _git_, so there should be no worries over leaked properties.

See the __.env-sample__ file in root directory for all possible environmental properties to use

### Building Maven Artifacts

All projects share the same parent modules.  As such, a single command from the project's
root directory can be used build the necessary jar files for the docker-compose file to work.

**Note:** _TestContainers_ are used in the Unit/Integration tests for a number of submodules.  Because of this,
the command below should only be run when _Docker Desktop_ (or whatever flavor you have running) is up and running.

To package all necessary modules into jar files, run the following command (Windows users, reverse the slash):

`./mvnw clean package`

**Note:** if running on a macOS system with an Apple Silicon M1+ processor, an extra dependency is needed to work properly.
For this scenario, run the command with the _macos-arm_ profile,
as such:

`./mvnw clean package -P macos-arm`

### Running Integration Tests with Maven

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

 _keycloak_ is set up to import realm settings from a file in a _./data/keycloak/_ subdirectory. 

Depending on whether the microservices are run locally or fully dockerized, the import settings are slightly different, which effect the issued JWT token.
Because of this, different import are used in each setting.

- When running against _docker-compose.yaml_, the import file is _./data/keycloak/compose/all_realms.json_.

- When running against _docker-compose-full.yaml_, the import file is _./data/keycloak/compose-full/all_realms.json_.

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

Promoting users to __PERFORMER__ or __ADMIN__ must be done manually through keycloak console

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

### Setting up environment

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

### Launching all microservices manually

**Note:** Be sure to build artifacts before running. See [Building Maven Artifacts](#building-maven-artifacts).

The following microservices should be launched in this order.
1. discovery-server (running Eureka server for local discovery)
   1. `java -jar ./discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar`
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

### Running Docker Compose

The _docker-compose.yml_ file is set up to run external services, such as databases, oauth2 servers, kafka, etc.

To start up the service, run `docker compose up -d`.  

Once done, and all local services have stopped, run `docker compose down` to shut down all the docker services.

## Running entire system within Docker via Docker Compose

### Prepping the environment

_**Important**_: due to naming issues with OAuth2 JWT tokens distributed by Keycloak,
an alias - _**127.0.0.1 keycloak**_ - needs to be added to end of the _**hosts**_ file.

For __Windows__, the _host_ file can be found at '__C:\Windows\System32\drivers\etc\hosts__'.

For __Linux__, the _host_ file can be found at '__/etc/hosts__'.

For __macOS__, the _host_ file can be found at '__/private/etc/hosts__'.

### Running Entire System within Docker

To run the entire system from within a Docker-Compose environment, you must first ensure
all the jar files are available in their respective target directories.  

See [Building Maven Artifacts](#building-maven-artifacts) for 
building the jar files.

Once the jar files have been built, run the following command from the root directory: 
`docker compose -f docker-compose-full.yaml up -d`.

Once everything is up and running, you can access the api at `http://localhost8080`, either via Swagger or Postman

Once done, run `docker compose -f docker-compose-full.yaml down` to stop all the services.

___

## Running with SSL/TLS enabled

SSL can be enabled in the api gateway and keycloak server.  
The profiles ssl_local and ssl_compose can be used to trigger these features.

### Keystore & Truststore Setup

To run keycloak with SSL enabled, a keystore must be setup with a certificate for the alias __keycloak__.

To run api-gateway with SSL enabled, a keystore must be setup with a certificate for the alias __api-gateway__.

For the microservices to be able to log into keycloak for OAuth2 authentication, a truststore must be setup for __api-gateway__, __booking_service__, and __event_service__ with a public certificate from
the keystore created by keycloak.

#### Credentials Needed

The directory __./certs__ has been included in the _.gitignore_ file, so this would be a good place to put the Keystores and Trust-stores.

To use with the code below, the _.env_ file needs to be loaded into memory.  See [Setting up environment](#setting-up-environment)

The following credentials should be setup in the __.env__ file,
- API_KEYSTORE_PATH (_full path to keystore for api-gateway_)
- API_KEYSTORE_PASSWORD (_password for keystore used by api-gateway_)
- API_KEYSTORE_ALIAS (_alias for api-gateway_)
- API_TRUSTSTORE_PATH (_full path to truststore used by api-gateway_)
- API_TRUSTSTORE_PASSWORD (_password for truststore used by api-gateway_)
- BOOKINGS_TRUSTSTORE_PATH (_full path to truststore used by booking-service_)
- BOOKINGS_TRUSTSTORE_PASSWORD (_password for truststore used by booking-service_)
- EVENTS_TRUSTSTORE_PATH (_full path to truststore used by event-service_)
- EVENTS_TRUSTSTORE_PASSWORD (_password for truststore used by event-service_)
- KEYCLOAK_KEYSTORE_PATH (_full path to keystore used by keycloak_)
- KEYCLOAK_KEYSTORE_PASSWORD (_password for keystore used by keycloak_)

#### Keystore/Truststore Setup Instructions

- To set up a keystore for __keycloak__ with a self-signed certificate, enter the following:

```
# Generate keycloak keystore
keytool -genkeypair alias keycloak -keystore ${KEYCLOAK_KEYSTORE_PATH} -storetype PKCS12 -storepass ${KEYCLOAK_KEYSTORE_PASSWORD} \
-keypass ${KEYCLOAK_KEYSTORE_PASSWORD} -dname "CN=keycloak" -keyalg RSA -keysize 2048 -validity 365
```

- To set up keystore for __api-gateway__ with a self-signed certificate, enter the following:

```
# Generate api-gateway keystore
keytool -genkeypair alias ${API_KEYSTORE_ALIAS} -keystore ${API_KEYSTORE_PATH} -storetype PKCS12 -storepass ${API_KEYSTORE_PASSWORD} \
keypass ${API_KEYSTORE_PASSWORD} -dname "CN=${API_KEYSTORE_ALIAS}" -keyalg RSA -keysize 2048 -validity 365
```

- To set up the truststore for the microservices, first a certificate must be exported from the keycloak keystore:

```
# Export certificate from keycloak keystore into file keycload_cert.crt
keytool -exportcert -alias keycloak -keystore ${KEYCLOAK_KEYSTORE_PATH} -file keycloak_cert.crt -storepass ${KEYCLOAK_KEYSTORE_PASSWORD}
```

Once the certificate is created, the truststore components can be built.

- To set up a truststore for __api-gateway__, enter the following:

```
# Create truststore with a dummy primary user for api-gateway
keytool -genkeypair alias dummy -keystore ${API_TRUSTSTORE_PATH} -storetype PKCS12 \
-storepass ${API_TRUSTSTORE_PASSWORD} -keypass ${API_TRUSTSTORE_PASSWORD} \
-keyalg RSA -keysize 2048

# Delete dummy user
keytool -delete -alias dummy -keystore ${API_TRUSTSTORE_PATH} -storepass ${API_TRUSTSTORE_PASSWORD}

# Insert keycloak certificate into api-gateway truststore
keytool -importcert -alias keycloak -keystore ${API_TRUSTSTORE_PATH} \
-file keycloak_cert.crt -storepass ${API_TRUSTSTORE_PASSWORD}
```

- To set up a truststore for __booking-service__, enter the following:

```
# Create truststore with a dummy primary user for booking-service
keytool -genkeypair alias dummy -keystore ${BOOKINGS_TRUSTSTORE_PATH} -storetype PKCS12 \
-storepass ${BOOKINGS_TRUSTSTORE_PASSWORD} -keypass ${BOOKINGS_TRUSTSTORE_PASSWORD} \
-keyalg RSA -keysize 2048

# Delete dummy user
keytool -delete -alias dummy -keystore ${BOOKINGS_TRUSTSTORE_PATH} \
-storepass ${BOOKINGS_TRUSTSTORE_PASSWORD}

# Insert keycloak certificate into booking-service truststore
keytool -importcert -alias keycloak -keystore ${BOOKINGS_TRUSTSTORE_PATH} \
-file keycloak_cert.crt -storepass ${BOOKINGS_TRUSTSTORE_PASSWORD}
```

- To set up a truststore for __event-service__, enter the following:

```
# Create truststore with a dummy primary user for event-service
keytool -genkeypair alias dummy -keystore ${EVENTS_TRUSTSTORE_PATH} -storetype PKCS12 \
-storepass ${EVENTS_TRUSTSTORE_PASSWORD} -keypass ${EVENTS_TRUSTSTORE_PASSWORD} \
-keyalg RSA -keysize 2048

# Delete dummy user
keytool -delete -alias dummy -keystore ${EVENTS_TRUSTSTORE_PATH} \
-storepass ${EVENTS_TRUSTSTORE_PASSWORD}

# Insert keycloak certificate into event-service truststore
keytool -importcert -alias keycloak -keystore ${EVENTS_TRUSTSTORE_PATH} \
-file keycloak_cert.crt -storepass ${EVENTS_TRUSTSTORE_PASSWORD}
```

### Running SSL with local microservices

To run all microservices locally, the keycloak and other external services need to be deployed using the _docker-compose-ssl.yaml_ file

To deploy, run the following command:

```
docker compose -f docker-compose-ssl.yaml up -d
```

Once all external services are up and running, then the microservices need to be deployed.

#### Launching all microservices manually

**Note:** Be sure to build artifacts before running. See [Building Maven Artifacts](#building-maven-artifacts).

The following microservices should be launched in this order.
1. discovery-server (running Eureka server for local discovery)
    1. `java -jar ./discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar`
2. event-service-config (using profile `native` to using filesystem-based config backend)
    1. `java -jar -Dspring.profiles.active=native,local_discovery ./event-service-config/target/event-service-config-0.0.1-SNAPSHOT.jar`
3. booking-service (using Eureka client for local discovery)
    1. `java -jar -Dspring.profiles.active=ssl_local ./booking-service/target/booking-service-0.0.1-SNAPSHOT.jar`
4. event-service (using Eureka client for local discovery)
    1. `java -jar -Dspring.profiles.active=ssl_local ./event-service/target/event-service-0.0.1-SNAPSHOT.jar`
5. notification-service (using Eureka client for local discovery)
    1. `java -jar -Dspring.profiles.active=ssl_local ./notification-service/target/notification-service-0.0.1-SNAPSHOT.jar`
6. api-gateway (using Eureka client for local discovery)
    1. `java -jar -Dspring.profiles.active=ssl_local ./api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar`

Once everything is up and running, you can access the api at `https://localhost:9000`, either via Swagger or Postman

Once done, run hit `docker compose -f docker-compose-ssl.yaml down` to stop all external services.

### Running Entire System within Docker using SSL

To run the entire system from within a Docker-Compose environment, you must first ensure
all the jar files are available in their respective target directories.

See [Building Maven Artifacts](#building-maven-artifacts) for
building the jar files.

Once the jar files have been built, run the following command from the root directory:

`docker compose -f docker-compose-ssl-full.yaml up -d`.

Once everything is up and running, you can access the api at `https://localhost:9000`, either via Swagger or Postman

Once done, run `docker compose -f docker-compose-ssl-full.yaml down` to stop all the services.

---

## Accessing the API via Swagger (preferred)

Swagger provides documentation for the system and easy access to try out the endpoints.

To use the Swagger UI while the system is running:

- Using http: 
  - Go to [http://localhost:8080/v3/swagger-ui/index.html](http://localhost:8080/v3/swagger-ui/index.html).
- Using https:
  - Go to [https://localhost:9000/v3/swagger-ui/index.html](https://localhost:9000/v3/swagger-ui/index.html);

## Accessing the API via Postman

If you want to run this system using __Postman__, there are four JSON files in the _data/postman_ directory: two for local (http/https) use and the other two for with fully dockerized use (http/https).

All Postman configurations require the following properties to be set as secrets in the Postman Environments area: `oauth_client_id` and `oauth_client_secret`.

Refer to the `OAUTH2_CLIENT_ID` and `OAUTH2_CLIENT_SECRET` properties in the `.env` file.
