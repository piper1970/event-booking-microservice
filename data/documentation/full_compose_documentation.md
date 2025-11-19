# Docker Environment Complete Setup without SSL

The default [docker-compose-full.yaml](../../docker-compose-full.yaml) file is set up to run an entire system within a docker environment.<br>
All properties stored in the _.env_ file in the root directory are automatically loaded when this yaml file is applied.

## Building Microservices With Maven

Before the docker environment can be run, all artifacts must be built with the **maven** wrapper.<br>
See [Building Maven Artifacts](project-setup.md#building-maven-artifacts) for instructions to build the jar artifacts.

## Starting up docker environment

To start up the docker environment, enter the following command:
`docker compose -f docker-compose-ssl-full.yaml up -d`

To tear down the docker services once done running, enter the following command:
`docker compose -f docker-compose-ssl-full.yaml down`;

### Docker Services Running
All services are loaded from the docker environment:

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
- **discovery-server**: manages dynamic routing of microservices within docker environment
- **event-service-config**: centralized property access within docker environment
- **api-gateway**: Proxy service for access to other microservices.  SSL-enabled and accessed at port 9000.
- **booking-service**: microservice for creation and management of bookings for events. No external port access.
- **event-service**: microservice for creation and management of events. No external port access.
- **notification-service**: microservice for handling booking confirmation emails processing. No external access.


