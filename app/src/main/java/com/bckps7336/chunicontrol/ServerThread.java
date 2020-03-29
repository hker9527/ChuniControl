package com.bckps7336.chunicontrol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ServerThread extends Thread {
    DatagramSocket socket;
    MainActivity.ServerHandler serverHandler;

    public ServerThread(MainActivity.ServerHandler handler, DatagramSocket socket) {
        super();
        this.serverHandler = handler;
        this.socket = socket;
    }

    @Override
    public void run() {
        while (true) {
            try {
                byte[] data = new byte[6];
                DatagramPacket packet = new DatagramPacket(data, data.length);
                socket.receive(packet);
                serverHandler.handle(packet.getData());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
