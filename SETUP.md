# Setting Up Project

## Prerequisites

- JDK 21+
- Maven
- Docker and Docker Compose

## Environment Files Used

An __.env__ file is needed in the root directory for docker compose to run correctly.
__.gitignore__ is set up to ignore saving this file.

See _env-sample_ in the root directory for a list of all environment variable used.

## Setting Up Databases For Each Service (??? - may not need this if postgres works properly)

For simplicity's sake, only one running instance of postgres is set up in the _docker-compose.yml_
file. 

Four services rely on this service, so prior to running all the services in the
_docker-compose.yml_ file, you will need to prime the postgres container.

To startup just the __psql__ client on the _postgres_ container, do the following:
1. Make sure _.env_ file is setup in root directory
2. Run `docker compose up postgres -d` to startup container
3. Run `docker compose exec postgres psql -U postgres` to exec into container and run the native client
4. Go (locally) to the _./data/scripts_ directory to find startup scripts for each service
5. Open each file up, running each command individually in __psql__, making sure to replace instances of _<see .env file>_ with values from _.env_ file before running command
6. Once done type `\q` to exit the app and the container.
7. Run `docker compost down` to bring down the _postgres_ container.

From this point on, the database-server is set up to handle all four databases.

## Setting Up Keycloak

Initially, _keycloak_ is set up in the _docker-compose.yml_ run based on the file _./data/keycloak/piper1970-realm.json_. 

However, after the first run of the _keycloak_ container, it will rely on
the _postgres_ instance to store settings.

After running docker compose for the first time, comment the line
out that sets the container to run off the realm file:

- `- --import-realm`  ->   `#      - --import-realm`

### Current KeyCloak Setup

The realm in use, piper1970, has 3 default users (user/pass are the same string):
- test-member
- test-performer
- test-admin

These users apply to the current authorities/roles in play for this realm:
- MEMBER
- PERFORMER
- ADMIN


- test-member only has MEMBER authority
- test-performer has MEMBER and PERFORMER authorities 
- test-admin has all three authorities


Keycloak allows for new users to signup. On successful signup, they are given MEMBER authority


## Running Docker Compose 

The _docker-compose.yml_ file is set up to build all local
docker images used in the system. These files require that
maven be called on each project.

The simplest choice here is to run the following command
from the root directory


