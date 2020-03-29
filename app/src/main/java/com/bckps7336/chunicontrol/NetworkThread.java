package com.bckps7336.chunicontrol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class NetworkThread extends Thread {
    InetAddress address;
    int port;

    DatagramSocket socket;

    //List<DatagramPacket> queue;
    DatagramPacket packet;

    boolean connected;

    public NetworkThread(MainActivity.ServerHandler handler, String host) {
        super();
        try {
            address = InetAddress.getByName(host);
            port = 24864;
            socket = new DatagramSocket();
            connected = true;

            ServerThread serverThread = new ServerThread(handler, socket);
            serverThread.start();
        } catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
            connected = false;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    private void send(byte[] bytes) {
        while (packet != null) ;// Ensure the packet queued get sent before the new packet
        // Log.d("packet", Hex.encodeHexString(bytes));
        packet = new DatagramPacket(bytes, bytes.length, address, port);
    }

    public void sendAir(boolean isPressed) {
        send(new byte[]{0x1, (byte) (isPressed ? 0x6 : 0x7), 0x4, 0x0, 0x0, 0x0});
    }

    public void sendKey(boolean isPressed, int key) { // key: 0-15, but protocol counts first key as F
        /*for (int i = 0; i < 2; i++) {
            send(new byte[]{0x1, (byte) (isPressed ? 0x1 : 0x2), (byte) (0xf - (key * 2 + i)), 0x0, 0x0, 0x0});
        }*/
        send(new byte[]{0x1, (byte) (isPressed ? 0x1 : 0x2), (byte) (0xf - key), 0x0, 0x0, 0x0});
    }

    public void sendTest() {
        send(new byte[]{0x1, 0x4, 0x0, 0x0, 0x0, 0x0});
    }

    public void sendService() {
        send(new byte[]{0x1, 0x5, 0x0, 0x0, 0x0, 0x0});
    }

    public void sendCoin() {
        send(new byte[]{0x1, 0x0, 0x0, 0x0, 0x0, 0x0});
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (packet != null) {
                    DatagramPacket _packet = new DatagramPacket(packet.getData(), packet.getLength());
                    socket.send(packet);
                    packet = null;
                }
                Thread.sleep(0, 1); // ASAP
            }
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
