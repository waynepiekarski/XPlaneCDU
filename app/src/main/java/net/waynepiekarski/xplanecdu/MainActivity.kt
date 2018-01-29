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
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.net.InetAddress
import kotlin.concurrent.thread



class MainActivity : Activity(), TCPClient.OnTCPEvent, MulticastReceiver.OnReceiveMulticast {

    private var becn_listener: MulticastReceiver? = null
    private var tcp_extplane: TCPClient? = null
    private var connectAddress: String? = null
    private var connectWorking = false
    private lateinit var overlayCanvas: Canvas
    private lateinit var sourceBitmap: Bitmap
    private var overlayOutlines = false
    private var overlayLightExec = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        aboutText.text = aboutText.getText().toString().replace("__VERSION__", "v" + BuildConfig.VERSION_NAME + " " + BuildConfig.VERSION_CODE + " " + BuildConfig.BUILD_TYPE)

        // Reset the text display to known 24 column text so the layout pass can work correctly
        resetDisplay()
        Toast.makeText(this, "Click the panel screws to bring up help and usage information, ", Toast.LENGTH_LONG).show()

        cduImage.setOnTouchListener { _view, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_UP) {
                // Compute touch location relative to the original image size
                val ix = ((motionEvent.x * cduImage.getDrawable().intrinsicWidth) / cduImage.width).toInt()
                val iy = ((motionEvent.y * cduImage.getDrawable().intrinsicHeight) / cduImage.height).toInt()
                Log.d(Const.TAG, "ImageClick = ${ix},${iy}, RawClick = ${motionEvent.x},${motionEvent.y} from Image ${cduImage.getDrawable().intrinsicWidth},${cduImage.getDrawable().intrinsicHeight} -> ${cduImage.width},${cduImage.height}")

                // If the help is visible, hide it on any kind of click
                if (aboutText.visibility == View.VISIBLE) {
                    aboutText.visibility = View.INVISIBLE
                    overlayOutlines = false
                    refreshOverlay()
                    return@setOnTouchListener true
                }

                // Find the click inside the definitions
                for (entry in Definitions.CDUButtonsZibo737) {
                    if ((ix >= entry.value.x1) && (ix <= entry.value.x2) && (iy >= entry.value.y1) && (iy <= entry.value.y2)) {
                        Log.d(Const.TAG, "Found click matches to key ${entry.key}")
                        if (entry.value.light) {
                            // Do not handle clicks within lights, they are not buttons
                        } else if (entry.key.startsWith("internal_help")) {
                            // One of the many help buttons were pressed, they all map to the same action
                            if (aboutText.visibility == View.VISIBLE) {
                                aboutText.visibility = View.INVISIBLE
                                overlayOutlines = false
                                refreshOverlay()
                            } else {
                                aboutText.visibility = View.VISIBLE
                                overlayOutlines = true
                                refreshOverlay()
                            }
                        } else if (entry.key.startsWith("laminar/")) {
                            // Regular button press
                            Log.d(Const.TAG, "Need to send command ${entry.key} for ${entry.value.label}")
                            sendCommand(entry.key)
                        } else {
                            Log.w(Const.TAG, "Unknown command ${entry.key} for ${entry.value.label} - ignored")
                        }
                    }
                }
            }
            return@setOnTouchListener true
        }

        cduImage.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            Log.d(Const.TAG, "Layout change: $left, $top, $right, $bottom")
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

            // Adjust the about box to the correct width to fit only over the text display
            val lp3 = aboutText.getLayoutParams()
            lp3.width = pixelWidth
            aboutText.setLayoutParams(lp3)

            // Resize the font until everything exceeds the total height.
            // The font is taller than wide, so we will not exceed the total width.
            var fontSize = 1.0f
            var validFontSize = -1.0f
            var validSet = false
            while (true) {
                // Log.d(Const.TAG, "Attempting to test font size $fontSize to exceed height $pixelHeight")
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
                    val lp = tv.getLayoutParams() as RelativeLayout.LayoutParams
                    if (!entry.value.small) // Small overlaps with large, don't add it to the total
                        totalHeight += tv.getMeasuredHeight() + lp.topMargin + lp.bottomMargin // Include negative margins
                }
                // Log.d(Const.TAG, "After font size $fontSize, computed new height $totalHeight compared to desired $pixelHeight")
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
            // The step size needs to be small enough otherwise the rows won't all align, 0.02f fails
            // Need to pick a small enough starting point, because 1.0 on some devices is too wide
            val scaleStep = 0.01f
            val scaleInitial = 0.05f
            var scaleXSmall = scaleInitial
            while (true) {
                terminalTextSmall1.setTextScaleX(scaleXSmall)
                terminalTextSmall1.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val width = terminalTextSmall1.getMeasuredWidth()
                // Log.d(Const.TAG, "After X scale $scaleXSmall received width $width for small lines, expecting $pixelWidth")
                if (width < pixelWidth) {
                    scaleXSmall += scaleStep
                } else {
                    // Search is done, we exceeded the constraints so revert back one step
                    scaleXSmall -= scaleStep
                    Log.d(Const.TAG, "Found X scale $scaleXSmall for small lines, expecting $pixelWidth")
                    break
                }
            }
            var scaleXLarge = scaleInitial
            while (true) {
                terminalTextLarge1.setTextScaleX(scaleXLarge)
                terminalTextLarge1.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val width = terminalTextLarge1.getMeasuredWidth()
                // Log.d(Const.TAG, "After X scale $scaleXLarge received width $width for large lines, expecting $pixelWidth")
                if (width < pixelWidth) {
                    scaleXLarge += scaleStep
                } else {
                    // Search is done, we exceeded the constraints so revert back one step
                    scaleXLarge -= scaleStep
                    Log.d(Const.TAG, "Found X scale $scaleXLarge for large lines, expecting $pixelWidth")
                    break
                }
            }
            var scaleXLabel = scaleInitial
            while (true) {
                terminalLabel1.setTextScaleX(scaleXLabel)
                terminalLabel1.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val width = terminalLabel1.getMeasuredWidth()
                // Log.d(Const.TAG, "After X scale $scaleXLabel received width $width for label lines, expecting $pixelWidth")
                if (width < pixelWidth) {
                    scaleXLabel += scaleStep
                } else {
                    // Search is done, we exceeded the constraints so revert back one step
                    scaleXLabel -= scaleStep
                    Log.d(Const.TAG, "Found X scale $scaleXLabel for label lines, expecting $pixelWidth")
                    break
                }
            }

            // Apply the final text sizes to all lines now
            Log.d(Const.TAG, "Applying final text sizes to all lines now")
            for (entry in Definitions.CDULinesZibo737) {
                val tv = entry.value.getTextView(this)
                if (entry.value.small)      tv.setTextScaleX(scaleXSmall)
                else if (entry.value.label) tv.setTextScaleX(scaleXLabel)
                else                        tv.setTextScaleX(scaleXLarge)
            }

            // Create a transparent overlay to draw key outlines and also any other indicators
            val bitmapDrawable = cduImage.getDrawable() as BitmapDrawable
            sourceBitmap = bitmapDrawable.getBitmap()
            val bitmapNew = Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, Bitmap.Config.ARGB_8888)
            overlayCanvas = Canvas(bitmapNew)
            Log.d(Const.TAG, "Adding overlay bitmap of size ${bitmapNew.width}x${bitmapNew.height}")
            cduHelp.setImageBitmap(bitmapNew)

            // Refresh the overlay for the first time
            refreshOverlay()
        }
    }

    fun refreshOverlay() {
        // Always clear the overlay first, this is not a super efficient process, but not used very often
        overlayCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        fun drawBox(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
            canvas.drawLine(x1, y1, x2, y1, paint)
            canvas.drawLine(x2, y1, x2, y2, paint)
            canvas.drawLine(x2, y2, x1, y2, paint)
            canvas.drawLine(x1, y2, x1, y1, paint)
        }

        // Illuminate the exec light if active
        val exec = Definitions.CDUButtonsZibo737["laminar/B738/indicators/fmc_exec_lights"]!!
        if (exec!!.illuminate) {
            val paint = Paint()
            paint.color = Color.YELLOW
            paint.style = Paint.Style.FILL
            overlayCanvas.drawRect(exec.x1.toFloat(), exec.y1.toFloat(), exec.x2.toFloat()+1, exec.y2.toFloat()+1, paint)
        }

        // We have other lamp indicators where we apply a color booster to make it look illuminated
        for (lamp in arrayOf("laminar/B738/fmc/fmc_message", "internal_ofst_light", "internal_dspyfail_light")) {
            val item = Definitions.CDUButtonsZibo737[lamp]!!
            if (!item.illuminate)
                continue
            val paint = Paint()
            paint.style = Paint.Style.FILL

            if (item.brightBitmap == null) {
                val partBitmap = Bitmap.createBitmap(item.x2 - item.x1, item.y2 - item.y1, Bitmap.Config.ARGB_8888)

                for (x in item.x1..item.x2 - 1) {
                    for (y in item.y1..item.y2 - 1) {
                        var color = sourceBitmap.getPixel(x, y)
                        if (Color.red(color) < 0x20)
                            color = Color.BLACK
                        else
                            color = Color.YELLOW

                        partBitmap.setPixel(x - item.x1, y - item.y1, color)
                    }
                }
                item.brightBitmap = partBitmap
            }

            overlayCanvas.drawBitmap(item.brightBitmap, item.x1.toFloat(), item.y1.toFloat(), paint)
        }

        // Draw the key outlines if they are active
        if (overlayOutlines) {
            val paint = Paint()
            paint.color = Color.RED

            drawBox(overlayCanvas, Definitions.displayXLeft, Definitions.displayYTop, Definitions.displayXRight, Definitions.displayYBottom, paint)

            for (entry in Definitions.CDUButtonsZibo737) {
                if (entry.value.x1 >= 0) {
                    overlayCanvas.drawText(entry.value.label, entry.value.x1.toFloat() + 3.0f, entry.value.y2.toFloat() - 3.0f, paint)
                    drawBox(overlayCanvas, entry.value.x1.toFloat(), entry.value.y1.toFloat(), entry.value.x2.toFloat(), entry.value.y2.toFloat(), paint)
                }
            }
        }
    }

    fun sendCommand(cmnd: String) {
        // Send the command on a separate thread
        thread(start = true) {
            if (tcp_extplane != null) {
                tcp_extplane!!.writeln("cmd once $cmnd")
            } else {
                Log.d(Const.TAG, "Ignoring command $cmnd since TCP connection is not available")
            }
        }
    }


    fun padString24(str: String = "", brackets: Boolean = false): String {
        if (brackets)
            return String.format("<%-22s>", str)
        else
            return String.format("%-24s", str)
    }

    fun centerString24(str: String = "", brackets: Boolean = false): String {
        val limit = if(brackets) 22 else 24
        check(str.length <= limit) { "Input string [$str] exceeds limit $limit" }
        val spaces = limit - str.length
        val left = spaces / 2
        val right = spaces - left
        if (brackets)
            return "<" + " ".repeat(left) + str + " ".repeat(right) + ">"
        else
            return " ".repeat(left) + str + " ".repeat(right)
    }

    fun resetDisplayDebug() {
        for (entry in Definitions.CDULinesZibo737) {
            val tv = entry.value.getTextView(this)
            if (entry.value.small)      tv.setText("SMALL-789012345678901234")
            else if (entry.value.label) tv.setText("LABEL-789012345678901234")
            else                        tv.setText("LARGE-789012345678901234")
        }
    }

    fun resetDisplay() {
        for (entry in Definitions.CDULinesZibo737) {
            val tv = entry.value.getTextView(this)
            if (entry.value.small)      tv.setText(padString24(brackets=false))
            if (entry.value.small)      tv.setText(padString24(brackets=false))
            else if (entry.value.label) tv.setText(padString24(brackets=true))
            else                        tv.setText(padString24(brackets=true))
        }
        terminalTextLarge3.setText(centerString24("XPlaneCDU", brackets=true))
        terminalLabel4.setText(centerString24("waiting", brackets=true))
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
        Log.d(Const.TAG, "onConfigurationChanged ignored")
        super.onConfigurationChanged(config)
    }

    override fun onResume() {
        super.onResume()
        Log.d(Const.TAG, "onResume()")
        restartNetworking()
    }

    private fun restartNetworking() {
        resetDisplay()
        connectText.setText("Waiting for X-Plane BECN broadcast")
        connectAddress = null
        connectWorking = false
        if (tcp_extplane != null) {
            Log.d(Const.TAG, "Cleaning up any TCP connections")
            tcp_extplane!!.stopListener()
            tcp_extplane = null
        }
        if (becn_listener != null) {
            Log.w(Const.TAG, "Cleaning up the BECN listener, somehow it is still around?")
            becn_listener!!.stopListener()
            becn_listener = null
        }
        Log.d(Const.TAG, "Starting X-Plane BECN listener")
        becn_listener = MulticastReceiver(Const.BECN_ADDRESS, Const.BECN_PORT, this)
    }

    override fun onPause() {
        if (tcp_extplane != null) {
            Log.d(Const.TAG, "onPause(): Cancelling existing TCP connection")
            tcp_extplane!!.stopListener()
            tcp_extplane = null
        }
        if (becn_listener != null) {
            Log.d(Const.TAG, "onPause(): Cancelling existing BECN listener")
            becn_listener!!.stopListener()
            becn_listener = null
        }
        super.onPause()
    }

    override fun onNetworkFailure() {
        Log.d(Const.TAG, "Received indication the network is not ready, cannot open socket")
        connectText.setText("No network available, cannot listen for X-Plane")
    }

    override fun onReceiveMulticast(buffer: ByteArray, source: InetAddress) {
        Log.d(Const.TAG, "Received BECN multicast packet from $source")
        connectText.setText("Received BECN: " + source.getHostAddress())
        connectAddress = source.toString().replace("/","")

        // The BECN listener will only reply once, so close it down and open the TCP connection
        becn_listener!!.stopListener()
        becn_listener = null

        check(tcp_extplane == null)
        Log.d(Const.TAG, "Making connection to $connectAddress:${Const.TCP_EXTPLANE_PORT}")
        tcp_extplane = TCPClient(source!!, Const.TCP_EXTPLANE_PORT, this)
    }

    override fun onConnectTCP() {
        // We will wait for EXTPLANE 1 in onReceiveTCP, so ignore this
        Log.d(Const.TAG, "Connected to ExtPlane, now waiting for welcome message")
        connectText.setText("Found X-Plane, waiting for ExtPlane plugin")
    }

    override fun onDisconnectTCP() {
        // Connection has closed down, reset the UI
        Log.d(Const.TAG, "onDisconnectTCP(): Closing down TCP connection to wait for new BECN")
        restartNetworking()
    }

    override fun onReceiveTCP(line: String) {
        if (line == "EXTPLANE 1") {
            Log.d(Const.TAG, "Found ExtPlane welcome message, will now make requests")
            connectText.setText("Received EXTPLANE from $connectAddress:${Const.TCP_EXTPLANE_PORT}")

            // Make requests for CDU values on a separate thread
            thread(start = true) {
                tcp_extplane!!.writeln("sub sim/aircraft/view/acf_descrip")
                for (entry in Definitions.CDULinesZibo737) {
                    // Log.d(Const.TAG, "Requesting CDU text key=" + entry.key + " value=" + entry.value.description)
                    tcp_extplane!!.writeln("sub " + entry.key)
                }
                for (entry in Definitions.CDUButtonsZibo737) {
                    if (entry.value.light) {
                        Log.d(Const.TAG, "Requesting illuminated status key=" + entry.key + " value=" + entry.value.description)
                        tcp_extplane!!.writeln("sub " + entry.key)
                    }
                }
            }
        } else {
            // Log.d(Const.TAG, "Received TCP line [$line]")
            if (!connectWorking) {
                // Everything is working with actual data coming back
                connectText.setText("X-Plane at $connectAddress:${Const.TCP_EXTPLANE_PORT}")
                connectWorking = true
            }

            val tokens = line.split(" ")
            if (tokens[0] == "ub") {
                val decoded = String(Base64.decode(tokens[2], Base64.DEFAULT))
                // Replace ` with degree symbol, and * with a diamond symbol (there is no box in Android fonts)
                val fixed = decoded.replace('`','\u00B0').replace('*','\u25CA')

                Log.d(Const.TAG, "Decoded byte array for name [${tokens[1]}] with string [$decoded]")
                val entry = Definitions.CDULinesZibo737[tokens[1]]
                if (entry == null) {
                    Log.d(Const.TAG, "Found non-CDU result name [${tokens[1]}] with string [$fixed]")
                } else {
                    val view = entry.getTextView(this)
                    if (entry.inverse) {
                        Log.d(Const.TAG, "Ignoring _I inverted message, not sure what [ ] means right now")
                    } else {
                        view.setText(fixed)
                    }
                }
            } else if (tokens[0] == "ud") {
                val number = tokens[2].toFloat()
                Log.d(Const.TAG, "Decoded number for name [${tokens[1]}] with value [$number]")
                val entry = Definitions.CDUButtonsZibo737[tokens[1]]
                if (entry == null) {
                    Log.d(Const.TAG, "Found non-CDU result name [${tokens[1]}] with value [$number]")
                } else {
                    if (entry.light) {
                        entry.illuminate = (number > 0.5f)
                        refreshOverlay()
                    } else {
                        Log.d(Const.TAG, "Found non-light name [${tokens[1]}] with value [$number]")
                    }
                }

            } else {
                Log.e(Const.TAG, "Unknown encoding type [${tokens[0]}] for name [${tokens[1]}]")
            }
        }
    }
}
