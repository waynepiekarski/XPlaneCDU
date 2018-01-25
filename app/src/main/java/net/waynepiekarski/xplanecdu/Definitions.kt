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
import android.widget.TextView

object Definitions {

    class CDUButton(var description: String, var x1: Int = -1, var y1: Int = -1, var x2: Int = -1, var y2: Int = -1) {
    }

    class CDULine(var description: String, var viewId: Int, var small: Boolean = false, var inverse: Boolean = false, var label: Boolean = false) {

        private var view: TextView? = null

        fun getTextView(activity: Activity): TextView {
            if (view == null)
                view = activity.findViewById(viewId)
            return view!!
        }
    }

    // The pixel coordinates of the CDU image where the text display should be stretched in to
    val displayYTop    = 35.0f
    val displayXLeft   = 56.0f
    val displayYBottom = 220.0f
    val displayXRight  = 270.0f

    // Ratios of label and small size relative to the large default size
    val displayLabelRatio = 0.5f
    val displaySmallRatio = 0.80f
    val displayLargeRatio = 1.0f

    val CDUButtonsZibo737 = mapOf(
            "laminar/B738/button/fmc1_1L" to CDUButton("FMC captain 1LSK", 11, 63, 31, 76),
            "laminar/B738/button/fmc1_2L" to CDUButton("FMC captain 2LSK"),
            "laminar/B738/button/fmc1_3L" to CDUButton("FMC captain 3LSK"),
            "laminar/B738/button/fmc1_4L" to CDUButton("FMC captain 4LSK"),
            "laminar/B738/button/fmc1_5L" to CDUButton("FMC captain 5LSK"),
            "laminar/B738/button/fmc1_6L" to CDUButton("FMC captain 6LSK"),
            "laminar/B738/button/fmc1_1R" to CDUButton("FMC captain 1RSK"),
            "laminar/B738/button/fmc1_2R" to CDUButton("FMC captain 2RSK"),
            "laminar/B738/button/fmc1_3R" to CDUButton("FMC captain 3RSK"),
            "laminar/B738/button/fmc1_4R" to CDUButton("FMC captain 4RSK"),
            "laminar/B738/button/fmc1_5R" to CDUButton("FMC captain 5RSK"),
            "laminar/B738/button/fmc1_6R" to CDUButton("FMC captain 6RSK"),
            "laminar/B738/button/fmc1_0" to CDUButton("FMC captain 0"),
            "laminar/B738/button/fmc1_1" to CDUButton("FMC captain 1"),
            "laminar/B738/button/fmc1_2" to CDUButton("FMC captain 2"),
            "laminar/B738/button/fmc1_3" to CDUButton("FMC captain 3"),
            "laminar/B738/button/fmc1_4" to CDUButton("FMC captain 4"),
            "laminar/B738/button/fmc1_5" to CDUButton("FMC captain 5"),
            "laminar/B738/button/fmc1_6" to CDUButton("FMC captain 6"),
            "laminar/B738/button/fmc1_7" to CDUButton("FMC captain 7"),
            "laminar/B738/button/fmc1_8" to CDUButton("FMC captain 8"),
            "laminar/B738/button/fmc1_9" to CDUButton("FMC captain 9"),
            "laminar/B738/button/fmc1_period" to CDUButton("FMC captain period"),
            "laminar/B738/button/fmc1_minus" to CDUButton("FMC captain minus"),
            "laminar/B738/button/fmc1_slash" to CDUButton("FMC captain slash"),
            "laminar/B738/button/fmc1_A" to CDUButton("FMC captain A", 136, 317, 158, 337),
            "laminar/B738/button/fmc1_B" to CDUButton("FMC captain B"),
            "laminar/B738/button/fmc1_C" to CDUButton("FMC captain C"),
            "laminar/B738/button/fmc1_D" to CDUButton("FMC captain D"),
            "laminar/B738/button/fmc1_E" to CDUButton("FMC captain E"),
            "laminar/B738/button/fmc1_F" to CDUButton("FMC captain F"),
            "laminar/B738/button/fmc1_G" to CDUButton("FMC captain G"),
            "laminar/B738/button/fmc1_H" to CDUButton("FMC captain H"),
            "laminar/B738/button/fmc1_I" to CDUButton("FMC captain I"),
            "laminar/B738/button/fmc1_J" to CDUButton("FMC captain J"),
            "laminar/B738/button/fmc1_K" to CDUButton("FMC captain K"),
            "laminar/B738/button/fmc1_L" to CDUButton("FMC captain L"),
            "laminar/B738/button/fmc1_M" to CDUButton("FMC captain M"),
            "laminar/B738/button/fmc1_N" to CDUButton("FMC captain N"),
            "laminar/B738/button/fmc1_O" to CDUButton("FMC captain O"),
            "laminar/B738/button/fmc1_P" to CDUButton("FMC captain P"),
            "laminar/B738/button/fmc1_Q" to CDUButton("FMC captain Q"),
            "laminar/B738/button/fmc1_R" to CDUButton("FMC captain R"),
            "laminar/B738/button/fmc1_S" to CDUButton("FMC captain S"),
            "laminar/B738/button/fmc1_T" to CDUButton("FMC captain T"),
            "laminar/B738/button/fmc1_U" to CDUButton("FMC captain U"),
            "laminar/B738/button/fmc1_V" to CDUButton("FMC captain V"),
            "laminar/B738/button/fmc1_W" to CDUButton("FMC captain W"),
            "laminar/B738/button/fmc1_X" to CDUButton("FMC captain X"),
            "laminar/B738/button/fmc1_Y" to CDUButton("FMC captain Y"),
            "laminar/B738/button/fmc1_Z" to CDUButton("FMC captain Z"),
            "laminar/B738/button/fmc1_SP" to CDUButton("FMC captain SPACE"),
            "laminar/B738/button/fmc1_clr" to CDUButton("FMC captain CLR"),
            "laminar/B738/button/fmc1_del" to CDUButton("FMC captain DEL"),
            "laminar/B738/button/fmc1_prev_page" to CDUButton("FMC captain PREV PAGE"),
            "laminar/B738/button/fmc1_next_page" to CDUButton("FMC captain NEXT PAGE", 76, 351, 110, 371),
            "laminar/B738/button/fmc1_init_ref" to CDUButton("FMC captain INIT REF"),
            "laminar/B738/button/fmc1_menu" to CDUButton("FMC captain MENU"),
            "laminar/B738/button/fmc1_n1_lim" to CDUButton("FMC captain N1 LIMIT"),
            "laminar/B738/button/fmc1_rte" to CDUButton("FMC captain RTE"),
            "laminar/B738/button/fmc1_legs" to CDUButton("FMC captain LEGS"),
            "laminar/B738/button/fmc1_fix" to CDUButton("FMC captain FIX"),
            "laminar/B738/button/fmc1_clb" to CDUButton("FMC captain CLB"),
            "laminar/B738/button/fmc1_crz" to CDUButton("FMC captain CRZ"),
            "laminar/B738/button/fmc1_des" to CDUButton("FMC captain DES"),
            "laminar/B738/button/fmc1_dep_app" to CDUButton("FMC captain DEP/ARR"),
            "laminar/B738/button/fmc1_hold" to CDUButton("FMC captain HOLD"),
            "laminar/B738/button/fmc1_prog" to CDUButton("FMC captain PROG"),
            "laminar/B738/button/fmc1_exec" to CDUButton("FMC captain EXEC"))

