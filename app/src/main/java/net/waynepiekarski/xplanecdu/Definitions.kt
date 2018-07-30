// ---------------------------------------------------------------------
//
// XPlaneCDU
//
// Copyright (C) 2018 Wayne Piekarski
// wayne@tinmith.net http://tinmith.net/wayne
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// ---------------------------------------------------------------------

package net.waynepiekarski.xplanecdu

import android.app.Activity
import android.graphics.Bitmap
import android.util.Log
import android.widget.TextView

object Definitions {

    class CDUButton(val description: String, val x1: Int = -1, val y1: Int = -1, val x2: Int = -1, val y2: Int = -1, val label: String, val light: Boolean = false, val dataref: Boolean = false, val drawLabel: Boolean = false) {
        var brightBitmap: Bitmap? = null // Used to cache a copy of the area illuminated
        var illuminate: Boolean = false
    }

    // Purge the cached views because they are different if the app is re-created after the back
    // button is pressed. The layout is re-inflated but the previous cache is still around.
    fun nullOnCreateCDULines(lines: Map<String, Definitions.CDULine>) {
        for (entry in lines) {
            entry.value.cachedView = null
        }
    }

    class CDULine(val description: String, private val viewId: Int, val small: Boolean = false, val inverse: Boolean = false, val green: Boolean = false, val magenta: Boolean = false, val label: Boolean = false) {

        var cachedView: TextView? = null

        fun getTextView(activity: Activity): TextView {
            if (cachedView == null)
                cachedView = activity.findViewById(viewId)
            return cachedView!!
        }
    }

    // The pixel coordinates of the CDU image where the text display should be stretched in to
    val displayYTop    = 64.0f
    val displayXLeft   = 108.0f
    val displayYBottom = 455.0f
    val displayXRight  = 575.0f

    // Ratios of label and small size relative to the large default size
    val displayLabelRatio = 0.76f
    val displaySmallRatio = 0.76f
    val displayLargeRatio = 1.00f

    // Need to specify 24 because the CDU needs to work before we know which aircraft it is
    var numColumns = 24

    // Constants for each aircraft configuration
    enum class Aircraft {
        ZIBO, SSG
    }

    fun setAircraft(type: Aircraft) {
        assert(Definitions.CDULinesZibo737.count() == Definitions.CDULinesSSG747.count())
        assert(Definitions.CDUButtonsZibo737.count() == Definitions.CDUButtonsSSG747.count())

        if (type == Aircraft.ZIBO) {
            Log.d(Const.TAG, "Changing to Zibo aircraft definition")
            buttons = CDUButtonsZibo737
            lines = CDULinesZibo737
            numColumns = 24
        } else if (type == Aircraft.SSG) {
            Log.d(Const.TAG, "Changing to SSG aircraft definition")
            buttons = CDUButtonsSSG747
            lines = CDULinesSSG747
            numColumns = 26
        }
    }

