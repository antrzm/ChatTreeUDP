import java.awt.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/*This thread has Node params */

class Node extends Thread {
    private String name;
    private int loose = 0;
    private int port;
    private String parentIP = null;
    private int parentPort = -1;
    private DatagramSocket socket;
    private Receiver receiver;


    Node(String name, int loose, int port) {
        this.name  = name;
        this.loose = loose;
        this.port  = port;
    }

    void setParentIP(String IP) {
        this.parentIP = IP;
    }

    void setParentPort(int port) {
        this.parentPort = port;
    }

    public void run() {
        try {
            socket = new DatagramSocket();
            if (parentIP != null)
                sendHello();
            receiver = new Receiver(port);
            receiver.start();
            //TODO: send
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendHello() throws IOException {
        String str = "hello from " + name;
        byte[] helloMsg = str.getBytes();
        byte[] dataLen = ByteBuffer.allocate(Integer.BYTES).putInt(helloMsg.length).array();
        ByteBuffer bb = ByteBuffer.allocate(helloMsg.length + dataLen.length).put(dataLen).put(helloMsg);
        InetAddress address = InetAddress.getByName(parentIP);
        DatagramPacket packet = new DatagramPacket(bb.array(), helloMsg.length + dataLen.length, address, parentPort);
        socket.send(packet);
    }
}
