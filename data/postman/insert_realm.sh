#!/bin/sh

# $@ should have the filename
filename="$@"

sed "s/\${KC_REALM}/${KC_REALM}/g" < "${filename}.template" > "${filename}"
