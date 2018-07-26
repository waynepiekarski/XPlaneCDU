#!/bin/bash

HOSTNAME=$1
if [ "$HOSTNAME" == "" ]; then
  echo "Specify the hostname to transmit the requests to"
  exit 1
fi

# FMC datarefs from the SSG 747
REFS="SSG/UFMCH SSG/UFMC/PID_P SSG/UFMC/PID_I SSG/UFMC/PID_D SSG/UFMC/PRESENT SSG/UFMC/VNAV SSG/UFMC/LK1 SSG/UFMC/LK2 SSG/UFMC/LK3 SSG/UFMC/LK4 SSG/UFMC/LK5 SSG/UFMC/LK6 SSG/UFMC/RK1 SSG/UFMC/RK2 SSG/UFMC/RK3 SSG/UFMC/RK4 SSG/UFMC/RK5 SSG/UFMC/RK6 SSG/UFMC/1 SSG/UFMC/2 SSG/UFMC/3 SSG/UFMC/4 SSG/UFMC/5 SSG/UFMC/6 SSG/UFMC/7 SSG/UFMC/8 SSG/UFMC/9 SSG/UFMC/0 SSG/UFMC/A SSG/UFMC/B SSG/UFMC/C SSG/UFMC/D SSG/UFMC/E SSG/UFMC/F SSG/UFMC/G SSG/UFMC/H SSG/UFMC/I SSG/UFMC/J SSG/UFMC/K SSG/UFMC/L SSG/UFMC/M SSG/UFMC/N SSG/UFMC/O SSG/UFMC/P SSG/UFMC/Q SSG/UFMC/R SSG/UFMC/S SSG/UFMC/T SSG/UFMC/U SSG/UFMC/V SSG/UFMC/W SSG/UFMC/X SSG/UFMC/Y SSG/UFMC/Z SSG/UFMC/barra SSG/UFMC/punto SSG/UFMC/espacio SSG/UFMC/menos SSG/UFMC/INITREF SSG/UFMC/RTE SSG/UFMC/DEPARR SSG/UFMC/ATC SSG/UFMC/FIX SSG/UFMC/LEGS SSG/UFMC/HOLD SSG/UFMC/FMCCOM SSG/UFMC/PROG SSG/UFMC/EXEC SSG/UFMC/MENU SSG/UFMC/NAVRAD SSG/UFMC/PREVPAGE SSG/UFMC/NEXTPAGE SSG/UFMC/CLR SSG/UFMC/CRZ_x737 SSG/UFMC/CLB_x737 SSG/UFMC/DEL SSG/UFMC/Upper_Right_Screw SSG/UFMC/Upper_Left_Screw SSG/UFMC/Exec_Light_on SSG/UFMC/Offset_on SSG/UFMC/FMC_SPEED_ON SSG/UFMC/FMC_SPEED SSG/UFMC/FMC_Speed SSG/UFMC/Fuel_Zero SSG/UFMC/Fuel_w_step SSG/UFMC/Fuel_at_destination SSG/UFMC/Dist_to_TD SSG/UFMC/Dist_to_TC SSG/UFMC/Acceleration_Altitude SSG/UFMC/Thrust_Reduction_Altitude SSG/UFMC/Waypoint/Lon SSG/UFMC/Waypoint/Lat SSG/UFMC/Waypoint/Altitude SSG/UFMC/Waypoint/Speed SSG/UFMC/Waypoint/Type_Altitude SSG/UFMC/Waypoint/Eta SSG/UFMC/Waypoint/SidStarApp SSG/UFMC/Waypoint/Fly_Over SSG/UFMC/Waypoint/Only_Draw SSG/UFMC/Waypoint/Index SSG/UFMC/Waypoint/Number_of_Waypoints SSG/UFMC/Waypoint/Toctod SSG/UFMC/Vertical_Deviation SSG/UFMC/Flight_Phase SSG/UFMC/Lateral_Deviation SSG/UFMC/TOGA_Button  SSG/UFMC/AP_discon_Button SSG/UFMC/AP_N1_Button SSG/UFMC/AP_ARM_AT_Switch SSG/UFMC/AP_SPD_Button SSG/UFMC/AP_SPD_Intervention_Button SSG/UFMC/AP_VNAV_Button SSG/UFMC/AP_LVLCHG_Button SSG/UFMC/AP_HDG_Button SSG/UFMC/AP_LNAV_Button SSG/UFMC/AP_VORLOC_Button SSG/UFMC/AP_APP_Button SSG/UFMC/AP_ALTHOLD_Button SSG/UFMC/AP_VS_Button SSG/UFMC/AP_Altitude_Intervention_Button SSG/UFMC/AP_CMDA_Button SSG/UFMC/AP_CMDB_Button SSG/UFMC/AP_CMDC_Button SSG/UFMC/AP_HDGHOLD_Button SSG/UFMC/LINE_1 SSG/UFMC/LINE_2 SSG/UFMC/LINE_3 SSG/UFMC/LINE_4 SSG/UFMC/LINE_5 SSG/UFMC/LINE_6 SSG/UFMC/LINE_7 SSG/UFMC/LINE_8 SSG/UFMC/LINE_9 SSG/UFMC/LINE_10 SSG/UFMC/LINE_11 SSG/UFMC/LINE_12 SSG/UFMC/LINE_13 SSG/UFMC/LINE_14 sim/aircraft/view/acf_tailnum"

# Protocol for X-Plane ExtPanel plugin from https://github.com/vranki/ExtPlane
for each in $REFS; do
  echo "sub $each"
done | nc $HOSTNAME 51000 | awk -F' ' '{ cmd="echo \042"$3"\042 | base64 --decode 2> /dev/null"; cmd | getline v; close(cmd); printf("[%-40s] [%-35s] --> [%s]\n", $2, $3, v) }'
