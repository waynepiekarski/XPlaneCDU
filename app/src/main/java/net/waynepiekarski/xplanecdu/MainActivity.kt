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
import android.widget.EditText
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.SoundEffectConstants
import java.net.UnknownHostException


class MainActivity : Activity(), TCPClient.OnTCPEvent, MulticastReceiver.OnReceiveMulticast {

    private var becn_listener: MulticastReceiver? = null
    private var tcp_extplane: TCPClient? = null
    private var connectAddress: String? = null
    private var manualAddress: String = ""
    private var manualInetAddress: InetAddress? = null
    private var connectZibo = false
    private var connectActNotes: String = ""
    private var connectWorking = false
    private var connectShutdown = false
    private var connectFailures = 0
    private lateinit var overlayCanvas: Canvas
    private lateinit var sourceBitmap: Bitmap
    private var overlayOutlines = false
    private var lastLayoutLeft   = -1
    private var lastLayoutTop    = -1
    private var lastLayoutRight  = -1
    private var lastLayoutBottom = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(Const.TAG, "onCreate()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // It is very important to clear the CDU lines cache. If you don't, then it keeps TextViews
        // around from before the user pressed the back button, but they are no longer valid. When
        // you resume, the memory is still from before, but the layout is re-inflated with new views.
        Definitions.nullOnCreateCDULines()

        // Also important to reset the layout cache, for the same reason as the CDU lines cache
        lastLayoutLeft   = -1
        lastLayoutTop    = -1
        lastLayoutRight  = -1
        lastLayoutBottom = -1

        // Add the compiled-in BuildConfig values to the about text
        aboutText.text = aboutText.getText().toString().replace("__VERSION__", "Version: " + Const.getBuildVersion() + " " + BuildConfig.BUILD_TYPE + " build " + Const.getBuildId() + " " + "\nBuild date: " + Const.getBuildDateTime())

        // Reset the text display to known 24 column text so the layout pass can work correctly
        resetDisplay()
        Toast.makeText(this, "Click the panel screws to bring up help and usage information.\nClick the terminal screen or connection status to specify a manual hostname.", Toast.LENGTH_LONG).show()

        // Miscellaneous counters that also need reset
        connectFailures = 0

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
                        } else if (entry.key.startsWith("internal_hostname")) {
                            // Pop up the host name changer for this item type
                            popupManualHostname()
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
                            sendCommand(tcp_extplane, entry.key)

                            // Play sound effect on button press
                            cduImage.playSoundEffect(SoundEffectConstants.CLICK);
                        } else {
                            Log.w(Const.TAG, "Unknown command ${entry.key} for ${entry.value.label} - ignored")
                        }
                    }
                }
            }
            return@setOnTouchListener true
        }

        cduImage.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if ((lastLayoutLeft == left) && (lastLayoutTop == top) && (lastLayoutRight == right) && (lastLayoutBottom == bottom)) {
                Log.d(Const.TAG, "Skipping layout change since it is identical to current layout")
                return@addOnLayoutChangeListener
            }
            lastLayoutLeft = left
            lastLayoutTop = top
            lastLayoutRight = right
            lastLayoutBottom = bottom
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
                for (entry in Definitions.CDULinesZibo737) {
                    val tv = entry.value.getTextView(this)
                    if (tv.text.length != 24)
                        Log.e(Const.TAG, "Detected string with invalid length [${tv.text}] in ${entry.key}")
                    val scale = if (entry.value.small)
                        Definitions.displaySmallRatio
                    else if (entry.value.label)
                        Definitions.displayLabelRatio
                    else
                        Definitions.displayLargeRatio
                    tv.setTextSize(fontSize * scale)
                    tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                    val lp = tv.getLayoutParams() as RelativeLayout.LayoutParams
                    // Don't add to the total anything that overlaps with large
                    if (!entry.value.small && !entry.value.inverse && !entry.value.green && !entry.value.magenta)
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
            var scaleAttempts = 0
            while (true) {
                scaleAttempts++
                terminalTextSmall1.setTextScaleX(scaleXSmall)
                terminalTextSmall1.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val width = terminalTextSmall1.getMeasuredWidth()
                // Log.d(Const.TAG, "After X scale $scaleXSmall received width $width for small lines, expecting $pixelWidth")
                if (width < pixelWidth) {
                    scaleXSmall += scaleStep
                } else {
                    // Search is done, we exceeded the constraints so revert back one step
                    scaleXSmall -= scaleStep
                    Log.d(Const.TAG, "Found X scale $scaleXSmall for small lines, expecting $pixelWidth from $width, $scaleAttempts attempts")
                    break
                }
            }
            var scaleXLarge = scaleInitial
            scaleAttempts = 0
            while (true) {
                scaleAttempts++
                terminalTextLarge1.setTextScaleX(scaleXLarge)
                terminalTextLarge1.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val width = terminalTextLarge1.getMeasuredWidth()
                // Log.d(Const.TAG, "After X scale $scaleXLarge received width $width for large lines, expecting $pixelWidth")
                if (width < pixelWidth) {
                    scaleXLarge += scaleStep
                } else {
                    // Search is done, we exceeded the constraints so revert back one step
                    scaleXLarge -= scaleStep
                    Log.d(Const.TAG, "Found X scale $scaleXLarge for large lines, expecting $pixelWidth from $width, $scaleAttempts attempts")
                    break
                }
            }
            var scaleXLabel = scaleInitial
            scaleAttempts = 0
            while (true) {
                scaleAttempts++
                terminalLabel1.setTextScaleX(scaleXLabel)
                terminalLabel1.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val width = terminalLabel1.getMeasuredWidth()
                // Log.d(Const.TAG, "After X scale $scaleXLabel received width $width for label lines, expecting $pixelWidth")
                if (width < pixelWidth) {
                    scaleXLabel += scaleStep
                } else {
                    // Search is done, we exceeded the constraints so revert back one step
                    scaleXLabel -= scaleStep
                    Log.d(Const.TAG, "Found X scale $scaleXLabel for label lines, expecting $pixelWidth from $width, $scaleAttempts attempts")
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

        connectText.setOnClickListener { popupManualHostname() }
    }

    // The user can click on the connectText and specify a X-Plane hostname manually
    private fun changeManualHostname(hostname: String) {
        if (hostname.isEmpty()) {
            Log.d(Const.TAG, "Clearing override X-Plane hostname for automatic mode, saving to prefs, restarting networking")
            manualAddress = hostname
            val sharedPref = getPreferences(Context.MODE_PRIVATE)
            with(sharedPref.edit()){
                putString("manual_address", manualAddress)
                commit()
            }
            restartNetworking()
        } else {
            Log.d(Const.TAG, "Setting override X-Plane hostname to $manualAddress")
            // Lookup the IP address on a background thread
            thread(start = true) {
                try {
                    manualInetAddress = InetAddress.getByName(hostname)
                } catch (e: UnknownHostException) {
                    // IP address was not valid, so ask for another one and exit this thread
                    Handler(Looper.getMainLooper()).post { popupManualHostname(error=true) }
                    return@thread
                }

                // We got a valid IP address, so we can now restart networking on the UI thread
                Handler(Looper.getMainLooper()).post {
                    manualAddress = hostname
                    Log.d(Const.TAG, "Converted manual X-Plane hostname [$manualAddress] to ${manualInetAddress}, saving to prefs, restarting networking")
                    val sharedPref = getPreferences(Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString("manual_address", manualAddress)
                        commit()
                    }
                    restartNetworking()
                }
            }
        }
    }

    private fun popupManualHostname(error: Boolean = false) {
        val builder = AlertDialog.Builder(this)
        if (error)
            builder.setTitle("Invalid entry! Specify X-Plane hostname or IP")
        else
            builder.setTitle("Specify X-Plane hostname or IP")

        val input = EditText(this)
        input.setText(manualAddress)
        input.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        builder.setView(input)
        builder.setPositiveButton("Manual Override") { dialog, which -> changeManualHostname(input.text.toString()) }
        builder.setNegativeButton("Revert") { dialog, which -> dialog.cancel() }
        builder.setNeutralButton("Automatic Multicast") { dialog, which -> changeManualHostname("") }
        builder.show()
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
        if (exec.illuminate) {
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

        // Notify the ImageView about the latest bitmap change
        cduHelp.invalidate()
    }

    private fun sendCommand(tcpRef: TCPClient?, cmnd: String) {
        // Send the command on a separate thread
        thread(start = true) {
            if ((tcpRef != null) && (tcpRef == tcp_extplane) && connectWorking) {
                tcpRef.writeln("cmd once $cmnd")
            } else {
                Log.d(Const.TAG, "Ignoring command $cmnd since TCP connection is not available")
            }
        }
    }


    fun padString24(str: String = "", brackets: Boolean = false): String {
        val limit = if(brackets) 22 else 24
        check(str.length <= limit) { "Input string [$str] length ${str.length} exceeds limit $limit" }
        if (brackets)
            return String.format("<%-22s>", str)
        else
            return String.format("%-24s", str)
    }

    fun centerString24(str: String = "", brackets: Boolean = false): String {
        val limit = if(brackets) 22 else 24
        check(str.length <= limit) { "Input string [$str] length ${str.length} exceeds limit $limit" }
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
            if (entry.value.label)        tv.setText("LabelLabelLabelLabelLabe")
            else if (entry.value.small)   tv.setText("S    S    S    S    S   ")
            else if (entry.value.inverse) tv.setText(" I    I    I    I    I  ")
            else if (entry.value.green)   tv.setText("  G    G    G    G    G ")
            else if (entry.value.magenta) tv.setText("   M    M    M    M    M")
            else                          tv.setText("    L    L    L    L    ")
        }
    }

    fun resetDisplay() {
        Log.d(Const.TAG, "resetDisplay()")
        for (entry in Definitions.CDULinesZibo737) {
            val tv = entry.value.getTextView(this)
            if (entry.value.inverse)      tv.setText(padString24(brackets=false))
            else if (entry.value.green)   tv.setText(padString24(brackets=false))
            else if (entry.value.magenta) tv.setText(padString24(brackets=false))
            else if (entry.value.small)   tv.setText(padString24(brackets=false))
            else if (entry.value.label)   tv.setText(padString24(brackets=true))
            else                          tv.setText(padString24(brackets=true))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Only implement full-screen in API >= 19, older Android brings them back on each click
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
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
        connectShutdown = false

        // Retrieve the manual address from shared preferences
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val prefAddress = sharedPref.getString("manual_address", "")
        Log.d(Const.TAG, "Found preferences value for manual_address = [$prefAddress]")

        // Pass on whatever this string is, and will end up calling restartNetworking()
        changeManualHostname(prefAddress)
    }

    private fun setConnectionStatus(line1: String, line2: String, fixup: String, dest: String? = null) {
        Log.d(Const.TAG, "Changing connection status to [$line1][$line2] with destination [$dest]")
        var out = line1 + ". "
        if (line2.length > 0)
            out += "${line2}. "
        if (fixup.length > 0)
            out += "${fixup}. "
        if (dest != null)
            out += "${dest}."
        if (connectFailures > 0)
            out += "\nError #$connectFailures"

        connectText.text = out

        terminalTextGreen0.setText(centerString24("XPlaneCDU", brackets=true))
        terminalLabel1.setText(centerString24("For Zibo 738 and XP11", brackets=true))

        terminalLabel2.setText(centerString24(line1, brackets=true))
        terminalTextLarge2.setText(centerString24(line2, brackets=true))
        if (connectFailures > 0)
            terminalLabel3.setText(centerString24("Error #$connectFailures", brackets=true))
        else
            terminalLabel3.setText(centerString24("", brackets=true))
        terminalTextMagenta3.setText(centerString24(fixup, brackets=true))
        terminalLabel4.setText(centerString24("Tap screws for help", brackets=true))

        terminalLabel5.setText(centerString24("v" + Const.getBuildVersion() + " Build " + Const.getBuildId(), brackets=true))
        terminalTextLarge5.setText(centerString24(Const.getBuildDateTime(), brackets=true))

        terminalTextLarge7.setText(centerString24("Tap screen to set IP", brackets=true))
    }

    private fun restartNetworking() {
        Log.d(Const.TAG, "restartNetworking()")
        resetDisplay()
        setConnectionStatus("Closing down network", "", "Wait a few seconds")
        connectAddress = null
        connectWorking = false
        connectZibo = false
        connectActNotes = ""
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
        if (connectShutdown) {
            Log.d(Const.TAG, "Will not restart BECN listener since connectShutdown is set")
        } else {
            if (manualAddress.isEmpty()) {
                setConnectionStatus("Waiting for X-Plane", "BECN broadcast", "Touch to override")
                Log.d(Const.TAG, "Starting X-Plane BECN listener since connectShutdown is not set")
                becn_listener = MulticastReceiver(Const.BECN_ADDRESS, Const.BECN_PORT, this)
            } else {
                Log.d(Const.TAG, "Manual address $manualAddress specified, skipping any auto-detection")
                check(tcp_extplane == null)
                connectAddress = manualAddress
                setConnectionStatus("Manual TCP connect", "", "Needs ExtPlane plugin", "$connectAddress:${Const.TCP_EXTPLANE_PORT}")
                tcp_extplane = TCPClient(manualInetAddress!!, Const.TCP_EXTPLANE_PORT, this)
            }
        }
    }

    override fun onPause() {
        Log.d(Const.TAG, "onPause()")
        connectShutdown = true // Prevent new BECN listeners starting up in restartNetworking
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

    override fun onDestroy() {
        Log.d(Const.TAG, "onDestroy()")
        super.onDestroy()
    }

    override fun onFailureMulticast(ref: MulticastReceiver) {
        if (ref != becn_listener)
            return
        connectFailures++
        setConnectionStatus("No network available", "Cannot listen for BECN", "Enable WiFi")
    }

    override fun onTimeoutMulticast(ref: MulticastReceiver) {
        if (ref != becn_listener)
            return
        Log.d(Const.TAG, "Received indication the multicast socket is not getting replies, will restart it and wait again")
        connectFailures++
        setConnectionStatus("Timeout waiting for", "BECN multicast", "Touch to override")
    }

    override fun onReceiveMulticast(buffer: ByteArray, source: InetAddress, ref: MulticastReceiver) {
        if (ref != becn_listener)
            return
        setConnectionStatus("Found BECN multicast", "", "Wait a few seconds", source.getHostAddress())
        connectAddress = source.toString().replace("/","")

        // The BECN listener will only reply once, so close it down and open the TCP connection
        becn_listener!!.stopListener()
        becn_listener = null

        check(tcp_extplane == null)
        Log.d(Const.TAG, "Making connection to $connectAddress:${Const.TCP_EXTPLANE_PORT}")
        tcp_extplane = TCPClient(source, Const.TCP_EXTPLANE_PORT, this)
    }

    override fun onConnectTCP(tcpRef: TCPClient) {
        if (tcpRef != tcp_extplane)
            return
        // We will wait for EXTPLANE 1 in onReceiveTCP, so don't send the requests just yet
        setConnectionStatus("Established TCP", "Waiting for ExtPlane", "Needs ExtPlane plugin", "$connectAddress:${Const.TCP_EXTPLANE_PORT}")
    }

    override fun onDisconnectTCP(tcpRef: TCPClient) {
        if (tcpRef != tcp_extplane)
            return
        Log.d(Const.TAG, "onDisconnectTCP(): Closing down TCP connection and will restart")
        connectFailures++
        restartNetworking()
    }

    override fun onReceiveTCP(line: String, tcpRef: TCPClient) {
        // If the current connection does not match the incoming reference, it is out of date and should be ignored.
        // This is important otherwise we will try to transmit on the wrong socket, fail, and then try to restart.
        if (tcpRef != tcp_extplane)
            return

        if (line == "EXTPLANE 1") {
            Log.d(Const.TAG, "Found ExtPlane welcome message, will now make subscription requests for aircraft info")
            setConnectionStatus("Received EXTPLANE", "Sending acf subscribe", "Start your flight", "$connectAddress:${Const.TCP_EXTPLANE_PORT}")

            // Make requests for aircraft type messages so we can detect when the Zibo 738 is available,
            // the datarefs do not exist until the aircraft is loaded and in use
            thread(start = true) {
                tcpRef.writeln("sub sim/aircraft/view/acf_descrip")
                tcpRef.writeln("sub sim/aircraft/view/acf_notes")
            }
        } else {
            // Log.d(Const.TAG, "Received TCP line [$line]")
            if (!connectWorking) {
                check(!connectZibo) { "connectZibo should not be set if connectWorking is not set" }
                // Everything is working with actual data coming back.
                // This is the last time we can put debug text on the CDU before it is overwritten
                connectFailures = 0
                setConnectionStatus("X-Plane CDU starting", "Check aircraft type", "Must be Zibo 738", "$connectAddress:${Const.TCP_EXTPLANE_PORT}")
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
                    // We have received either acf_notes or acf_descrip, so we need to see if the
                    // aircraft has changed, and if Zibo is available for us to subscribe to.
                    if (tokens[1] == "sim/aircraft/view/acf_notes") {
                        if (decoded != connectActNotes) {
                            // The aircraft name has actually changed from before
                            if (decoded.toLowerCase().startsWith("zibomod")) {
                                setConnectionStatus("X-Plane CDU starting", "Starting subscription", "Must be Zibo 738", "$connectAddress:${Const.TCP_EXTPLANE_PORT}")

                                // The aircraft has changed to the Zibo 738, so start the subscription process
                                thread(start = true) {
                                    Log.d(Const.TAG, "Sending subscriptions for Zibo 738 datarefs now that it is running")
                                    for (entry in Definitions.CDULinesZibo737) {
                                        // Log.d(Const.TAG, "Requesting CDU text key=" + entry.key + " value=" + entry.value.description)
                                        tcpRef.writeln("sub " + entry.key)
                                    }
                                    for (entry in Definitions.CDUButtonsZibo737) {
                                        if (entry.value.light) {
                                            Log.d(Const.TAG, "Requesting illuminated status key=" + entry.key + " value=" + entry.value.description)
                                            tcpRef.writeln("sub " + entry.key)
                                        }
                                    }
                                }
                            } else {
                                // The aircraft changed to something non-Zibo, so reset the CDU and wait for a new aircraft
                                connectZibo = false
                                resetDisplay()
                                setConnectionStatus("Waiting for Zibo 738", "Non-Zibo detected", "Change to Zibo 738", "$connectAddress:${Const.TCP_EXTPLANE_PORT}")
                            }
                            connectActNotes = decoded
                        } else {
                            Log.d(Const.TAG, "acf_notes updated, but no change from previous [$connectActNotes]")
                        }
                    } else {
                        Log.d(Const.TAG, "Found unused result name [${tokens[1]}] with string [$fixed]")
                    }
                } else {
                    val view = entry.getTextView(this)
                    // Always pad to 24 chars so the terminal is always ready to be re-laid out
                    view.text = padString24(fixed)
                    // If this is the first time we found a Zibo CDU dataref, then update the UI, this is the final step!
                    if (!connectZibo) {
                        setConnectionStatus("X-Plane CDU working", "", "", "$connectAddress:${Const.TCP_EXTPLANE_PORT}")
                        connectZibo = true
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
