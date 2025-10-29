# Mustache Files for Outgoing Emails

The mustache files in this directory hold very limited html, without any styling.

Their main purpose is to send out templated notifications to the users that are attempting to book events.
They are also used to notify users of changes to the bookings and/or events.

## Booking Created Mustache
This template is used to send out customized emails to the user when they are first attempting to book an event.

The following values are embedded in the template:
- username
  - name provided by user when signing up with Keycloak/OAuth2
- confirmationLink
  - dynamic url for the user to click to confirm the booking
- formattedExpirationDate
  - gives the user the timeframe needed to confirm the booking
- eventLink
  - secure link to the event
- bookingLink
  - secure link to the booking

## Booking Cancelled Mustache
This template is used to send out customized emails to the user when a booking for an event has been cancelled.
Cancellation would typically be done by the user. No admin cancellation privileges have been set in the app.

The following values are embedded in the template:
- username
    - name provided by user when signing up with Keycloak/OAuth2
- eventLink
    - secure link to the event
- bookingLink
    - secure link to the booking

## Booking Updated Mustache
This template is used to notify the user when the event the user has booked is updated.

The following values are embedded in the template:
- username
    - name provided by user when signing up with Keycloak/OAuth2
- eventLink
    - secure link to the event
- bookingLink
    - secure link to the booking

## Booking Event Unavailable
This template is used to notify the user when an event the user is trying to book is unavailable.

This scenario would happen if the number of seats for an event were to drop to zero during the time between the user booking the event and confirming the booking.

The following values are embedded in the template:
- username
    - name provided by user when signing up with Keycloak/OAuth2
- eventLink
    - secure link to the event
- bookingLink
    - secure link to the booking
