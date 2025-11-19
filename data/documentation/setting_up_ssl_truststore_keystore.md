# Keystore & Truststore Setup Instructions

For docker-compose environments using SSL, the following items must be setup for certification trust:
- For __keycloak__, a keystore must be setup, with an exported certificate to be used by microservices
- For __booking-service__, __event-service__ and __api-gateway__, trust stores must be set up with the keycloak certificate imported.
  - __notification-service__ does not require authentication, so a trust store is not necessary.
- For __api-gateway__, an additional keystore is required to secure HTTPS access via proxy to other microservices.

---

## Setting Up Security for Keycloak Service

Setting up the __keycloak__ requires keystore generation and exporting of the main certificate.

To set up a keystore for __keycloak__ with a self-signed certificate, enter the following:

```
keytool -genkeypair alias keycloak -keystore ${KEYCLOAK_KEYSTORE_PATH} -storetype PKCS12 -storepass ${KEYCLOAK_KEYSTORE_PASSWORD} \
-keypass ${KEYCLOAK_KEYSTORE_PASSWORD} -dname "CN=keycloak" -keyalg RSA -keysize 2048 -validity 365
```

To export self-signed certificate, enter the following:

```
keytool -exportcert -alias keycloak -keystore ${KEYCLOAK_KEYSTORE_PATH} -file keycloak_cert.crt -storepass ${KEYCLOAK_KEYSTORE_PASSWORD}
```

## Setting Up Security for api-gateway

Setting up the __api-gateway__ requires setup of both keystore and truststore.<br>
Once the truststore is set up, the certificate from __keycloak__ must be imported into it.

To set up the keystore for __api-gateway__ with a self-signed, certificate, enter the following:

```
keytool -genkeypair alias ${API_KEYSTORE_ALIAS} -keystore ${API_KEYSTORE_PATH} -storetype PKCS12 -storepass ${API_KEYSTORE_PASSWORD} \
keypass ${API_KEYSTORE_PASSWORD} -dname "CN=${API_KEYSTORE_ALIAS}" -keyalg RSA -keysize 2048 -validity 365
```

To set up a truststore for __api-gateway__, enter the following:

```
# Create truststore with a dummy primary user
keytool -genkeypair alias dummy -keystore ${API_TRUSTSTORE_PATH} -storetype PKCS12 \
-storepass ${API_TRUSTSTORE_PASSWORD} -keypass ${API_TRUSTSTORE_PASSWORD} \
-keyalg RSA -keysize 2048

# Delete dummy user from truststore
keytool -delete -alias dummy -keystore ${API_TRUSTSTORE_PATH} -storepass ${API_TRUSTSTORE_PASSWORD}

# Insert keycloak certificate into truststore
keytool -importcert -alias keycloak -keystore ${API_TRUSTSTORE_PATH} \
-file keycloak_cert.crt -storepass ${API_TRUSTSTORE_PASSWORD}
```

## Setting Up Security for booking-service

Because __api-gateway__ acts as a proxy, __booking-service__ does not require a keystore.<br>
However, to access __keycloak__ on an HTTPS url, a truststore with keycloak's certificate
is required.

To set up a truststore for __booking-service__, enter the following:

```
# Create truststore with a dummy primary user
keytool -genkeypair alias dummy -keystore ${BOOKINGS_TRUSTSTORE_PATH} -storetype PKCS12 \
-storepass ${BOOKINGS_TRUSTSTORE_PASSWORD} -keypass ${BOOKINGS_TRUSTSTORE_PASSWORD} \
-keyalg RSA -keysize 2048

# Delete dummy user
keytool -delete -alias dummy -keystore ${BOOKINGS_TRUSTSTORE_PATH} \
-storepass ${BOOKINGS_TRUSTSTORE_PASSWORD}

# Insert keycloak certificate into truststore
keytool -importcert -alias keycloak -keystore ${BOOKINGS_TRUSTSTORE_PATH} \
-file keycloak_cert.crt -storepass ${BOOKINGS_TRUSTSTORE_PASSWORD}
```


## Setting Up Security for event-service

Because __api-gateway__ acts as a proxy, __event-service__ does not require a keystore.<br>
However, to access __keycloak__ on an HTTPS url, a truststore with keycloak's certificate
is required.

To set up a truststore for __event-service__, enter the following:

```
# Create truststore with a dummy primary user
keytool -genkeypair alias dummy -keystore ${EVENTS_TRUSTSTORE_PATH} -storetype PKCS12 \
-storepass ${EVENTS_TRUSTSTORE_PASSWORD} -keypass ${EVENTS_TRUSTSTORE_PASSWORD} \
-keyalg RSA -keysize 2048

# Delete dummy user
keytool -delete -alias dummy -keystore ${EVENTS_TRUSTSTORE_PATH} \
-storepass ${EVENTS_TRUSTSTORE_PASSWORD}

# Insert keycloak certificate into truststore
keytool -importcert -alias keycloak -keystore ${EVENTS_TRUSTSTORE_PATH} \
-file keycloak_cert.crt -storepass ${EVENTS_TRUSTSTORE_PASSWORD}
```
