#!/bin/bash

SRCBASE=`dirname $0`/../app/src/main/java/net/waynepiekarski/xplanecdu
XMLBASE=`dirname $0`/../app/src/main/res/layout
STRBASE=`dirname $0`/../app/src/main/res/values

set -xv
cat $SRCBASE/Definitions.kt | sed "s/fmc1/fmc2/g" | sed "s/ captain / officer /g" | sed "s/fmc_exec_lights/fmc_exec_lights_fo/g" > $SRCBASE/Definitions_fo.kt
mv $SRCBASE/Definitions_fo.kt $SRCBASE/Definitions.kt

cat $SRCBASE/MainActivity.kt | sed "s/\"XPlaneCDU\"/\"XPlaneCDU-FO\"/g" | sed "s/For Zibo738 & SSG748/Zibo First Officer/g" > $SRCBASE/MainActivity_fo.kt
mv $SRCBASE/MainActivity_fo.kt $SRCBASE/MainActivity.kt

cat $XMLBASE/activity_main.xml | sed "s/Android CDU for X-Plane 11/Zibo 738 first officer CDU/g" > $XMLBASE/activity_main_fo.xml
mv $XMLBASE/activity_main_fo.xml $XMLBASE/activity_main.xml

cat $STRBASE/strings.xml | sed "s/XPlaneCDU/FO-XPlaneCDU/g" > $STRBASE/strings_fo.xml
mv $STRBASE/strings_fo.xml $STRBASE/strings.xml
