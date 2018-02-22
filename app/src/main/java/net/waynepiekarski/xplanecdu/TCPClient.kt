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

import android.util.Log

import java.net.*
import kotlin.concurrent.thread
import java.io.*


class TCPClient (private var address: InetAddress, private var port: Int, private var callback: OnTCPEvent) {
    private lateinit var socket: Socket
    @Volatile private var cancelled = false
    private lateinit var bufferedWriter: BufferedWriter
    private lateinit var bufferedReader: BufferedReader
    private lateinit var inputStreamReader: InputStreamReader
    private lateinit var outputStreamWriter: OutputStreamWriter

    interface OnTCPEvent {
        fun onReceiveTCP(line: String, tcpRef: TCPClient)
        fun onConnectTCP(tcpRef: TCPClient)
        fun onDisconnectTCP(tcpRef: TCPClient)
    }

    fun stopListener() {
        // Stop the loop from running any more
        cancelled = true

        // Call close on the top level buffers to cause any pending read to fail, ending the loop
        closeBuffers()

        // The socketThread loop will now clean up everything
    }

    fun writeln(str: String) {
        if (cancelled) {
            Log.d(Const.TAG, "Skipping write to cancelled socket: [$str]")
            return
        }
        Log.d(Const.TAG, "Writing to TCP socket: [$str]")
        try {
            bufferedWriter.write(str + "\n")
            bufferedWriter.flush()
        } catch (e: IOException) {
            Log.d(Const.TAG, "Failed to write [$str] to TCP socket with exception $e")
            stopListener()
        }
    }

    private fun closeBuffers() {
        // Call close on the top level buffers which will propagate to the original socket
        // and cause any pending reads and writes to fail
        if (::bufferedWriter.isInitialized) {
            try {
                Log.d(Const.TAG, "Closing bufferedWriter")
                bufferedWriter.close()
            } catch (e: IOException) {
                Log.d(Const.TAG, "Closing bufferedWriter in stopListener caused IOException, this is probably ok")
            }
        }
        if (::bufferedReader.isInitialized) {
            try {
                Log.d(Const.TAG, "Closing bufferedReader")
                bufferedReader.close()
            } catch (e: IOException) {
                Log.d(Const.TAG, "Closing bufferedReader in stopListener caused IOException, this is probably ok")
            }
        }
    }

    // In a separate function so we can "return" any time to bail out
    private fun socketThread() {
        try {
            socket = Socket(address, port)
        } catch (e: Exception) {
            Log.e(Const.TAG, "Failed to connect to $address:$port with exception $e")
            MainActivity.doUiThread { callback.onDisconnectTCP(this) }
            return
        }

        // Wrap the socket up so we can work with it - no exceptions should be thrown here
        try {
            inputStreamReader = InputStreamReader(socket.getInputStream())
            bufferedReader = BufferedReader(inputStreamReader)
            outputStreamWriter = OutputStreamWriter(socket.getOutputStream())
            bufferedWriter = BufferedWriter(outputStreamWriter)
        } catch (e: IOException) {
            Log.e(Const.TAG, "Exception while opening socket buffers $e")
            closeBuffers()
            MainActivity.doUiThread { callback.onDisconnectTCP(this) }
            return
        }

        // Connection should be established, everything is ready to read and write
        MainActivity.doUiThread { callback.onConnectTCP(this) }

        // Start reading from the socket, any writes happen from another thread
        while (!cancelled) {
            var line: String?
            try {
                line = bufferedReader.readLine()
            } catch (e: IOException) {
                Log.d(Const.TAG, "Exception during socket readLine $e")
                line = null
            }
            if (line == null) {
                Log.d(Const.TAG, "readLine returned null, connection has failed")
                cancelled = true
            } else {
                Log.d(Const.TAG, "TCP returned line [$line]")
                MainActivity.doUiThread { callback.onReceiveTCP(line, this) }
            }
        }

        // Close any outer buffers we own, which will propagate to the original socket
        closeBuffers()

        // The connection is gone, tell the listener in case they need to update the UI
        MainActivity.doUiThread { callback.onDisconnectTCP(this) }
    }

    // Constructor starts a new thread to handle the blocking outbound connection
    init {
        Log.d(Const.TAG, "Created thread to connect to $address:$port")
        thread(start = true) {
            socketThread()
        }
    }
}