    val CDUButtonsZibo737 = mapOf(

            "internal_help_1" to CDUButton("internal_help",   0,   0,  46,   86, "HELP1"),
            "internal_help_2" to CDUButton("internal_help", 633,   0, 682,   86, "HELP2"),
            "internal_help_3" to CDUButton("internal_help",   0, 498,  47,  730, "HELP3"),
            "internal_help_4" to CDUButton("internal_help", 631, 464, 682,  729, "HELP4"),
            "internal_help_5" to CDUButton("internal_help",   0, 948,  52, 1074, "HELP5"),
            "internal_help_6" to CDUButton("internal_help", 630, 947, 683, 1073, "HELP6"),

            "internal_hostname" to CDUButton("internal_hostname", displayXLeft.toInt(), displayYTop.toInt(), displayXRight.toInt(), displayYBottom.toInt(), "AUTO/MANUAL CONNECT"),

            "laminar/B738/button/fmc1_1L" to CDUButton("FMC captain 1LSK", 6, 116, 52, 151, "1L"),
            "laminar/B738/button/fmc1_2L" to CDUButton("FMC captain 2LSK", 6, 170, 52, 205, "2L"),
            "laminar/B738/button/fmc1_3L" to CDUButton("FMC captain 3LSK", 6, 225, 52, 258, "3L"),
            "laminar/B738/button/fmc1_4L" to CDUButton("FMC captain 4LSK", 6, 278, 52, 313, "4L"),
            "laminar/B738/button/fmc1_5L" to CDUButton("FMC captain 5LSK", 6, 333, 52, 367, "5L"),
            "laminar/B738/button/fmc1_6L" to CDUButton("FMC captain 6LSK", 6, 387, 52, 421, "6L"),
            "laminar/B738/button/fmc1_1R" to CDUButton("FMC captain 1RSK", 630, 116, 676, 151, "1R"),
            "laminar/B738/button/fmc1_2R" to CDUButton("FMC captain 2RSK", 630, 170, 676, 205, "2R"),
            "laminar/B738/button/fmc1_3R" to CDUButton("FMC captain 3RSK", 630, 225, 676, 258, "3R"),
            "laminar/B738/button/fmc1_4R" to CDUButton("FMC captain 4RSK", 630, 278, 676, 313, "4R"),
            "laminar/B738/button/fmc1_5R" to CDUButton("FMC captain 5RSK", 630, 333, 676, 367, "5R"),
            "laminar/B738/button/fmc1_6R" to CDUButton("FMC captain 6RSK", 630, 387, 676, 421, "6R"),

            "laminar/B738/button/fmc1_A" to CDUButton("FMC captain A", 288, 666, 338, 717, "A"), // Text row 1
            "laminar/B738/button/fmc1_B" to CDUButton("FMC captain B", 358, 666, 407, 717, "B"),
            "laminar/B738/button/fmc1_C" to CDUButton("FMC captain C", 426, 666, 475, 717, "C"),
            "laminar/B738/button/fmc1_D" to CDUButton("FMC captain D", 494, 666, 542, 717, "D"),
            "laminar/B738/button/fmc1_E" to CDUButton("FMC captain E", 564, 666, 611, 717, "E"),
            "laminar/B738/button/fmc1_F" to CDUButton("FMC captain F", 288, 733, 338, 784, "F"), // Text row 2
            "laminar/B738/button/fmc1_G" to CDUButton("FMC captain G", 358, 733, 407, 784, "G"),
            "laminar/B738/button/fmc1_H" to CDUButton("FMC captain H", 426, 733, 475, 784, "H"),
            "laminar/B738/button/fmc1_I" to CDUButton("FMC captain I", 494, 733, 542, 784, "I"),
            "laminar/B738/button/fmc1_J" to CDUButton("FMC captain J", 564, 733, 611, 784, "J"),
            "laminar/B738/button/fmc1_K" to CDUButton("FMC captain K", 288, 801, 338, 850, "K"), // Text row 3
            "laminar/B738/button/fmc1_L" to CDUButton("FMC captain L", 358, 801, 407, 850, "L"),
            "laminar/B738/button/fmc1_M" to CDUButton("FMC captain M", 426, 801, 475, 850, "M"),
            "laminar/B738/button/fmc1_N" to CDUButton("FMC captain N", 494, 801, 542, 850, "N"),
            "laminar/B738/button/fmc1_O" to CDUButton("FMC captain O", 564, 801, 611, 850, "O"),
            "laminar/B738/button/fmc1_P" to CDUButton("FMC captain P", 288, 868, 338, 920, "P"), // Text row 4
            "laminar/B738/button/fmc1_Q" to CDUButton("FMC captain Q", 358, 868, 407, 920, "Q"),
            "laminar/B738/button/fmc1_R" to CDUButton("FMC captain R", 426, 868, 475, 920, "R"),
            "laminar/B738/button/fmc1_S" to CDUButton("FMC captain S", 494, 868, 542, 920, "S"),
            "laminar/B738/button/fmc1_T" to CDUButton("FMC captain T", 564, 868, 611, 920, "T"),
            "laminar/B738/button/fmc1_U" to CDUButton("FMC captain U", 288, 937, 338, 987, "U"), // Text row 5
            "laminar/B738/button/fmc1_V" to CDUButton("FMC captain V", 358, 937, 407, 987, "V"),
            "laminar/B738/button/fmc1_W" to CDUButton("FMC captain W", 426, 937, 475, 987, "W"),
            "laminar/B738/button/fmc1_X" to CDUButton("FMC captain X", 494, 937, 542, 987, "X"),
            "laminar/B738/button/fmc1_Y" to CDUButton("FMC captain Y", 564, 937, 611, 987, "Y"),
            "laminar/B738/button/fmc1_Z"     to CDUButton("FMC captain Z",     288, 1006, 338, 1055, "Z"), // Text row 6
            "laminar/B738/button/fmc1_SP"    to CDUButton("FMC captain SPACE", 358, 1006, 407, 1055, " "),
            "laminar/B738/button/fmc1_del"   to CDUButton("FMC captain DEL",   426, 1006, 475, 1055, "DEL"),
            "laminar/B738/button/fmc1_slash" to CDUButton("FMC captain slash", 494, 1006, 542, 1055, "/"),
            "laminar/B738/button/fmc1_clr"   to CDUButton("FMC captain CLR",   564, 1006, 611, 1055, "CLR"),

            "laminar/B738/button/fmc1_init_ref" to CDUButton("FMC captain INIT REF",  76, 529, 148, 582, "INIT REF", drawLabel=true), // Top row
            "laminar/B738/button/fmc1_rte"      to CDUButton("FMC captain RTE",      164, 529, 235, 582, "RTE", drawLabel=true),
            "laminar/B738/button/fmc1_clb"      to CDUButton("FMC captain CLB",      251, 529, 323, 582, "CLB", drawLabel=true),
            "laminar/B738/button/fmc1_crz"      to CDUButton("FMC captain CRZ",      338, 529, 410, 582, "CRZ", drawLabel=true),
            "laminar/B738/button/fmc1_des"      to CDUButton("FMC captain DES",      425, 529, 497, 582, "DES", drawLabel=true),

            "laminar/B738/button/fmc1_menu"    to CDUButton("FMC captain MENU",     76, 597, 148, 649, "MENU", drawLabel=true), // Second row
            "laminar/B738/button/fmc1_legs"    to CDUButton("FMC captain LEGS",    164, 597, 235, 649, "LEGS", drawLabel=true),
            "laminar/B738/button/fmc1_dep_app" to CDUButton("FMC captain DEP/ARR", 251, 597, 323, 649, "DEP ARR", drawLabel=true),
            "laminar/B738/button/fmc1_hold"    to CDUButton("FMC captain HOLD",    338, 597, 410, 649, "HOLD", drawLabel=true),
            "laminar/B738/button/fmc1_prog"    to CDUButton("FMC captain PROG",    425, 597, 497, 649, "PROG", drawLabel=true),
            "laminar/B738/button/fmc1_exec"    to CDUButton("FMC captain EXEC",    540, 608, 609, 649, "EXEC", drawLabel=true),

            "laminar/B738/indicators/fmc_exec_lights" to CDUButton("EXEC light", 551, 590, 597, 596, "EXEC", light=true),
            "laminar/B738/fmc/fmc_message"            to CDUButton("MSG light",  632, 757, 651, 827, "MSG",  light=true),
            "internal_ofst_light"              to CDUButton("OFST light",        632, 828, 651, 922, "OFST"),
            "internal_dspyfail_light"          to CDUButton("DSPY FAIL light",    30, 757,  48, 921, "DSPYFAIL"),

            "laminar/B738/button/fmc1_n1_lim"    to CDUButton("FMC captain N1 LIMIT",  76, 665, 148, 715,  "N1 LIMIT", drawLabel=true),
            "laminar/B738/button/fmc1_fix"       to CDUButton("FMC captain FIX",       164, 665, 235, 715, "FIX", drawLabel=true),
            "laminar/B738/button/fmc1_prev_page" to CDUButton("FMC captain PREV PAGE", 76, 729, 148, 782,  "PREV PAGE", drawLabel=true),
            "laminar/B738/button/fmc1_next_page" to CDUButton("FMC captain NEXT PAGE", 164, 729, 235, 782, "NEXT PAGE", drawLabel=true),

            "laminar/B738/button/fmc1_1"      to CDUButton("FMC captain 1",       68, 798, 121, 852, "1"), // Numeric keypad
            "laminar/B738/button/fmc1_2"      to CDUButton("FMC captain 2",      138, 798, 189, 852, "2"),
            "laminar/B738/button/fmc1_3"      to CDUButton("FMC captain 3",      208, 798, 259, 852, "3"),
            "laminar/B738/button/fmc1_4"      to CDUButton("FMC captain 4",       68, 869, 121, 920, "4"),
            "laminar/B738/button/fmc1_5"      to CDUButton("FMC captain 5",      138, 869, 189, 920, "5"),
            "laminar/B738/button/fmc1_6"      to CDUButton("FMC captain 6",      208, 869, 259, 920, "6"),
            "laminar/B738/button/fmc1_7"      to CDUButton("FMC captain 7",       68, 938, 121, 988, "7"),
            "laminar/B738/button/fmc1_8"      to CDUButton("FMC captain 8",      138, 938, 189, 988, "8"),
            "laminar/B738/button/fmc1_9"      to CDUButton("FMC captain 9",      208, 938, 259, 988, "9"),
            "laminar/B738/button/fmc1_period" to CDUButton("FMC captain period",  68, 1006, 121, 1057, "."),
            "laminar/B738/button/fmc1_0"      to CDUButton("FMC captain 0",      138, 1006, 189, 1057, "0"),
            "laminar/B738/button/fmc1_minus"  to CDUButton("FMC captain minus",  208, 1006, 259, 1057, "-")
    )

