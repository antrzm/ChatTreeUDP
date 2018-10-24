import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

/*This thread has Node params such as name, port, parentIP&port*/
/*Thread sends hello msg to parent and other messages to parent and child (if exist)*/
/*PROTOCOL:
 * type:      cmd:   hash:   data:
 * hello msg     0   data    IP & port
 * ACK           1   data    hash of client
 * regular msg   2   data    text
 * bb msg        3   data    IP & port
 * */

class Node extends Thread {
    private static final int CMD_HELLO = 0;
    private static final int CMD_REG = 2;
    private int loose = 0;
    private int port;
    private String parentIP = null;
    private int parentPort = -1;
    private DatagramSocket socket;
    private String myAddress;

    Node(String name, int loose, int port) {
        this.loose = loose;
        this.port = port;
        try {
            myAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
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
            Receiver receiver = new Receiver(loose, myAddress, port, parentIP, parentPort);
            receiver.start();
            if (parentIP != null) sendHello();
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String text = scanner.nextLine();
                if (!text.isEmpty()) {
                    MyPackage myPack = new MyPackage(CMD_REG, text, myAddress, port);
                    if (parentPort != -1) receiver.sendToParent(myPack);
                    receiver.sendToChildren(myPack, null, -1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendHello() throws IOException {
        String text = "Hello from " + myAddress + ' ' + Integer.toString(port);
        MyPackage myPack = new MyPackage(CMD_HELLO, text, myAddress, port);
        DatagramPacket packet = myPack.generateDatagramPacket(parentIP, parentPort);
        socket.send(packet);
    }
}