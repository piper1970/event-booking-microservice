# Event-Booking Service (POC)

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
3. **payment-service**: Processes payments and refunds for bookings
4. **notification-service**: Sends mock email notifications
5. **event-service-gateway**: Routes client requests to appropriate services
6. **discovery-server**: Provides service discovery for microservices
7. **event-service-config**: Centralized configuration

# External Services Employed
1. **postgres**: backend rdbms database system
2. **zipkin**: distributed log tracing across microservices
3. **keycloak**: oauth2/openid authentication and authorization
4. **kafka**: message broker for asynchronous microservice communication
5. **zookeeper**: kafka dependency for distributed configuration management
6. **wiremock**: library used to mock external payment api
7. **prometheus**: metrics monitoring
8. **grafana**: visualization of metrics

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
- **Resilience4J**: Circuit breaking and fault tolerance
- **Prometheus/Grafana**: Monitoring and Visualization

## Setup
See _./SETUP.md_ file for further setup instructions




