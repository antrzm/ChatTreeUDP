import java.net.DatagramSocket;
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
    private static final int CMD_REG = 2;
    private int loose = 0;
    private int port;
    private String parentIP = null;
    private int parentPort = -1;
    private String myAddress;
    private String name;

    Node(String name, int loose, int port) {
        this.loose = loose;
        this.port  = port;
        this.name = name;
        myAddress  = "127.0.0.1";
    }

    void setParentIP(String IP) {
        this.parentIP = IP;
    }

    void setParentPort(int port) {
        this.parentPort = port;
    }

    public void run() {
        try {
            DatagramSocket socket = new DatagramSocket();
            Receiver receiver = new Receiver(loose, myAddress, port, parentIP, parentPort);
            receiver.start();
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String text = scanner.nextLine();
                if (!text.isEmpty()) {
                    if (text.equals("/userlist")) {
                        receiver.printUsers();
                        continue;
                    }
                    if (text.equals("/list")) {
                        receiver.printMsgs();
                        continue;
                    }
                    if (port != -1) {
                        text = this.name + ": " + text;
                        MyPackage myPack = new MyPackage(CMD_REG, text, myAddress, port);
                        receiver.sendToOthers(myPack, null, -1);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}