    val CDUButtonsSSG747 = mapOf(

            "internal_help_1" to CDUButton("internal_help",   0,   0,  46,   86, "HELP1"),
            "internal_help_2" to CDUButton("internal_help", 633,   0, 682,   86, "HELP2"),
            "internal_help_3" to CDUButton("internal_help",   0, 498,  47,  730, "HELP3"),
            "internal_help_4" to CDUButton("internal_help", 631, 464, 682,  729, "HELP4"),
            "internal_help_5" to CDUButton("internal_help",   0, 948,  52, 1074, "HELP5"),
            "internal_help_6" to CDUButton("internal_help", 630, 947, 683, 1073, "HELP6"),

            "internal_hostname" to CDUButton("internal_hostname", displayXLeft.toInt(), displayYTop.toInt(), displayXRight.toInt(), displayYBottom.toInt(), "AUTO/MANUAL CONNECT"),

            "SSG/UFMC/LK1" to CDUButton("FMC captain 1LSK", 6, 116, 52, 151, "1L", dataref=true),
            "SSG/UFMC/LK2" to CDUButton("FMC captain 2LSK", 6, 170, 52, 205, "2L", dataref=true),
            "SSG/UFMC/LK3" to CDUButton("FMC captain 3LSK", 6, 225, 52, 258, "3L", dataref=true),
            "SSG/UFMC/LK4" to CDUButton("FMC captain 4LSK", 6, 278, 52, 313, "4L", dataref=true),
            "SSG/UFMC/LK5" to CDUButton("FMC captain 5LSK", 6, 333, 52, 367, "5L", dataref=true),
            "SSG/UFMC/LK6" to CDUButton("FMC captain 6LSK", 6, 387, 52, 421, "6L", dataref=true),
            "SSG/UFMC/RK1" to CDUButton("FMC captain 1RSK", 630, 116, 676, 151, "1R", dataref=true),
            "SSG/UFMC/RK2" to CDUButton("FMC captain 2RSK", 630, 170, 676, 205, "2R", dataref=true),
            "SSG/UFMC/RK3" to CDUButton("FMC captain 3RSK", 630, 225, 676, 258, "3R", dataref=true),
            "SSG/UFMC/RK4" to CDUButton("FMC captain 4RSK", 630, 278, 676, 313, "4R", dataref=true),
            "SSG/UFMC/RK5" to CDUButton("FMC captain 5RSK", 630, 333, 676, 367, "5R", dataref=true),
            "SSG/UFMC/RK6" to CDUButton("FMC captain 6RSK", 630, 387, 676, 421, "6R", dataref=true),

            "SSG/UFMC/A" to CDUButton("FMC captain A", 288, 666, 338, 717, "A", dataref=true), // Text row 1
            "SSG/UFMC/B" to CDUButton("FMC captain B", 358, 666, 407, 717, "B", dataref=true),
            "SSG/UFMC/C" to CDUButton("FMC captain C", 426, 666, 475, 717, "C", dataref=true),
            "SSG/UFMC/D" to CDUButton("FMC captain D", 494, 666, 542, 717, "D", dataref=true),
            "SSG/UFMC/E" to CDUButton("FMC captain E", 564, 666, 611, 717, "E", dataref=true),
            "SSG/UFMC/F" to CDUButton("FMC captain F", 288, 733, 338, 784, "F", dataref=true), // Text row 2
            "SSG/UFMC/G" to CDUButton("FMC captain G", 358, 733, 407, 784, "G", dataref=true),
            "SSG/UFMC/H" to CDUButton("FMC captain H", 426, 733, 475, 784, "H", dataref=true),
            "SSG/UFMC/I" to CDUButton("FMC captain I", 494, 733, 542, 784, "I", dataref=true),
            "SSG/UFMC/J" to CDUButton("FMC captain J", 564, 733, 611, 784, "J", dataref=true),
            "SSG/UFMC/K" to CDUButton("FMC captain K", 288, 801, 338, 850, "K", dataref=true), // Text row 3
            "SSG/UFMC/L" to CDUButton("FMC captain L", 358, 801, 407, 850, "L", dataref=true),
            "SSG/UFMC/M" to CDUButton("FMC captain M", 426, 801, 475, 850, "M", dataref=true),
            "SSG/UFMC/N" to CDUButton("FMC captain N", 494, 801, 542, 850, "N", dataref=true),
            "SSG/UFMC/O" to CDUButton("FMC captain O", 564, 801, 611, 850, "O", dataref=true),
            "SSG/UFMC/P" to CDUButton("FMC captain P", 288, 868, 338, 920, "P", dataref=true), // Text row 4
            "SSG/UFMC/Q" to CDUButton("FMC captain Q", 358, 868, 407, 920, "Q", dataref=true),
            "SSG/UFMC/R" to CDUButton("FMC captain R", 426, 868, 475, 920, "R", dataref=true),
            "SSG/UFMC/S" to CDUButton("FMC captain S", 494, 868, 542, 920, "S", dataref=true),
            "SSG/UFMC/T" to CDUButton("FMC captain T", 564, 868, 611, 920, "T", dataref=true),
            "SSG/UFMC/U" to CDUButton("FMC captain U", 288, 937, 338, 987, "U", dataref=true), // Text row 5
            "SSG/UFMC/V" to CDUButton("FMC captain V", 358, 937, 407, 987, "V", dataref=true),
            "SSG/UFMC/W" to CDUButton("FMC captain W", 426, 937, 475, 987, "W", dataref=true),
            "SSG/UFMC/X" to CDUButton("FMC captain X", 494, 937, 542, 987, "X", dataref=true),
            "SSG/UFMC/Y" to CDUButton("FMC captain Y", 564, 937, 611, 987, "Y", dataref=true),
            "SSG/UFMC/Z" to CDUButton("FMC captain Z",     288, 1006, 338, 1055, "Z", dataref=true), // Text row 6
            "SSG/UFMC/espacio" to CDUButton("FMC captain SPACE", 358, 1006, 407, 1055, " ",   dataref=true),
            "SSG/UFMC/DEL"     to CDUButton("FMC captain DEL",   426, 1006, 475, 1055, "DEL", dataref=true),
            "SSG/UFMC/barra"   to CDUButton("FMC captain slash", 494, 1006, 542, 1055, "/",   dataref=true),
            "SSG/UFMC/CLR"     to CDUButton("FMC captain CLR",   564, 1006, 611, 1055, "CLR", dataref=true),

            "SSG/UFMC/INITREF" to CDUButton("FMC captain INIT REF",  76, 529, 148, 582, "INIT REF", dataref=true, drawLabel=true), // Top row
            "SSG/UFMC/RTE"     to CDUButton("FMC captain RTE",      164, 529, 235, 582, "RTE",      dataref=true, drawLabel=true),
            "SSG/UFMC/DEPARR"  to CDUButton("FMC captain DEP ARR",  251, 529, 323, 582, "DEP ARR",  dataref=true, drawLabel=true),
            "SSG/UFMC/ATC"     to CDUButton("FMC captain ATC",      338, 529, 410, 582, "ATC",      dataref=true, drawLabel=true),
            "SSG/UFMC/VNAV"    to CDUButton("FMC captain VNAV",     425, 529, 497, 582, "VNAV",     dataref=true, drawLabel=true),

            "SSG/UFMC/FIX"     to CDUButton("FMC captain FIX",       76, 597, 148, 649, "FIX",  dataref=true, drawLabel=true), // Second row
            "SSG/UFMC/LEGS"    to CDUButton("FMC captain LEGS",     164, 597, 235, 649, "LEGS", dataref=true, drawLabel=true),
            "SSG/UFMC/HOLD"    to CDUButton("FMC captain HOLD",     251, 597, 323, 649, "HOLD", dataref=true, drawLabel=true),
            "SSG/UFMC/FMCCOM"  to CDUButton("FMC captain FMC COM",  338, 597, 410, 649, "FMC COMM", dataref=true, drawLabel=true),
            "SSG/UFMC/PROG"    to CDUButton("FMC captain PROG",     425, 597, 497, 649, "PROG", dataref=true, drawLabel=true),
            "SSG/UFMC/EXEC"    to CDUButton("FMC captain EXEC",     540, 608, 609, 649, "EXEC", dataref=true, drawLabel=true),

            "SSG/UFMC/Exec_Light_on" to CDUButton("EXEC light", 551, 590, 597, 596, "EXEC", light=true),

            "SSG/UFMC/MENU"      to CDUButton("FMC captain MENU",       76, 665, 148, 715, "MENU",      dataref=true, drawLabel=true),
            "SSG/UFMC/NAVRAD"    to CDUButton("FMC captain NAV RAD",   164, 665, 235, 715, "NAV RAD",   dataref=true, drawLabel=true),
            "SSG/UFMC/PREVPAGE"  to CDUButton("FMC captain PREV PAGE",  76, 729, 148, 782, "PREV PAGE", dataref=true, drawLabel=true),
            "SSG/UFMC/NEXTPAGE"  to CDUButton("FMC captain NEXT PAGE", 164, 729, 235, 782, "NEXT PAGE", dataref=true, drawLabel=true),

            "SSG/UFMC/1"      to CDUButton("FMC captain 1",       68,  798, 121,  852, "1", dataref=true), // Numeric keypad
            "SSG/UFMC/2"      to CDUButton("FMC captain 2",      138,  798, 189,  852, "2", dataref=true),
            "SSG/UFMC/3"      to CDUButton("FMC captain 3",      208,  798, 259,  852, "3", dataref=true),
            "SSG/UFMC/4"      to CDUButton("FMC captain 4",       68,  869, 121,  920, "4", dataref=true),
            "SSG/UFMC/5"      to CDUButton("FMC captain 5",      138,  869, 189,  920, "5", dataref=true),
            "SSG/UFMC/6"      to CDUButton("FMC captain 6",      208,  869, 259,  920, "6", dataref=true),
            "SSG/UFMC/7"      to CDUButton("FMC captain 7",       68,  938, 121,  988, "7", dataref=true),
            "SSG/UFMC/8"      to CDUButton("FMC captain 8",      138,  938, 189,  988, "8", dataref=true),
            "SSG/UFMC/9"      to CDUButton("FMC captain 9",      208,  938, 259,  988, "9", dataref=true),
            "SSG/UFMC/punto"  to CDUButton("FMC captain period",  68, 1006, 121, 1057, ".", dataref=true),
            "SSG/UFMC/0"      to CDUButton("FMC captain 0",      138, 1006, 189, 1057, "0", dataref=true),
            "SSG/UFMC/menos"  to CDUButton("FMC captain minus",  208, 1006, 259, 1057, "-", dataref=true)
    )

