#!/bin/sh

# substitute KC_REALM env variable in template files and export to JSON file
# envsubst < /opt/keycloak/data/import/all_realms.json.template > /opt/keycloak/data/import/all_realms.json
sed "s/\${KC_REALM}/${KC_REALM}/g" < /opt/keycloak/data/import/all_realms.json.template > /opt/keycloak/data/import/all_realms.json

# manually call entrypoint from parent keycloak Dockerfile
exec /opt/keycloak/bin/kc.sh "$@"