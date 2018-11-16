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

object Const {
    val TAG = "XPlaneCDU"
    val TCP_EXTPLANE_PORT = 51000
    val MIN_EXTPLANE_VERSION = 1000 // EXTPLANE-VERSION value should be greater or equal than this to avoid warnings
    val BECN_PORT = 49707
    val BECN_ADDRESS = "239.255.1.1"
    val ERROR_NETWORK_SLEEP: Long = 1000 // Number of msec to wait on network failure
    val ERROR_MULTICAST_LOOPS = 5 // Number of loops (seconds) before we give up and restart the socket

    fun getBuildId(): Int { return BuildConfig.VERSION_CODE }
    fun getBuildVersion(): String { return BuildConfig.VERSION_NAME }

    private var _datetime: String? = null
    fun getBuildDateTime(): String {
        if (_datetime != null)
            return _datetime!!
        // Convert integer value YYMMDDHHMM into string
        var c = getBuildId()
        val min = c % 100
        c /= 100
        val hrs = c % 100
        c /= 100
        val day = c % 100
        c /= 100
        val monthStr = arrayOf("N/A", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        val mon = monthStr[c % 100]
        c /= 100
        val year = c
        _datetime = "20%02d-%s-%02d %02d:%02d".format(year, mon, day, hrs, min)
        return _datetime!!
    }
}
