#!/bin/bash

HOSTNAME=$1
if [ "$HOSTNAME" == "" ]; then
  echo "Specify the hostname to transmit the requests to"
  exit 1
fi

# Datarefs exported by the Laminar FMS in X-Plane 11.35
# https://developer.x-plane.com/article/datarefs-for-the-cdu-screen/
# U+00B0 (degree sign): (0xC2 0xB0)
# U+2610 (ballot box): (0xE2 0x98 0x90)
# U+2190 (left arrow): (0xE2 0x86 0x90) to U+2193 (downwards arrow): (0xE2 0x86 0x93)
# U+0394 (greek capital letter delta): (0xCE 0x94)
# U+2B21 (white hexagon): (0xE2 0xAC 0xA1)
REFS=""
for NUM in 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  REFS="$REFS sim/cockpit2/radios/indicators/fms_cdu1_text_line$NUM"
  REFS="$REFS sim/cockpit2/radios/indicators/fms_cdu2_text_line$NUM"
  REFS="$REFS sim/cockpit2/radios/indicators/fms_cdu1_style_line$NUM"
  REFS="$REFS sim/cockpit2/radios/indicators/fms_cdu2_style_line$NUM"
done

# Protocol for X-Plane ExtPanel plugin from https://github.com/vranki/ExtPlane
for each in $REFS; do
  echo "sub $each"
done | nc $HOSTNAME 51000 | awk -F' ' '{ cmd="echo \042"$3"\042 | base64 --decode 2> /dev/null"; cmd | getline v; close(cmd); printf("[%-40s] [%-35s] --> [%s]\n", $2, $3, v) }'