    val CDULinesZibo737 = mapOf(
            "laminar/B738/fmc1/Line00_L" to CDULine("PAGE LABEL LARGE FONT", R.id.terminalTextLarge0),
            "laminar/B738/fmc1/Line00_S" to CDULine("PAGE LABEL SMALL FONT", R.id.terminalTextSmall0, small=true),
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
            "laminar/B738/fmc1/Line01_I" to CDULine("LINE 1 LARGE FONT INVERSE", R.id.terminalTextLarge1, inverse=true),
            "laminar/B738/fmc1/Line02_I" to CDULine("LINE 2 LARGE FONT INVERSE", R.id.terminalTextLarge2, inverse=true),
            "laminar/B738/fmc1/Line03_I" to CDULine("LINE 3 LARGE FONT INVERSE", R.id.terminalTextLarge3, inverse=true),
            "laminar/B738/fmc1/Line04_I" to CDULine("LINE 4 LARGE FONT INVERSE", R.id.terminalTextLarge4, inverse=true),
            "laminar/B738/fmc1/Line05_I" to CDULine("LINE 5 LARGE FONT INVERSE", R.id.terminalTextLarge5, inverse=true),
            "laminar/B738/fmc1/Line06_I" to CDULine("LINE 6 LARGE FONT INVERSE", R.id.terminalTextLarge6, inverse=true),
            "laminar/B738/fmc1/Line01_S" to CDULine("LINE 1 SMALL FONT", R.id.terminalTextSmall1, small=true),
            "laminar/B738/fmc1/Line02_S" to CDULine("LINE 2 SMALL FONT", R.id.terminalTextSmall2, small=true),
            "laminar/B738/fmc1/Line03_S" to CDULine("LINE 3 SMALL FONT", R.id.terminalTextSmall3, small=true),
            "laminar/B738/fmc1/Line04_S" to CDULine("LINE 4 SMALL FONT", R.id.terminalTextSmall4, small=true),
            "laminar/B738/fmc1/Line05_S" to CDULine("LINE 5 SMALL FONT", R.id.terminalTextSmall5, small=true),
            "laminar/B738/fmc1/Line06_S" to CDULine("LINE 6 SMALL FONT", R.id.terminalTextSmall6, small=true),
            "laminar/B738/fmc1/Line_entry" to CDULine("LINE ENTRY LARGE FONT", R.id.terminalTextLarge7),
            "laminar/B738/fmc1/Line_entry_I" to CDULine("LINE ENTRY LARGE FONT INVERSE", R.id.terminalTextLarge7, inverse=true)
    )
}
