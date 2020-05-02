package com.bckps7336.chunicontrol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ServerThread extends Thread {
    Boolean flag = true;

    DatagramSocket socket;
    ClientThread.ClientCallback clientCallback;

    public ServerThread(ClientThread.ClientCallback clientCallback, DatagramSocket socket) {
        super();
        this.clientCallback = clientCallback;
        this.socket = socket;
    }

    public synchronized void stopIt() {
        flag = false;
    }

    @Override
    public void run() {
        while (flag) {
            try {
                byte[] data = new byte[6];
                DatagramPacket packet = new DatagramPacket(data, data.length);
                socket.receive(packet);
                byte[] data1 = packet.getData();
                clientCallback.handle(data1);
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }
}
