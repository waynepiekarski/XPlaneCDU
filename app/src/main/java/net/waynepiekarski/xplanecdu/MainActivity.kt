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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
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

        cduImage.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            Log.d(Const.TAG, "CDU raw image = ${cduImage.getDrawable().intrinsicWidth}x${cduImage.getDrawable().intrinsicHeight}")
            Log.d(Const.TAG, "CDU scaled image = ${cduImage.width}x${cduImage.height}")
            val scaleX = cduImage.width / cduImage.getDrawable().intrinsicWidth.toFloat()
            val scaleY = cduImage.height / cduImage.getDrawable().intrinsicHeight.toFloat()

            // Compute the dimensions of the text display in actual device pixels,
            // since the CDU ImageView has been stretched to fit this
            val pixelXLeft   = (Definitions.displayXLeft   * scaleX).toInt()
            val pixelXRight  = (Definitions.displayXRight  * scaleX).toInt()
            val pixelYTop    = (Definitions.displayYTop    * scaleY).toInt()
            val pixelYBottom = (Definitions.displayYBottom * scaleY).toInt()
            val pixelWidth   = pixelXRight - pixelXLeft
            val pixelHeight  = pixelYBottom - pixelYTop

            // Set the top and left padding in pixels according to where the text display starts
            val lp1 = leftPaddingDisplay.getLayoutParams()
            lp1.width = pixelXLeft
            leftPaddingDisplay.setLayoutParams(lp1)
            val lp2 = topPaddingDisplay.getLayoutParams()
            lp2.height = pixelYTop
            topPaddingDisplay.setLayoutParams(lp2)

            // Resize the font until everything exceeds the total height.
            // The font is taller than wide, so we will not exceed the total width.
            val fontRatio = Definitions.displayLabelRatio
            var fontSize = 1.0f
            var validFontSize = -1.0f
            var validSet = false
            while (true) {
                Log.d(Const.TAG, "Attempting to test font size $fontSize to exceed height $pixelHeight")
                var totalHeight = 0
                // val fontRatio = getResources().getDimension(R.dimen.cdu_label_to_large_ratio)
                for (entry in Definitions.CDULinesZibo737) {
                    if (entry.value.inverse)
                        continue // Skip inverse values, they are duplicates or the normal large ones
                    val tv = entry.value.getTextView(this)
                    val scale = if (entry.value.small)
                        Definitions.displaySmallRatio
                    else if (entry.value.label)
                        Definitions.displayLabelRatio
                    else
                        Definitions.displayLargeRatio
                    tv.setTextSize(fontSize * scale)
                    tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                    if (!entry.value.small) // Small overlaps with large, don't add it to the total
                        totalHeight += tv.getMeasuredHeight()
                }
                Log.d(Const.TAG, "After font size $fontSize, computed new height $totalHeight compared to desired $pixelHeight")
                if (validSet) {
                    // Search is done, and we reapplied the last fitting font size
                    Log.d(Const.TAG, "The search for a fitting font is done and applied $fontSize")
                    break
                } else if (totalHeight < pixelHeight) {
                    validFontSize = fontSize
                    fontSize += 0.5f // Android seems to only implement steps of 0.5
                } else {
                    // Done with the search, we need to back up one size now and reapply it
                    validSet = true
                    fontSize = validFontSize
                    Log.d(Const.TAG, "Reversing back a fontSize to $fontSize and reapplying")
                }
            }
            Log.d(Const.TAG, "Found fitting font size $validFontSize")

            // Compute a suitable width for each line type (small, large, label)
            var scaleXSmall = 1.0f
            while (true) {
                terminalTextSmall1.setTextScaleX(scaleXSmall)
                terminalTextSmall1.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val width = terminalTextSmall1.getMeasuredWidth()
                Log.d(Const.TAG, "After X scale $scaleXSmall received width $width for small lines")
                if (width < pixelWidth) {
                    scaleXSmall += 0.02f
                } else {
                    // Search is done, we exceeded the constraints so revert back one step
                    scaleXSmall -= 0.02f
                    break
                }
            }
            var scaleXLarge = 1.0f
            while (true) {
                terminalTextLarge1.setTextScaleX(scaleXLarge)
                terminalTextLarge1.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val width = terminalTextLarge1.getMeasuredWidth()
                Log.d(Const.TAG, "After X scale $scaleXLarge received width $width for large lines")
                if (width < pixelWidth) {
                    scaleXLarge += 0.02f
                } else {
                    // Search is done, we exceeded the constraints so revert back one step
                    scaleXLarge -= 0.02f
                    break
                }
            }
            var scaleXLabel = 1.0f
            while (true) {
                terminalLabel1.setTextScaleX(scaleXLabel)
                terminalLabel1.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val width = terminalLabel1.getMeasuredWidth()
                Log.d(Const.TAG, "After X scale $scaleXLabel received width $width for label lines")
                if (width < pixelWidth) {
                    scaleXLabel += 0.02f
                } else {
                    // Search is done, we exceeded the constraints so revert back one step
                    scaleXLabel -= 0.02f
                    break
                }
            }

            // Apply the final text sizes to all lines now
            for (entry in Definitions.CDULinesZibo737) {
                val tv = entry.value.getTextView(this)
                if (entry.value.small)      tv.setTextScaleX(scaleXSmall)
                else if (entry.value.label) tv.setTextScaleX(scaleXLabel)
                else                        tv.setTextScaleX(scaleXLarge)
            }

            // Draw a debugging view that shows where all the keys are specified
            val debug = true
            if (debug) {
                val bitmapDrawable = cduImage.getDrawable() as BitmapDrawable
                val bitmap = bitmapDrawable.getBitmap()
                val bitmapCopy = bitmap.copy(bitmap.getConfig(), true)
                val canvas = Canvas(bitmapCopy)
                val paint = Paint()
                paint.color = Color.RED

                fun drawBox(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
                    canvas.drawLine(x1, y1, x2, y1, paint)
                    canvas.drawLine(x2, y1, x2, y2, paint)
                    canvas.drawLine(x2, y2, x1, y2, paint)
                    canvas.drawLine(x1, y2, x1, y1, paint)
                }

                drawBox(canvas, Definitions.displayXLeft, Definitions.displayYTop, Definitions.displayXRight, Definitions.displayYBottom, paint)

                for (entry in Definitions.CDUButtonsZibo737) {
                    if (entry.value.x1 >= 0) {
                        canvas.drawText(entry.value.label, entry.value.x1.toFloat() + 3.0f, entry.value.y2.toFloat() - 3.0f, paint)
                        drawBox(canvas, entry.value.x1.toFloat(), entry.value.y1.toFloat(), entry.value.x2.toFloat(), entry.value.y2.toFloat(), paint)
                    }
                }

                Log.d(Const.TAG, "Applying debug bitmap of size ${bitmapCopy.width}x${bitmapCopy.height}")
                cduImage.setImageBitmap(bitmapCopy)
            }
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
            Log.d(Const.TAG, "Making connection to $xplane_address:${Const.TCP_EXTPLANE_PORT}")
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
                for (entry in Definitions.CDULinesZibo737) {
                    // Log.d(Const.TAG, "Requesting CDU text key=" + entry.key + " value=" + entry.value.description)
                    tcp_extplane!!.writeln("sub " + entry.key)
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
                Log.d(Const.TAG, "Decoded byte array for name [${tokens[1]}] with string [$decoded]")
                val entry = Definitions.CDULinesZibo737.get(tokens[1])
                if (entry == null) {
                    Log.d(Const.TAG, "Found non-CDU result name [${tokens[1]}] with string [$decoded]")
                } else {
                    val view = entry!!.getTextView(this)
                    if (entry.inverse) {
                        Log.d(Const.TAG, "Ignoring _I inverted message, not sure what [ ] means right now")
                    } else {
                        view.setText(decoded)
                    }
                }
            } else {
                Log.e(Const.TAG, "Unknown encoding type [${tokens[0]}] for name [${tokens[1]}]")
            }
        }
    }
}
