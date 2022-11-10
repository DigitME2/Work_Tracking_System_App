/*
Copyright 2022 DigitME2

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/



// adapted from the OEE server & Fully Copied from Inventory Tracker

package com.admt.barcodereader;
import android.content.Context;
import android.os.StrictMode;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.Enumeration;

public class ServerDiscovery {
    public static class DiscoveryResult{
        public String ipAddress;
        public String port;
        public String protocol;
        public String serverBaseAddress;

        public DiscoveryResult(String Protocol, String IpAddress, String Port)
        {
            ipAddress = IpAddress;
            port = Port;
            protocol = Protocol;
            serverBaseAddress = String.format("%s:/%s:%s", protocol, ipAddress, port);
        }
    }

    private static final int DISCOVERY_TIMEOUT_MS = 10000;
    private static final String DISCOVERY_REQUEST_MESSAGE = "DISCOVER_PTT_SERVER_REQUEST";
    private static final String DISCOVERY_RESPONSE_MESSAGE = "DISCOVER_PTT_SERVER_RESPONSE";
    private static final String TAG = "ServerDiscovery";

    public static ServerDiscovery.DiscoveryResult findServer(Context context) {
        //Find the server using UDP broadcast
        //DbHelper dbHelper = new DbHelper(context);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        try {
            //Open a random port to send the package
            DatagramSocket datagramSocket = new DatagramSocket();
            datagramSocket.setBroadcast(true);
            datagramSocket.setSoTimeout(DISCOVERY_TIMEOUT_MS);

            byte[] sendData = DISCOVERY_REQUEST_MESSAGE.getBytes();

            //Broadcast the message all over the network interfaces
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;   //Don't want to broadcast to the loopback interface
                }

                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast == null) {
                        continue;
                    }
                    //Send the broadcast packet
                    try {
                        DatagramPacket sendPacket = new DatagramPacket(
                                sendData,
                                sendData.length,
                                broadcast,
                                8093);
                        datagramSocket.send(sendPacket);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                    Log.v(TAG,
                            "Request packet sent to: " +
                                    broadcast.getHostAddress() +
                                    "; Interface: " +
                                    networkInterface.getDisplayName());
                }
            }

            Log.v(TAG, "Done looping over network interfaces. Waiting for a reply");

            //Wait for a response
            byte[] receiveBuf = new byte[15000];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
            //Show a dialog if the connection times out
            try {
                datagramSocket.receive(receivePacket);
            } catch (SocketTimeoutException e) {

                Log.v(TAG, "Socket timeout. Exiting...");
                datagramSocket.close();
                return null;
            }
            //We have a response
            Log.v(TAG, "Broadcast response from server: " + receivePacket.getAddress().getHostAddress());

            //Check if the message is correct
            String message = new String(receivePacket.getData()).trim();
            String[] split = message.split(":",3); // response is message:protocol:port
            if (split[0].equals(DISCOVERY_RESPONSE_MESSAGE)) {
                ServerDiscovery.DiscoveryResult discoveryResult = new DiscoveryResult(
                        split[1],
                        receivePacket.getAddress().toString(),
                        split[2]
                );
                Log.v(TAG, "Server base address is " + discoveryResult.serverBaseAddress);
                return discoveryResult;
            }

            datagramSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }
}