    val CDULinesZibo737 = mapOf(
            "laminar/B738/fmc1/Line00_L" to CDULine("PAGE LABEL LARGE FONT", R.id.terminalTextLarge0),
            "laminar/B738/fmc1/Line00_S" to CDULine("PAGE LABEL SMALL FONT", R.id.terminalTextSmall0, small=true),
            "laminar/B738/fmc1/Line00_G" to CDULine("PAGE LABEL GREEN FONT", R.id.terminalTextGreen0, green=true),
            "laminar/B738/fmc1/Line00_M" to CDULine("PAGE LABEL MAGENTA FONT", R.id.terminalTextMagenta0, magenta=true),
            "laminar/B738/fmc1/Line01_X" to CDULine("LINE 1 LABEL SMALL FONT", R.id.terminalLabel1, label=true),
            "laminar/B738/fmc1/Line02_X" to CDULine("LINE 2 LABEL SMALL FONT", R.id.terminalLabel2, label=true),
            "laminar/B738/fmc1/Line03_X" to CDULine("LINE 3 LABEL SMALL FONT", R.id.terminalLabel3, label=true),
            "laminar/B738/fmc1/Line04_X" to CDULine("LINE 4 LABEL SMALL FONT", R.id.terminalLabel4, label=true),
            "laminar/B738/fmc1/Line05_X" to CDULine("LINE 5 LABEL SMALL FONT", R.id.terminalLabel5, label=true),
            "laminar/B738/fmc1/Line06_X" to CDULine("LINE 6 LABEL SMALL FONT", R.id.terminalLabel6, label=true),
            "laminar/B738/fmc1/Line01_L" to CDULine("LINE 1 LARGE FONT", R.id.terminalTextLarge1),
            "laminar/B738/fmc1/Line02_L" to CDULine("LINE 2 LARGE FONT", R.id.terminalTextLarge2),
            "laminar/B738/fmc1/Line03_L" to CDULine("LINE 3 LARGE FONT", R.id.terminalTextLarge3),
            "laminar/B738/fmc1/Line04_L" to CDULine("LINE 4 LARGE FONT", R.id.terminalTextLarge4),
            "laminar/B738/fmc1/Line05_L" to CDULine("LINE 5 LARGE FONT", R.id.terminalTextLarge5),
            "laminar/B738/fmc1/Line06_L" to CDULine("LINE 6 LARGE FONT", R.id.terminalTextLarge6),
            "laminar/B738/fmc1/Line01_G" to CDULine("LINE 1 GREEN FONT", R.id.terminalTextGreen1, green=true),
            "laminar/B738/fmc1/Line02_G" to CDULine("LINE 2 GREEN FONT", R.id.terminalTextGreen2, green=true),
            "laminar/B738/fmc1/Line03_G" to CDULine("LINE 3 GREEN FONT", R.id.terminalTextGreen3, green=true),
            "laminar/B738/fmc1/Line04_G" to CDULine("LINE 4 GREEN FONT", R.id.terminalTextGreen4, green=true),
            "laminar/B738/fmc1/Line05_G" to CDULine("LINE 5 GREEN FONT", R.id.terminalTextGreen5, green=true),
            "laminar/B738/fmc1/Line06_G" to CDULine("LINE 6 GREEN FONT", R.id.terminalTextGreen6, green=true),
            "laminar/B738/fmc1/Line01_M" to CDULine("LINE 1 MAGENTA FONT", R.id.terminalTextMagenta1, magenta=true),
            "laminar/B738/fmc1/Line02_M" to CDULine("LINE 2 MAGENTA FONT", R.id.terminalTextMagenta2, magenta=true),
            "laminar/B738/fmc1/Line03_M" to CDULine("LINE 3 MAGENTA FONT", R.id.terminalTextMagenta3, magenta=true),
            "laminar/B738/fmc1/Line04_M" to CDULine("LINE 4 MAGENTA FONT", R.id.terminalTextMagenta4, magenta=true),
            "laminar/B738/fmc1/Line05_M" to CDULine("LINE 5 MAGENTA FONT", R.id.terminalTextMagenta5, magenta=true),
            "laminar/B738/fmc1/Line06_M" to CDULine("LINE 6 MAGENTA FONT", R.id.terminalTextMagenta6, magenta=true),
            "laminar/B738/fmc1/Line01_I" to CDULine("LINE 1 LARGE FONT INVERSE", R.id.terminalTextLargeInv1, inverse=true),
            "laminar/B738/fmc1/Line02_I" to CDULine("LINE 2 LARGE FONT INVERSE", R.id.terminalTextLargeInv2, inverse=true),
            "laminar/B738/fmc1/Line03_I" to CDULine("LINE 3 LARGE FONT INVERSE", R.id.terminalTextLargeInv3, inverse=true),
            "laminar/B738/fmc1/Line04_I" to CDULine("LINE 4 LARGE FONT INVERSE", R.id.terminalTextLargeInv4, inverse=true),
            "laminar/B738/fmc1/Line05_I" to CDULine("LINE 5 LARGE FONT INVERSE", R.id.terminalTextLargeInv5, inverse=true),
            "laminar/B738/fmc1/Line06_I" to CDULine("LINE 6 LARGE FONT INVERSE", R.id.terminalTextLargeInv6, inverse=true),
            "laminar/B738/fmc1/Line01_S" to CDULine("LINE 1 SMALL FONT", R.id.terminalTextSmall1, small=true),
            "laminar/B738/fmc1/Line02_S" to CDULine("LINE 2 SMALL FONT", R.id.terminalTextSmall2, small=true),
            "laminar/B738/fmc1/Line03_S" to CDULine("LINE 3 SMALL FONT", R.id.terminalTextSmall3, small=true),
            "laminar/B738/fmc1/Line04_S" to CDULine("LINE 4 SMALL FONT", R.id.terminalTextSmall4, small=true),
            "laminar/B738/fmc1/Line05_S" to CDULine("LINE 5 SMALL FONT", R.id.terminalTextSmall5, small=true),
            "laminar/B738/fmc1/Line06_S" to CDULine("LINE 6 SMALL FONT", R.id.terminalTextSmall6, small=true),
            "laminar/B738/fmc1/Line_entry" to CDULine("LINE ENTRY LARGE FONT", R.id.terminalTextLarge7),
            "laminar/B738/fmc1/Line_entry_I" to CDULine("LINE ENTRY LARGE FONT INVERSE", R.id.terminalTextLargeInv7, inverse=true)
    )

