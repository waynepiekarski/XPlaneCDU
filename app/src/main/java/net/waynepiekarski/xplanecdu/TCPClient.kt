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

import android.os.Handler
import android.os.Looper
import android.util.Log

import java.net.*
import kotlin.concurrent.thread
import java.io.*


class TCPClient (address: InetAddress, port: Int, internal var callback: OnReceiveTCP) {
    private lateinit var socket: Socket
    @Volatile private var cancelled = false
    private lateinit var bufferedWriter: BufferedWriter

    interface OnReceiveTCP {
        fun onReceiveTCP(line: String)
    }

    fun stopListener() {
        cancelled = true
    }

    fun writeln(str: String) {
        Log.d(Const.TAG, "Writing to TCP socket: [$str]")
        bufferedWriter.write(str)
        bufferedWriter.write("\n")
        bufferedWriter.flush()
    }

    init {
        Log.d(Const.TAG, "Created thread to listen for $address on port $port")
        thread(start = true) {
            try {
                socket = Socket(address, port)

                val inputStreamReader = InputStreamReader(socket.getInputStream())
                val bufferedReader = BufferedReader(inputStreamReader)
                val outputStreamWriter = OutputStreamWriter(socket.getOutputStream())
                bufferedWriter = BufferedWriter(outputStreamWriter)

                while (!cancelled) {
                    val line = bufferedReader.readLine()
                    Log.d(Const.TAG, "TCP returned line [$line]")
                    Handler(Looper.getMainLooper()).post {
                        callback.onReceiveTCP(line)
                    }
                }
            } catch (e: SocketTimeoutException) {
                // Log.d(Const.TAG, "Timeout, reading again ...");
            } catch (e: IOException) {
                Log.e(Const.TAG, "Socket failed " + e)
            } finally {
                Log.d(Const.TAG, "Thread is cancelled, closing down TCP listener")
                socket.close()
                Log.d(Const.TAG, "TCP listener thread for port $port has ended")
            }
        }
    }
}
