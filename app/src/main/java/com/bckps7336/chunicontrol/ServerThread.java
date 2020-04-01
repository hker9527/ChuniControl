package com.bckps7336.chunicontrol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ServerThread extends Thread {
    DatagramSocket socket;
    MainActivity.MainCallback mainCallback;
    NetworkThread.ClientCallback clientCallback;

    public ServerThread(MainActivity.MainCallback mainCallback, NetworkThread.ClientCallback clientCallback, DatagramSocket socket) {
        super();
        this.mainCallback = mainCallback;
        this.clientCallback = clientCallback;
        this.socket = socket;
    }

    @Override
    public void run() {
        while (true) {
            try {
                byte[] data = new byte[6];
                DatagramPacket packet = new DatagramPacket(data, data.length);
                socket.receive(packet);
                byte[] data1 = packet.getData();
                mainCallback.handle(data1);
                clientCallback.handle(data1);
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
