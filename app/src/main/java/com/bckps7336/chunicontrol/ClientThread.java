package com.bckps7336.chunicontrol;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import tech.gusavila92.apache.commons.codec.binary.Hex;

public class ClientThread extends Thread {
    boolean flag = true;

    InetAddress address;
    int port;

    DatagramSocket socket;
    //List<DatagramPacket> queue;
    DatagramPacket packet;

    final int TIMEOUT = 100;
    ClientCallback clientCallback;
    NetworkCallback networkCallback;

    ServerThread serverThread;

    boolean[] bitMask;
    boolean connected = false;
    long timeSend;
    long timePacket;
    boolean pingSent, pingReceived;
    int ping = -1;

    public ClientThread(NetworkCallback networkCallback, String host) {
        super();
        try {
            address = InetAddress.getByName(host);
            port = 24864;

            clientCallback = new ClientCallback();
            socket = new DatagramSocket();

            this.networkCallback = networkCallback;

            serverThread = new ServerThread(clientCallback, socket);
            serverThread.start();

            sendPing();

            bitMask = new boolean[32];
        } catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
            connected = false;
        }
    }

    public int getPing() {
        return ping;
    }

    public boolean isConnected() {
        if (ping == -1) { // Not yet pinged
            if (!pingSent) sendPing();
            try {
                int count = 0;
                while (count < TIMEOUT) { // Wait 100 ms
                    Thread.sleep(1);
                    count++;
                    if (ping != -1) break;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
        //bitMask[0xf - (key * 2)] = isPressed;
        //bitMask[0xf - (key * 2) - 1] = isPressed;
        bitMask[key] = isPressed;
        bitMask[Math.min(key + 1, 0xf)] = isPressed;
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
        pingSent = true;
        pingReceived = false;
        send(Constant.TYPE_PING);
    }

    public synchronized void stopIt() {
        flag = false;
        serverThread.stopIt();
    }

    @Override
    public void run() {
        try {
            while (flag) {
                long currentTime = System.currentTimeMillis();
                if (packet != null) {
                    timeSend = System.currentTimeMillis();
                    socket.send(packet);
                    packet = null;
                    timePacket = currentTime;
                }

                if (!pingSent && (currentTime - timePacket >= 10000)) { // Check ping every 10 second of inactivity
                    sendPing();
                }

                if (pingSent && !pingReceived && (currentTime - timeSend > TIMEOUT)) { // If sent ping and 0.1s passed
                    Log.d("client", "timeout");
                    connected = false;
                    stopIt();
                    break;
                }
                Thread.sleep(0, 1); // ASAP
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    public class ClientCallback {
        public void handle(final byte[] data) {
            connected = true;
            ping = (int) (System.currentTimeMillis() - timeSend);
            networkCallback.handle(data);
            switch (data[1]) {
                case Constant.TYPE_PONG:
                    pingReceived = true;
                    break;
                default:
                    break;
            }
            Log.d("ping", String.valueOf(ping));
        }
    }
}
