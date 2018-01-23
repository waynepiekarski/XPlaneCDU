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
import android.content.Context
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import java.net.InetAddress
import kotlin.concurrent.thread

class MainActivity : Activity(), TCPClient.OnReceiveTCP, MulticastReceiver.OnReceiveMulticast {

    private var becn_listener: MulticastReceiver? = null
    private var tcp_extplane: TCPClient? = null
    private var xplane_address: InetAddress? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        versionText.text = "v" + BuildConfig.VERSION_NAME + " " + BuildConfig.VERSION_CODE + " " + BuildConfig.BUILD_TYPE

        cduImage.setOnTouchListener { _view, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                // Compute touch location relative to the original image size
                val ix = ((motionEvent.x * cduImage.getDrawable().intrinsicWidth) / cduImage.width).toInt()
                val iy = ((motionEvent.y * cduImage.getDrawable().intrinsicHeight) / cduImage.height).toInt()
                Log.d(Const.TAG, "ImageClick = ${ix},${iy}, RawClick = ${motionEvent.x},${motionEvent.y} from Image ${cduImage.getDrawable().intrinsicWidth},${cduImage.getDrawable().intrinsicHeight} -> ${cduImage.width},${cduImage.height}")
            }
            true
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onConfigurationChanged(config: Configuration) {
        Log.d(Const.TAG, "onConfigurationChanged")
        super.onConfigurationChanged(config)
    }

    override fun onResume() {
        super.onResume()

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
        Log.d(Const.TAG, "onResume(), starting connections with IP address " + ip)
        ipAddress.text = ip

        becn_listener = MulticastReceiver(Const.BECN_ADDRESS, Const.BECN_PORT, this)
    }

    override fun onPause() {
        Log.d(Const.TAG, "onPause(), cancelling UDP listeners")
        if (tcp_extplane != null) {
            tcp_extplane!!.stopListener()
            tcp_extplane = null
        }
        becn_listener!!.stopListener()
        super.onPause()
    }

    override fun onReceiveMulticast(buffer: ByteArray, source: InetAddress) {
        Log.d(Const.TAG, "Received BECN multicast packet from $source")
        xplaneHost.setText("Found BECN: " + source.getHostAddress())
        xplane_address = source

        if (tcp_extplane == null) {
            Log.d(Const.TAG, "Making connection to $xplane_address")
            tcp_extplane = TCPClient(xplane_address!!, Const.TCP_EXTPLANE_PORT, this)
        }
    }

    var comms_started = false

    override fun onReceiveTCP(line: String) {
        if (line == "EXTPLANE 1") {
            Log.d(Const.TAG, "Found ExtPlane welcome message, will now make requests")
            xplaneHost.setText("Found EXTPLANE: " + xplane_address)

            // Make requests for CDU values on a separate thread
            thread(start = true) {
                tcp_extplane!!.writeln("sub sim/aircraft/view/acf_descrip")
                for (line in Definitions.CDULinesZibo737) {
                    // Log.d(Const.TAG, "Requesting CDU text key=" + line.key + " value=" + line.value.description)
                    tcp_extplane!!.writeln("sub " + line.key)
                }
            }
        } else {
            // Log.d(Const.TAG, "Received TCP line [$line]")
            if (!comms_started) {
                xplaneHost.setText("X-Plane: " + xplane_address)
                comms_started = true
            }

            val tokens = line.split(" ")
            if (tokens[0] == "ub") {
                val decoded = String(Base64.decode(tokens[2], Base64.DEFAULT))
                Log.d(Const.TAG, "Decoded byte array for name [${tokens[1]}] with string [${decoded}]")
            } else {
                Log.e(Const.TAG, "Unknown encoding type [${tokens[0]}] for name [${tokens[1]}]")
            }
        }
    }
}
