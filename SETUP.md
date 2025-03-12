# Setting Up Project

## Prerequisites

- JDK 21+
- Maven
- Docker and Docker Compose

## Environment Files Used

## Building Maven Artifacts

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

An __.env__ file is needed in the root directory for docker compose to run correctly.
__.gitignore__ is set up to ignore saving this file.

See _env-sample_ in the root directory for a list of all environment variable used.

## Setting Up Databases For Each Service

The postgres container in the docker-compose file runs the initialization scripts from the 
_./data/scripts_ directory.  Once done, it closes the container. The container will need to
be restarted again.

I suggest just doing the following command sequence initially to ensure the needed
databases are set up prior to running the other containers:
1. `docker compose up postgres -d` (_prime the database engine_)
2. `docker compose down`
3. `docker compose up -d`

## Setting Up Keycloak

Initially, _keycloak_ is set to import settings from the file _./data/keycloak/piper1970-realm.json_. 

However, after the first run of the _keycloak_ container, it should rely on
the _postgres_ database that holds the settings.

After running docker compose for the first time, comment the line
out that sets the container to run off the realm file:

- `- --import-realm`  --->   `#      - --import-realm`

### Current KeyCloak Setup

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

Keycloak allows for new users to signup. On successful signup, they are given MEMBER authority

It should be noted that I set up KeyCloak with limited functionality and security.
I would not recommend using this in any setting other than development.


## Running Docker Compose 

The _docker-compose.yml_ file is set up to build all local
docker images used in the system. These files require that
maven be called on each project.

The simplest choice here is to run the following command
from the root directory:

`docker compose up -d`

Once done running the application, it called be closed with the following:

`docker compose down`


