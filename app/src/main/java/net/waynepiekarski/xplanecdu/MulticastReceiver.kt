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

import java.io.IOException
import java.util.Arrays

import java.net.*
import kotlin.concurrent.thread


class MulticastReceiver (address: String, port: Int, internal var callback: OnReceiveMulticast) {
    private lateinit var socket: MulticastSocket
    @Volatile private var cancelled = false
    private var lastAddress: InetAddress? = null

    interface OnReceiveMulticast {
        fun onReceiveMulticast(buffer: ByteArray, source: InetAddress)
    }

    init {
        Log.d(Const.TAG, "Created thread to listen on multicast address $address on port $port")
        thread(start = true) {
            var packetCount = 0
            Log.d(Const.TAG, "Receiving multicast packets for address $address on port $port")
            try {
                socket = MulticastSocket(port)
                // Only block for 1 second before trying again, allows us to check for if cancelled
                socket.soTimeout = 1000
                socket.joinGroup(InetAddress.getByName(address))

                val buffer = ByteArray(64 * 1024) // UDP maximum is 64kb
                val packet = DatagramPacket(buffer, buffer.size)
                while (!cancelled) {
                    // Log.d(Const.TAG, "Waiting for multicast packet on port " + port + " with maximum size " + buffer.size);
                    try {
                        socket.receive(packet)
                        packetCount++
                        // Log.d(Const.TAG, "Received multicast packet with " + packet.length + " bytes of data");
                        // Log.d(Const.TAG, "Hex dump = [" + bytesToHex(packet.getData(), packet.getLength()) + "]");
                        // Log.d(Const.TAG, "Txt dump = [" + UDPReceiver.bytesToChars(packet.getData(), packet.getLength()) + "]");
                        // getHostAddress appears to block and should not be called on the UI thread!
                        val data = Arrays.copyOfRange(buffer, 0, packet.length)
                        if (lastAddress == null || !lastAddress!!.equals(packet.address)) {
                            lastAddress = packet.address
                            Handler(Looper.getMainLooper()).post {
                                callback.onReceiveMulticast(data, packet.address)
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // Log.d(Const.TAG, "Timeout, reading again ...");
                    } catch (e: IOException) {
                        Log.e(Const.TAG, "Failed to read packet " + e)
                    } catch (e: Exception) {
                        Log.e(Const.TAG, "Unknown exception " + e)
                    }
                }
                Log.d(Const.TAG, "Thread is cancelled, closing down multicast listener on port " + port)
                socket.close()
            } catch (e: SocketException) {
                Log.e(Const.TAG, "Failed to open socket " + e)
            }

            Log.d(Const.TAG, "Multicast listener thread for port $port has ended")
        }
    }

    fun stopListener() {
        cancelled = true
    }
}
