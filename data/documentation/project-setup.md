# Project Setup

## Prerequisites

- JDK 21+
- Docker and Docker Compose

## Required Environmental Properties

Numerous environmental properties are needed to run the entire application.<br>
When running the entire application from a single docker-compose file,
these properties should all be located in an __.env__ file located in the root directory. All _docker-compose_
variants read directly from this file.

If running microservices outside the _docker-compose_ environment,
then the environmental properties will need to be set in the shell environment being run.<br>
See [Setting up environment for locally-run microservices](../../README.md#setting-up-environment-for-locally-run-microservices) for instructions on loading properties from the __.env__ file directly into the local environment.

NOTE: The [.gitignore](../../.gitignore) file lists the __.env__ file, preventing its sensitive properties from being committed.<br>
See __.env-sample__ file in root directory for all possible environmental properties needed.

## Building Maven Artifacts

All projects share the same parent modules.  As such, a single command from the project's
root directory can be used build the necessary jar files for the docker-compose file to work.

To package all necessary modules into jar files, run the following command (Windows users, reverse the slash):

`./mvnw clean package`

**Note:** if running on a macOS system with an Apple Silicon M1+ processor, an extra dependency is needed to work properly.<br>
For this scenario, run the command with the _macos-arm_ profile,
as such:

`./mvnw clean package -P macos-arm`

## Running Integration Tests with Maven

The major microservices all have integration tests around the controller logic, relying primarily on
Testcontainers (keycloak is mocked, while the other services are live)

To run these integration tests, you must have the Docker daemon running.
Additionally, since the ports used in the integration tests are the same as the ones used when running normally,
all normally-run docker services should be stopped while running integration tests.

**Note:** _TestContainers_ are used in the Integration tests for a number of submodules.  Because of this,
the command below should only be run when _Docker_  is up and running.

The following command will run all integration tests:

`./mvnw verify` _(or `./mvnw verifiy -P macos-arm` if using a Mac with an Apple Silicon M1+ processor)_

**Note:** Integration tests can be time-consuming, due to the number of test containers running.

## Setting Up External Docker Resources

Certain docker containers require external configuration.<br>
These configuration scripts can be found in the [data](../../data) directory.

### PostgreSQL Initialization

The postgres container in the docker-compose file runs the initialization shell scripts, initializing
all databases and users needed both for keycloak and the three main microservices.<br>
See setup scripts in [data/scripts](../../data/scripts) directory.

### Keycloak Initialization

_keycloak_ is set up to import realm settings from a file in a _./data/keycloak/_ subdirectory.

Depending on whether the microservices are run locally or fully dockerized, the import settings are slightly different, which effect the issued JWT token.
Because of this, different import files are used in each setting.

- _docker-compose.yaml_ 
  - uses [data/keycloak/compose/all_realms.json](../../data/keycloak/compose/all_realms.json).
- _docker-compose-full.yaml_
  - uses [data/keycloak/compose-full/all_realms.json](../../data/keycloak/compose-full/all_realms.json).
- _docker-compose-ssl.yaml_
  - uses [data/keycloak/compose-ssl/all_realms.json](../../data/keycloak/compose-ssl/all_realms.json).
- _docker-compose-ssl-full.yaml_
  - uses [data/keycloak/compose-ssl-full/all_realms.json](../../data/keycloak/compose-ssl-full/all_realms.json).

To access the keycloak server, go to _**http://localhost:8180** _( https://localhost:8443 for SSL versions )_ and login with credentials stored in _.env_ file ( _**KC_ADMIN**_ and _**KC_ADMIN_PASSWORD**_ ).


#### Current KeyCloak Setup

The realm in use, **piper1970**, has 2 default users for testing (user/pass are the same):
- test-member
- test-performer

These users apply to the current authorities/roles in play for this realm:
- MEMBER
- PERFORMER

- test-member has MEMBER authority
- test-performer has MEMBER and PERFORMER authorities

Keycloak allows for new users to signup. On successful signup, they are given MEMBER authority.<br>
Promoting users to __PERFORMER__  must be done manually through keycloak console

### ELK (ElasticSearch, LogStash, Kibana) Certificates Setup

A one-time compose job, _setup-elk-certs-job_, relies on a yaml file for setting up certificates used by
_ElasticSearch_, _LogStash_, and _Kibana_. containers.<br>
See [data/elk/instances.yml](../../data/elk/instances.yml)

### LogStash Setup

Depending on whether the microservices are deployed locally or in a docker environment, LogStash requires
a configuration script to run.

If running microservices locally, the script used sets up a file log reader, capturing logs from the `./logs` directory.<br>
In this situation, each microservice's _logback-spring.xml_ resource file sets up a file appender to store logs locally.<br>
See [data/logstash/logstash-local.conf](../../data/logstash/logstash-local.conf)

If running microservices within the docker context, the script used sets up a tcp log reader, capturing logs via tcp sockets.<br>
In this situation, each microservice's _logback-spring.xml_ resource file sets up a tcp socket appender to push logs out via tcp socket channel.<br>
See [data/logstash/logstash-compose.conf](../../data/logstash/logstash-compose.conf)

### Prometheus Setup

Prometheus requires a configuration script to launch.
This script sets up metric scraping jobs for each of the microservices, along with the api-gateway.<br>
Depending on which docker-compose file is used to deploy services, a different config is used.

- **docker-compose.yaml**
    - uses [data/prometheus/prometheus-local.yml](../../data/prometheus/prometheus-local.yml)

- **docker-compose-full.yaml**
    - uses [data/prometheus/prometheus-compose_full.yml](../../data/prometheus/prometheus-compose_full.yml)

- **docker-compose-ssl.yaml**
    - uses [data/prometheus/prometheus-ssl_local.yml](../../data/prometheus/prometheus-ssl_local.yml)

- **docker-compose-ssl-full.yaml**
    - uses [data/prometheus/prometheus-compose-ssl_full.yml](../../data/prometheus/prometheus-compose-ssl_full.yml)

### Grafana Setup

Grafana uses a common configuration file for all docker compose instances.<br>
This configuration file, [data/grafana/datasources.yml](../../data/grafana/datasources.yml), sets up connection to prometheus server.