    val CDULinesSSG747 = mapOf(
            "SSG/UFMC/LINE_1"  to CDULine("PAGE LABEL LARGE FONT",   R.id.terminalTextLarge0),
            "SSG/UFMC/LINE_2"  to CDULine("LINE 1 LABEL SMALL FONT", R.id.terminalLabel1, label=true),
            "SSG/UFMC/LINE_3"  to CDULine("LINE 1 LARGE FONT",       R.id.terminalTextLarge1),
            "SSG/UFMC/LINE_4"  to CDULine("LINE 2 LABEL SMALL FONT", R.id.terminalLabel2, label=true),
            "SSG/UFMC/LINE_5"  to CDULine("LINE 2 LARGE FONT",       R.id.terminalTextLarge2),
            "SSG/UFMC/LINE_6"  to CDULine("LINE 3 LABEL SMALL FONT", R.id.terminalLabel3, label=true),
            "SSG/UFMC/LINE_7"  to CDULine("LINE 3 LARGE FONT",       R.id.terminalTextLarge3),
            "SSG/UFMC/LINE_8"  to CDULine("LINE 4 LABEL SMALL FONT", R.id.terminalLabel4, label=true),
            "SSG/UFMC/LINE_9"  to CDULine("LINE 4 LARGE FONT",       R.id.terminalTextLarge4),
            "SSG/UFMC/LINE_10" to CDULine("LINE 5 LABEL SMALL FONT", R.id.terminalLabel5, label=true),
            "SSG/UFMC/LINE_11" to CDULine("LINE 5 LARGE FONT",       R.id.terminalTextLarge5),
            "SSG/UFMC/LINE_12" to CDULine("LINE 6 LABEL SMALL FONT", R.id.terminalLabel6, label=true),
            "SSG/UFMC/LINE_13" to CDULine("LINE 6 LARGE FONT",       R.id.terminalTextLarge6),
            "SSG/UFMC/LINE_14" to CDULine("LINE ENTRY LARGE FONT",   R.id.terminalTextLarge7),

            // These --UNUSED-- lines need to have unique key names or they are combined together by the mapOf
            "--UNUSED--1" to CDULine("PAGE LABEL SMALL FONT", R.id.terminalTextSmall0, small=true),
            "--UNUSED--2" to CDULine("PAGE LABEL GREEN FONT", R.id.terminalTextGreen0, green=true),
            "--UNUSED--3" to CDULine("PAGE LABEL MAGENTA FONT", R.id.terminalTextMagenta0, magenta=true),
            "--UNUSED--4" to CDULine("LINE 1 GREEN FONT", R.id.terminalTextGreen1, green=true),
            "--UNUSED--5" to CDULine("LINE 2 GREEN FONT", R.id.terminalTextGreen2, green=true),
            "--UNUSED--6" to CDULine("LINE 3 GREEN FONT", R.id.terminalTextGreen3, green=true),
            "--UNUSED--7" to CDULine("LINE 4 GREEN FONT", R.id.terminalTextGreen4, green=true),
            "--UNUSED--8" to CDULine("LINE 5 GREEN FONT", R.id.terminalTextGreen5, green=true),
            "--UNUSED--9" to CDULine("LINE 6 GREEN FONT", R.id.terminalTextGreen6, green=true),
            "--UNUSED--10" to CDULine("LINE 1 MAGENTA FONT", R.id.terminalTextMagenta1, magenta=true),
            "--UNUSED--11" to CDULine("LINE 2 MAGENTA FONT", R.id.terminalTextMagenta2, magenta=true),
            "--UNUSED--12" to CDULine("LINE 3 MAGENTA FONT", R.id.terminalTextMagenta3, magenta=true),
            "--UNUSED--13" to CDULine("LINE 4 MAGENTA FONT", R.id.terminalTextMagenta4, magenta=true),
            "--UNUSED--14" to CDULine("LINE 5 MAGENTA FONT", R.id.terminalTextMagenta5, magenta=true),
            "--UNUSED--15" to CDULine("LINE 6 MAGENTA FONT", R.id.terminalTextMagenta6, magenta=true),
            "--UNUSED--16" to CDULine("LINE 1 LARGE FONT INVERSE", R.id.terminalTextLargeInv1, inverse=true),
            "--UNUSED--17" to CDULine("LINE 2 LARGE FONT INVERSE", R.id.terminalTextLargeInv2, inverse=true),
            "--UNUSED--18" to CDULine("LINE 3 LARGE FONT INVERSE", R.id.terminalTextLargeInv3, inverse=true),
            "--UNUSED--19" to CDULine("LINE 4 LARGE FONT INVERSE", R.id.terminalTextLargeInv4, inverse=true),
            "--UNUSED--20" to CDULine("LINE 5 LARGE FONT INVERSE", R.id.terminalTextLargeInv5, inverse=true),
            "--UNUSED--21" to CDULine("LINE 6 LARGE FONT INVERSE", R.id.terminalTextLargeInv6, inverse=true),
            "--UNUSED--22" to CDULine("LINE 1 SMALL FONT", R.id.terminalTextSmall1, small=true),
            "--UNUSED--23" to CDULine("LINE 2 SMALL FONT", R.id.terminalTextSmall2, small=true),
            "--UNUSED--24" to CDULine("LINE 3 SMALL FONT", R.id.terminalTextSmall3, small=true),
            "--UNUSED--25" to CDULine("LINE 4 SMALL FONT", R.id.terminalTextSmall4, small=true),
            "--UNUSED--26" to CDULine("LINE 5 SMALL FONT", R.id.terminalTextSmall5, small=true),
            "--UNUSED--27" to CDULine("LINE 6 SMALL FONT", R.id.terminalTextSmall6, small=true),
            "--UNUSED--28" to CDULine("LINE ENTRY LARGE FONT INVERSE", R.id.terminalTextLargeInv7, inverse=true)
    )

    var buttons: Map<String, Definitions.CDUButton> = CDUButtonsZibo737
    var lines: Map<String, Definitions.CDULine> = CDULinesZibo737
}
