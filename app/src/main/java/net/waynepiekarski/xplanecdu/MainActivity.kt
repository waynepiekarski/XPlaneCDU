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
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.net.InetAddress
import android.widget.EditText
import android.app.AlertDialog
import android.content.Context
import android.os.*
import android.text.InputType
import android.view.SoundEffectConstants
import java.net.UnknownHostException




class MainActivity : Activity(), TCPClient.OnTCPEvent, MulticastReceiver.OnReceiveMulticast {

    private var becn_listener: MulticastReceiver? = null
    private var tcp_extplane: TCPClient? = null
    private var connectAddress: String? = null
    private var manualAddress: String = ""
    private var manualInetAddress: InetAddress? = null
    private var connectSupported = false
    private var connectActiveDescrip: String = ""
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
    private var lastLayoutColumns = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(Const.TAG, "onCreate()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // It is very important to clear the CDU lines cache. If you don't, then it keeps TextViews
        // around from before the user pressed the back button, but they are no longer valid. When
        // you resume, the memory is still from before, but the layout is re-inflated with new views.
        Definitions.nullOnCreateCDULines(Definitions.CDULinesZibo737)
        Definitions.nullOnCreateCDULines(Definitions.CDULinesSSG747)

        // Also important to reset the layout cache, for the same reason as the CDU lines cache
        lastLayoutLeft = -1
        lastLayoutTop = -1
        lastLayoutRight = -1
        lastLayoutBottom = -1
        lastLayoutColumns = -1

        // Add the compiled-in BuildConfig values to the about text
        aboutText.text = aboutText.getText().toString().replace("__VERSION__", "Version: " + Const.getBuildVersion() + " " + BuildConfig.BUILD_TYPE + " build " + Const.getBuildId() + " " + "\nBuild date: " + Const.getBuildDateTime())

        // Reset the text display to known column text so the layout pass can work correctly
        resetDisplay()
        Toast.makeText(this, "Click the panel screws to bring up help and usage information.\nClick the terminal screen or connection status to specify a manual hostname.", Toast.LENGTH_LONG).show()

        // Miscellaneous counters that also need reset
        connectFailures = 0

        cduImage.setOnTouchListener { _view, motionEvent ->
            if (backgroundThread == null) {
                // It seems possible for onTouch events to arrive after onPause, but the background
                // thread is now null, and I've observed null exceptions in doBgThread. So avoid handling
                // any events here if the app is not running.
                Log.w(Const.TAG, "onTouch event ignored after onPause()")
            } else if (motionEvent.action == MotionEvent.ACTION_UP) {
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
                for (entry in Definitions.buttons) {
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
                        } else if (entry.key.startsWith("internal_")) {
                            Log.w(Const.TAG, "Unknown internal command ${entry.key} for ${entry.value.label} - ignored")
                        } else {
                            // Handle button presses, which can either be commands or changing dataref values
                            if (entry.value.dataref) {
                                Log.d(Const.TAG, "Need to set dataref ${entry.key} to 1.0 for ${entry.value.label}")
                                setDataref(tcp_extplane, entry.key, 1.0f)
                            } else {
                                Log.d(Const.TAG, "Need to send command ${entry.key} for ${entry.value.label}")
                                sendCommand(tcp_extplane, entry.key)
                            }

                            // Play sound effect on button press
                            cduImage.playSoundEffect(SoundEffectConstants.CLICK)
                        }
                    }
                }
            }
            return@setOnTouchListener true
        }

        cduImage.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if ((lastLayoutLeft == left) && (lastLayoutTop == top) && (lastLayoutRight == right) && (lastLayoutBottom == bottom) && (lastLayoutColumns == Definitions.numColumns)) {
                Log.d(Const.TAG, "Skipping layout change since it is identical to current layout")
                return@addOnLayoutChangeListener
            }
            lastLayoutLeft = left
            lastLayoutTop = top
            lastLayoutRight = right
            lastLayoutBottom = bottom
            lastLayoutColumns = Definitions.numColumns
            layoutCduImage()
        }

        connectText.setOnClickListener { popupManualHostname() }
    }

    private fun changeCduImageColumns(columns: Int) {
        lastLayoutColumns = columns
    }

    private fun layoutCduImage() {
        Log.d(Const.TAG, "Layout change: $lastLayoutLeft, $lastLayoutTop, $lastLayoutRight, $lastLayoutBottom")
        Log.d(Const.TAG, "Number of text columns = $lastLayoutColumns")
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
            for (entry in Definitions.lines) {
                val tv = entry.value.getTextView(this)
                if (tv.text.length != Definitions.numColumns)
                    Log.e(Const.TAG, "Detected string with invalid length [${tv.text}]=${tv.text.length} in ${entry.key} expected ${Definitions.numColumns}")
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
        for (entry in Definitions.lines) {
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

    companion object {
        private var backgroundThread: HandlerThread? = null

        fun doUiThread(code: () -> Unit) {
            Handler(Looper.getMainLooper()).post { code() }
        }

        fun doBgThread(code: () -> Unit) {
            Handler(backgroundThread!!.getLooper()).post { code() }
        }
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
            doBgThread {
                try {
                    manualInetAddress = InetAddress.getByName(hostname)
                } catch (e: UnknownHostException) {
                    // IP address was not valid, so ask for another one and exit this thread
                    doUiThread { popupManualHostname(error=true) }
                    return@doBgThread
                }

                // We got a valid IP address, so we can now restart networking on the UI thread
                doUiThread {
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

        var fontSize = -1.0f
        var bounds = Rect()
        for ((_, item) in Definitions.buttons) {
            // Draw the label text in if this item has been flagged, otherwise use the text in the image
            if (item.drawLabel) {
                val paint = Paint()
                paint.color = Color.LTGRAY
                paint.textScaleX = 0.65f // Make the font a bit thinner than usual
                paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                paint.setAntiAlias(true)

                val maxWidth = item.x2 - item.x1
                val maxHeight = item.y2 - item.y1

                // If this is the first time we're handling a button, compute the font size. We assume all buttons
                // are the same size, so don't mix and match the sizes!
                if (fontSize < 0) {
                    // Compute the font size for a label XXXX XXXX that fits within one of the rectangle buttons
                    fontSize = 1.0f
                    val testBounds = Rect()
                    while (true) {
                        paint.textSize = fontSize
                        paint.getTextBounds("XXXX", 0, "XXXX".length, testBounds)
                        // Check that we can fit two rows of text in with *2.0f
                        // Also, only use 75% of the max width, and 60% of the max height, do not use the whole button area
                        if ((bounds.width() < maxWidth * 0.75f) && (bounds.height()*2.0f < maxHeight * 0.6f)) {
                            fontSize += 0.5f
                            bounds = testBounds
                        } else {
                            break
                        }
                    }
                    // We now have fontSize set to the largest value possible
                    Log.d(Const.TAG, "Found font size ${fontSize} for CDU key overlay with height ${maxHeight}")
                }
                paint.textSize = fontSize

                fun centerText(text: String, x: Float, y: Float, paint: Paint, canvas: Canvas) {
                    val width = paint.measureText(text)
                    canvas.drawText(text, x - width/2.0f, y, paint)
                }

                // Deal with either one or two rows of text, but not more
                val lines = item.label.split(' ') // Split on spaces or newlines

                val yCenter = (item.y1 + maxHeight/2.0f)
                // paint.ascent() goes slightly higher than all-caps text, and paint.descent() goes slightly lower.
                // They are not exactly equal, but combining them gives the best vertical centering I've seen so far.
                val yHeight = paint.ascent() + paint.descent()

                if (lines.size == 1) {
                    centerText(lines[0], item.x1 + maxWidth/2.0f, (yCenter - yHeight/2.0f), paint, overlayCanvas)
                } else if (lines.size == 2) {
                    centerText(lines[0], item.x1 + maxWidth/2.0f, (yCenter - yHeight/2.0f) + yHeight/1.5f, paint, overlayCanvas)
                    centerText(lines[1], item.x1 + maxWidth/2.0f, (yCenter - yHeight/2.0f) - yHeight/1.5f, paint, overlayCanvas)
                } else {
                    Log.e(Const.TAG, "Found ${lines.size} in string [${item.label}] instead of expected 1 or 2 split on space")
                }
            }
        }

        for ((_, item) in Definitions.buttons) {
            // Illuminate the exec light if active
            if (item.label == "EXEC") {
                if (item.illuminate) {
                    val paint = Paint()
                    paint.color = Color.YELLOW
                    paint.style = Paint.Style.FILL
                    overlayCanvas.drawRect(item.x1.toFloat(), item.y1.toFloat(), item.x2.toFloat()+1, item.y2.toFloat()+1, paint)
                }
            } else if (item.label in arrayOf("MSG", "OFST", "DSPYFAIL")) {
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
        }

        // Draw the key outlines if they are active
        if (overlayOutlines) {
            val paint = Paint()
            paint.color = Color.RED

            drawBox(overlayCanvas, Definitions.displayXLeft, Definitions.displayYTop, Definitions.displayXRight, Definitions.displayYBottom, paint)

            for (entry in Definitions.buttons) {
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
        doBgThread {
            if ((tcpRef != null) && (tcpRef == tcp_extplane) && connectWorking) {
                tcpRef.writeln("cmd once $cmnd")
            } else {
                Log.d(Const.TAG, "Ignoring command $cmnd since TCP connection is not available")
            }
        }
    }

    private fun setDataref(tcpRef: TCPClient?, dataref: String, value: Float) {
        // Send the request on a separate thread
        doBgThread {
            if ((tcpRef != null) && (tcpRef == tcp_extplane) && connectWorking) {
                tcpRef.writeln("set $dataref $value")
            } else {
                Log.d(Const.TAG, "Ignoring set $dataref $value since TCP connection is not available")
            }
        }
    }

    fun padStringCW(str: String = "", brackets: Boolean = false): String {
        val limit = if(brackets) (Definitions.numColumns-2) else (Definitions.numColumns)
        if (brackets)
            return String.format("<%-${limit}s>", str)
        else
            return String.format("%-${limit}s", str)
    }

    fun centerStringCW(str: String = "", brackets: Boolean = false): String {
        val limit = if(brackets) (Definitions.numColumns-2) else (Definitions.numColumns)
        var spaces = limit - str.length
        if (spaces < 0) // If string is too long, don't center it. acf_descrip could cause this if too long.
            spaces = 0
        val left = spaces / 2
        val right = spaces - left
        if (brackets)
            return "<" + " ".repeat(left) + str + " ".repeat(right) + ">"
        else
            return " ".repeat(left) + str + " ".repeat(right)
    }

    fun resetDisplayDebug() {
        for (entry in Definitions.lines) {
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
        for (entry in Definitions.lines) {
            val tv = entry.value.getTextView(this)
            if (entry.value.inverse)      tv.setText(padStringCW(brackets=false))
            else if (entry.value.green)   tv.setText(padStringCW(brackets=false))
            else if (entry.value.magenta) tv.setText(padStringCW(brackets=false))
            else if (entry.value.small)   tv.setText(padStringCW(brackets=false))
            else if (entry.value.label)   tv.setText(padStringCW(brackets=true))
            else                          tv.setText(padStringCW(brackets=true))
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

        // Start up our background processing thread
        backgroundThread = HandlerThread("BackgroundThread")
        backgroundThread!!.start()

        // Retrieve the manual address from shared preferences
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val prefAddress = sharedPref.getString("manual_address", "")
        Log.d(Const.TAG, "Found preferences value for manual_address = [$prefAddress]")

        // Pass on whatever this string is, and will end up calling restartNetworking()
        changeManualHostname(prefAddress)
    }

    private fun setConnectionStatus(line1: String, line2: String, fixup: String, dest: String? = null, redraw: Boolean = true) {
        Log.d(Const.TAG, "Changing connection status to [$line1][$line2][$fixup] with destination [$dest]")
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

        // Do not redraw the CDU if the text area is currently in-use
        if (redraw) {
            terminalTextGreen0.setText(centerStringCW("XPlaneCDU", brackets = true))

            terminalLabel1.setText(centerStringCW("For Zibo738 & SSG748", brackets = true))

            terminalLabel2.setText(centerStringCW(line1, brackets = true))
            terminalTextLarge2.setText(centerStringCW(line2, brackets = true))
            if (connectFailures > 0)
                terminalLabel3.setText(centerStringCW("Error #$connectFailures", brackets = true))
            else
                terminalLabel3.setText(centerStringCW("", brackets = true))
            terminalTextMagenta3.setText(centerStringCW(fixup, brackets = true))
            terminalLabel4.setText(centerStringCW("Tap screws for help", brackets = true))

            terminalLabel5.setText(centerStringCW("v" + Const.getBuildVersion() + " Build " + Const.getBuildId(), brackets = true))
            terminalTextLarge5.setText(centerStringCW(Const.getBuildDateTime(), brackets = true))

            terminalTextLarge7.setText(centerStringCW("Tap screen to set IP", brackets = true))
        }
    }

    private fun restartNetworking() {
        Log.d(Const.TAG, "restartNetworking()")
        resetDisplay()
        setConnectionStatus("Closing down network", "", "Wait a few seconds")
        connectAddress = null
        connectWorking = false
        connectSupported = false
        connectActiveDescrip = ""
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
                setConnectionStatus("Manual TCP connect", "Find ExtPlane plugin", "Check Win firewall", "$connectAddress:${Const.TCP_EXTPLANE_PORT}")
                tcp_extplane = TCPClient(manualInetAddress!!, Const.TCP_EXTPLANE_PORT, this)
            }
        }
    }

    override fun onPause() {
        Log.d(Const.TAG, "onPause()")
        super.onPause()
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
        backgroundThread!!.quit()
        backgroundThread = null
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
        setConnectionStatus("Found BECN multicast", "Find ExtPlane plugin", "Check Win firewall", source.getHostAddress())
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

            // Make requests for aircraft type messages so we can detect when a supported aircraft is available,
            // the datarefs do not exist until the aircraft is loaded and in use
            doBgThread {
                tcpRef.writeln("sub sim/aircraft/view/acf_descrip")
            }
        } else {
            // Log.d(Const.TAG, "Received TCP line [$line]")
            if (!connectWorking) {
                check(!connectSupported) { "connectSupported should not be set if connectWorking is not set" }
                // Everything is working with actual data coming back.
                // This is the last time we can put debug text on the CDU before it is overwritten
                connectFailures = 0
                setConnectionStatus("X-Plane CDU starting", "Waiting acf_descrip", "Must be Zibo/SSG", "$connectAddress:${Const.TCP_EXTPLANE_PORT}")
                connectWorking = true
            }

            val tokens = line.split(" ")
            if (tokens[0] == "ub") {
                val decoded = String(Base64.decode(tokens[2], Base64.DEFAULT))
                // Zibo: Replace ` with degree symbol, and * with a diamond symbol (there is no box in Android fonts)
                // SSG: Uses "[]" to represent the box character so we need to remap this to a diamond symbol
                var fixed = decoded.replace('`','\u00B0').replace('*','\u25CA').replace("[]","\u25CA")

                // SSG: Contains a bug where it adds a space to the end of a line of hyphens, need to remove that space.
                // This happens in many places, this is the smallest string that prevents the problem everywhere.
                fixed = fixed.replace("--------- ", "---------")
                // SSG: There are 4 extra spaces here which should not be there
                fixed = fixed.replace("STEP       Opt", "STEP   Opt")
                // SSG: There is an extra space at the end in all of these
                fixed = fixed.replace("-----------------DATA LINK ", "-----------------DATA LINK")
                fixed = fixed.replace("--------------------Preflt ", "--------------------Preflt")

                Log.d(Const.TAG, "Decoded byte array with [$fixed]=${fixed.length} for name [${tokens[1]}]")
                val lineEntry = Definitions.lines[tokens[1]]
                if (lineEntry == null) {
                    // We have received a change in acf_descrip. If we have never seen any aircraft before, then start
                    // the subscriptions if it is either Zibo or SSG. If we have seen a previous aircraft, then reset
                    // the network and UI to start fresh.
                    if (tokens[1] == "sim/aircraft/view/acf_descrip") {
                        if (connectActiveDescrip == "") {
                            // No previous aircraft during this connection
                            connectActiveDescrip = decoded

                            // The aircraft description has actually changed from before, look for one of our supported aircraft
                            // Laminar 737 is "Boeing 737-800" and Laminar 747 is "B747-400" or "747", so need to avoid matching these
                            val ZIBO738_DESCRIP = "Boeing 737-800X"
                            val ULTZ739_DESCRIP = "Boeing 737-900UX"
                            val SSG748I_DESCRIP = "SSG Boeing 748-i"
                            val SSG748F_DESCRIP = "SSG  Boeing 748 - Freighter" // Two spaces is a typo in the SSG aircraft
                            if (decoded.contains(ZIBO738_DESCRIP)
                                || decoded.contains(ULTZ739_DESCRIP)
                                || decoded.contains(SSG748I_DESCRIP)
                                || decoded.contains(SSG748F_DESCRIP))
                            {
                                setConnectionStatus("X-Plane CDU starting", "Sub: ${connectActiveDescrip}", "Check latest plugin", "$connectAddress:${Const.TCP_EXTPLANE_PORT}")

                                // The aircraft has changed to a supported aircraft, so start the subscription process
                                if (decoded.contains(ZIBO738_DESCRIP) || decoded.contains(ULTZ739_DESCRIP)) {
                                    Log.d(Const.TAG, "Sending subscriptions for Zibo 738 or Ultimate 739 datarefs now that it is detected")
                                    Definitions.setAircraft(Definitions.Aircraft.ZIBO)
                                } else if (decoded.contains(SSG748I_DESCRIP) || decoded.contains(SSG748F_DESCRIP)) {
                                    Log.d(Const.TAG, "Sending subscriptions for SSG 748 I/F datarefs now that it is detected")
                                    Definitions.setAircraft(Definitions.Aircraft.SSG)
                                }

                                // Force the layout to be redone, since the SSG has more columns of text than the Zibo
                                Log.d(Const.TAG, "Forcing layout to be redone, since the aircraft have different text columns")
                                changeCduImageColumns(Definitions.numColumns)
                                resetDisplay() // Set the strings to the default values
                                layoutCduImage() // Now redo the layout based on the changes

                                // The SSG has some unused CDU lines compared to the Zibo, so clear them out as-if a dataref had arrived to do this
                                for (entry in Definitions.lines) {
                                    // Log.d(Const.TAG, "Requesting CDU text key=" + entry.key + " value=" + entry.value.description)
                                    if (entry.key.startsWith("--UNUSED--")) {
                                        // Set the TextView to a blank string because we will never get an update for it
                                        // Must be done in the UI Thread and not within doBgThread below
                                        Log.d(Const.TAG, "Clearing out the text for [${entry.value.description}]")
                                        val view = entry.value.getTextView(this)
                                        view.text = padStringCW("")
                                    } else if (entry.key == "SSG/UFMC/LINE_14") {
                                        // LINE_14 is the text entry area, and is not initialized until one char is entered. Need to clear this just in case.
                                        Log.d(Const.TAG, "Clearing out the text for possibly uninitialized [${entry.value.description}]")
                                        val view = entry.value.getTextView(this)
                                        view.text = padStringCW("")
                                    }
                                }

                                doBgThread {
                                    for (entry in Definitions.lines) {
                                        // Log.d(Const.TAG, "Requesting CDU text key=" + entry.key + " value=" + entry.value.description)
                                        if (entry.key.startsWith("--UNUSED--")) {
                                            // Was handled earlier
                                        } else {
                                            tcpRef.writeln("sub " + entry.key)
                                        }
                                    }
                                    for (entry in Definitions.buttons) {
                                        if (entry.value.light) {
                                            Log.d(Const.TAG, "Requesting illuminated status key=" + entry.key + " value=" + entry.value.description)
                                            tcpRef.writeln("sub " + entry.key)
                                        } else if (entry.value.dataref) {
                                            // SSG requires us to listen on a dataref otherwise the set commands later on won't work
                                            Log.d(Const.TAG, "Requesting button dataref status key=" + entry.key + " value=" + entry.value.description)
                                            tcpRef.writeln("sub " + entry.key)
                                        }
                                    }
                                }
                            } else {
                                // acf_descrip contains an aircraft which we don't support
                                setConnectionStatus("X-Plane CDU failed", "Invalid ${connectActiveDescrip}", "Must be Zibo/SSG", "$connectAddress:${Const.TCP_EXTPLANE_PORT}")
                            }
                        } else if (connectActiveDescrip == decoded) {
                            // acf_descrip was sent to us with the same value. This can happen if a second device connects
                            // via ExtPlane, and it updates all listeners with the latest value. We can safely ignore this.
                            Log.d(Const.TAG, "Detected aircraft update which is the same [$connectActiveDescrip], but ignoring since nothing has changed")
                        } else {
                            // Currently handling another aircraft, so reset everything to keep the restart sequence simple
                            Log.d(Const.TAG, "Detected aircraft change from [$connectActiveDescrip] to [$decoded], so resetting display and connection")
                            resetDisplay()
                            restartNetworking()
                        }
                    } else {
                        Log.d(Const.TAG, "Found unused result name [${tokens[1]}] with string [$fixed]")
                    }
                } else {
                    val view = lineEntry.getTextView(this)
                    // Always pad the chars so the terminal is always ready to be re-laid out
                    view.text = padStringCW(fixed)
                    // If this is the first time we found a supported CDU dataref, then update the UI, this is the final step
                    // and we know everything is working. Do not refresh the entire CDU because we already have text on there.
                    if (!connectSupported) {
                        setConnectionStatus("X-Plane CDU working", "${connectActiveDescrip}", "", "$connectAddress:${Const.TCP_EXTPLANE_PORT}", redraw = false)
                        connectSupported = true
                    }
                }
            } else if ((tokens[0] == "ud") || (tokens[0] == "uf")) {
                val number = tokens[2].toFloat()
                // Log.d(Const.TAG, "Decoded number for name [${tokens[1]}] with value [$number]")
                val entry = Definitions.buttons[tokens[1]]
                if (entry == null) {
                    Log.d(Const.TAG, "Found non-CDU result name [${tokens[1]}] with value [$number]")
                } else {
                    if (entry.light) {
                        entry.illuminate = (number > 0.5f)
                        refreshOverlay()
                    } else {
                        // Log.d(Const.TAG, "Found non-light name [${tokens[1]}] with value [$number]")
                    }
                }
            } else {
                Log.e(Const.TAG, "Unknown encoding type [${tokens[0]}] for name [${tokens[1]}]")
            }
        }
    }
}
