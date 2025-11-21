# Docker Environment Setup for Local Microservice Deployments with SSL enabled

The default [docker-compose-ssl.yaml](../../docker-compose-ssl.yaml) file is set up to build an SSL-enabled minimal docker environment with only external services.<br>
All properties stored in the _.env_ file in the root directory are automatically loaded when this yaml file is applied.

Once this docker environment is running, each individual microservice must be manually started before the systems
is considered active.

## Ensuring Proper Keystore & Truststore access

Since this version utilizes SSL capabilities, all keystore and truststore files must be present and accessible.
See [Keystore & Truststore Setup Instructions](setting_up_ssl_truststore_keystore.md) for instructions on setting this up.

## Starting up docker environment

To start up the docker environment, enter the following command:
`docker compose -f docker-compose-ssl.yaml up -d`

To tear down the docker services once done running, enter the following command:
`docker compose -f docker-compose-ssl.yaml down`;

### Docker Services Running
The following services are loaded from the docker environment:

- **postgres**: persistent database used by keycloak and all microservice
- **redis**: key-value database used by api-gateway for host-based rate limiting features
- **zipkin**: service used for enhanced traceability between microservices
- **keycloak**: OAuth2/OpenId authorization server used for security (SSL-enabled)
- **kafka**: messaging service used between microservices for asynchronous communication
- **zookeeper**: service used by kafka to store broker configuration settings
- **schema-registry**: service used by kafka for accessing Avro-based schemas used for message serialization and deserialization
- **prometheus**: service used to scrape microservice metrics from microservice actuator endpoints
- **grafana**: service used for visual representation of microservice metrics captured by prometheus
- **logstash**: service for aggregating multiple log sources for downstream indexing
- **elasticsearch**: service for indexing aggregated logs
- **kibana**: service offering visual representation of indexed logs

## Starting up local microservices (SSL-enabled)

### Building Microservices With Maven

Prior to running microservices locally, all artifacts must be built with the **maven** wrapper.<br>
See [Building Maven Artifacts](project-setup.md#building-maven-artifacts) for instructions to build the jar artifacts.

### Running Microservices Locally

Prior to starting up the local microservices, the properties stored in the _**.env**_ file need to be loaded into the running environment.<br>
See [Setting up environment for locally-run microservices](../../README.md#setting-up-environment-for-locally-run-microservices) for steps to load the _**.env**_ file into the current running environment.

The following microservices should be start up, in the following order, from the root directory:

1) **DiscoveryServer**: `java -jar ./discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar`
2) **EventServiceConfig**: `java -jar -Dspring.profiles.active=native,local_discovery ./event-service-config/target/event-service-config-0.0.1-SNAPSHOT.jar`
3) **BookingService**: `java -jar -Dspring.profiles.active=ssl_local ./booking-service/target/booking-service-0.0.1-SNAPSHOT.jar`
4) **EventService**: `java -jar -Dspring.profiles.active=ssl_local ./event-service/target/event-service-0.0.1-SNAPSHOT.jar`
5) **NotificationService**: `java -jar -Dspring.profiles.active=ssl_local ./notification-service/target/notification-service-0.0.1-SNAPSHOT.jar`
6) **ApiGateway**: `java -jar -Dspring.profiles.active=ssl_local ./api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar`

_Note_: _**BookingService**_, _**EventService**_, _**NotificationService**_, and _**ApiGateway**_ all require configuration information
stored in _**EventServiceConfig**_.  If _**EventServiceConfig**_ is not yet up and running, the other microservices will immediately fail.<br>
This is by design.  When this happens, just keep re-attempting to restart the microservices until they eventually succeed.

_Note_: For best performance, running each microservice in its own shell environment is recommended.
That way, each microservice can be easily stopped and log messages will not be intermixed between the services.
