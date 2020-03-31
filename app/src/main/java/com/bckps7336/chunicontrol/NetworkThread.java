package com.bckps7336.chunicontrol;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import tech.gusavila92.apache.commons.codec.binary.Hex;

public class NetworkThread extends Thread {
    InetAddress address;
    int port;

    DatagramSocket socket;
    //List<DatagramPacket> queue;
    DatagramPacket packet;

    ClientCallback clientCallback;
    boolean connected;

    boolean[] bitMask;

    long timeSend;
    long timePacket;
    boolean pingSent, pingReceived;
    int latency;

    public NetworkThread(MainActivity.MainCallback mainCallback, String host) {
        super();
        try {
            address = InetAddress.getByName(host);
            port = 24864;

            clientCallback = new ClientCallback();
            socket = new DatagramSocket();

            ServerThread serverThread = new ServerThread(mainCallback, clientCallback, socket);
            serverThread.start();

            sendPing();

            bitMask = new boolean[32];
        } catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
            connected = false;
        }
    }

    public int getLatency() {
        return latency;
    }

    public boolean isConnected() {
        return connected;
    }

    private void _send(byte[] bytes) {
        while (packet != null) ;// Ensure the packet queued get sent before the new packet
        // Log.d("packet", Hex.encodeHexString(bytes));
        packet = new DatagramPacket(bytes, bytes.length, address, port);
    }

    private void send(int action) {
        _send(new byte[]{
                Constant.SRC_CLIENT,
                (byte) action,
                0x0, 0x0, 0x0, 0x0
        });
    }

    private void sendBitmask() {
        byte[] bytes = new byte[]{
                Constant.SRC_CLIENT,
                Constant.TYPE_BITMASK,
                0x0, 0x0, 0x0, 0x0
        };
        for (int i = 0; i < 4; i++) {
            int num = 0;
            for (int j = 0; j < 8; j++) {
                num = (num << 1) | (bitMask[8 * i + j] ? 1 : 0);
            }
            bytes[2 + i] = (byte) num;
        }
        Log.d("packet", Hex.encodeHexString(bytes));
        _send(bytes);
    }

    public void sendAir(boolean isPressed) {
        // send(new byte[]{0x1, (byte) (isPressed ? 0x6 : 0x7), 0x4, 0x0, 0x0, 0x0});
        bitMask[16 + 4] = isPressed;
        sendBitmask();
    }

    public void sendKey(boolean isPressed, int key) { // key: 0-15, but protocol counts first key as F
        // send(new byte[]{0x1, (byte) (isPressed ? 0x1 : 0x2), (byte) (0xf - key), 0x0, 0x0, 0x0});
        bitMask[0xf - (key * 2)] = isPressed;
        bitMask[0xf - (key * 2) - 1] = isPressed;
        sendBitmask();
    }

    public void sendTest() {
        send(Constant.TYPE_CABINET_TEST);
    }

    public void sendService() {
        send(Constant.TYPE_CABINET_SERVICE);
    }

    public void sendCoin() {
        send(Constant.TYPE_COIN_INSERT);
    }

    public void sendShutdown() {
        send(Constant.TYPE_SHUTDOWN);
    }

    public void sendPing() {
        pingReceived = false;
        timeSend = System.currentTimeMillis();
        send(Constant.TYPE_PING);
    }

    @Override
    public void run() {
        try {
            while (true) {
                long currentTime = System.currentTimeMillis();
                if (packet != null) {
                    socket.send(packet);
                    packet = null;
                    timePacket = currentTime;
                }

                if (!pingSent && (currentTime - timePacket >= 1000)) { // Check ping every 10 second of inactivity
                    sendPing();
                    timePacket = currentTime;
                }

                if (pingSent && !pingReceived && (currentTime - timeSend > 1000)) { // If sent ping and 1s passed
                    connected = false;
                    pingSent = false;
                    break;
                }
                Thread.sleep(0, 1); // ASAP
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public class ClientCallback {
        public ClientCallback() {
            super();
        }

        public void handle(final byte[] data) {
            switch (data[1]) {
                case Constant.TYPE_PONG:
                    latency = (int) (System.currentTimeMillis() - timeSend);
                    pingReceived = true;
                    connected = true;

                    Log.d("ping", String.valueOf(latency));
                    break;
                default:
                    break;
            }
        }
    }
}
