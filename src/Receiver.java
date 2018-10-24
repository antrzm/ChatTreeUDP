import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;


/*This thread receives message and sends them to child node (if exists) and to parent node (if exists)*/
/*Message is either received or ignored based depending on loose percent and random number generated  */
/*When msg is received ACK-package is sent back*/
/*PROTOCOL:
 * type:      cmd:   hash:   data:
 * hello msg     0   data    IP & port
 * ACK           1   data    hash of client
 * regular msg   2   data    length + text
 * bb msg        3   data    IP & port
 * */

public class Receiver extends Thread {
    private static final int CMD_HELLO = 0;
    private static final int CMD_ACK = 1;
    private static final int CMD_REG = 2;
    private static final int PACK_SIZE = 2048;
    private byte data[] = new byte[PACK_SIZE];
    private DatagramPacket packet;
    private DatagramSocket socket;
    private Map<String, Integer> kidsAddrs;
    private int loose;
    private String myIP;
    private int myPort;
    private String parentIP;
    private int parentPort;
    private Map<Integer, MyPackage> messages;

    private Thread ACKThread;


    Receiver(int loose, String IP, int port, String parentIP, int parentPort) throws SocketException {
        packet = new DatagramPacket(data, PACK_SIZE);
        socket = new DatagramSocket(port);
        myIP = IP;
        myPort = port;
        this.loose = loose;
        this.parentIP = parentIP;
        this.parentPort = parentPort;
        kidsAddrs = new HashMap<>();
        messages = new HashMap<>();
    }

    public void run() {
        Random random = new Random(System.currentTimeMillis());
        Thread ACKThread = new Thread() {
            public void run() {
                try {
                    sleep(3000);
                    if (!messages.isEmpty())
                        for (Object obj : messages.entrySet()) {
                            Map.Entry pair = (Map.Entry) obj;
                            MyPackage pack = (MyPackage) pair.getValue();
                            sendMsg(String.valueOf(pack.packet.getAddress()), pack.packet.getPort(), pack);
                        }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        };
        ACKThread.start();
        while (true) {
            try {
                socket.receive(packet);
                int rndNum = random.nextInt(100 + 1);
                if (loose > rndNum) {                           //imitating message lost
                    if (data.length != 0) {
                        int offset = 0;
                        ByteBuffer bb = ByteBuffer.wrap(data);
                        int cmd = bb.getInt();
                        offset += Integer.BYTES;

                        int hash = bb.getInt();
                        offset += Integer.BYTES;

                        int IPsz = bb.getInt();
                        offset += Integer.BYTES;

                        ByteArrayInputStream bais = new ByteArrayInputStream(data, offset, IPsz);
                        byte[] IPbytes = new byte[IPsz];
                        bais.read(IPbytes, 0, IPsz);
                        String IP = new String(IPbytes);
                        offset += IPsz;
                        System.out.println("IP is " + IP);

                        bb = ByteBuffer.wrap(data, offset, Integer.BYTES);
                        int port = bb.getInt();
                        offset += Integer.BYTES;
                        System.out.println("port is " + port);

                        bb = ByteBuffer.wrap(data, offset, Integer.BYTES);
                        int msgLenSize = bb.getInt();
                        offset += Integer.BYTES;

                        bais = new ByteArrayInputStream(data, offset, msgLenSize);
                        byte[] msgBytes = new byte[msgLenSize];
                        bais.read(msgBytes, 0, msgLenSize);
                        String msg = new String(msgBytes);
                        System.out.println(msg);

                        switch (cmd) {
                            case CMD_HELLO: {
                                if (!isChild(IP, port)) addChild(IP, port);
                                MyPackage ACKpack = new MyPackage(CMD_ACK, String.valueOf(hash), myIP, myPort);
                                sendMsg(IP, port, ACKpack);
                            }
                            case CMD_ACK: {
                                messages.remove(hash);
                            }
                            case CMD_REG: {
                                if (isChild(IP, port) || isParent(IP, port)) {
                                    MyPackage ACKpack = new MyPackage(CMD_ACK, String.valueOf(hash), myIP, myPort);
                                    sendMsg(IP, port, ACKpack);
                                    MyPackage pack = new MyPackage(CMD_REG, msg, myIP, myPort);
                                    if (!isParent(IP, port)) sendToParent(pack);
                                    sendToChildren(pack, IP, port);
                                }
                            }
                        }
                    }
                }
            } catch (SocketTimeoutException e) {
                e.printStackTrace();
                removeChild(String.valueOf(packet.getAddress()), packet.getPort());
                System.exit(-1);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
    }

    private boolean isParent(String ip, int port) {
        return ip.equals(parentIP) && port == parentPort;
    }

    void sendToParent(MyPackage pack) throws IOException {
        sendMsg(parentIP, parentPort, pack);
    }

    private void sendMsg(String IP, int port, MyPackage pack) throws IOException {
        DatagramPacket packet = pack.generateDatagramPacket(IP, port);
        socket.send(packet);
        messages.put(pack.hash, pack);
    }

    void sendToChildren(MyPackage pack, String selfIP, int selfPort) throws IOException {
        for (Object obj : kidsAddrs.entrySet()) {
            Map.Entry pair = (Map.Entry) obj;
            if (selfPort == -1) {
                sendMsg((String) pair.getKey(), (Integer) pair.getValue(), pack);
            } else if (!pair.getKey().equals(selfIP) && pair.getValue() != Integer.valueOf(selfPort))
                sendMsg((String) pair.getKey(), (Integer) pair.getValue(), pack);
        }
    }

    private boolean isChild(String IP, int port) {
        for (Object o : kidsAddrs.entrySet()) {
            Map.Entry pair = (Map.Entry) o;
            if (pair.getKey() == IP && pair.getValue() == Integer.valueOf(port))
                return true;
        }
        return false;
    }

    private void addChild(String IP, int port) {
        kidsAddrs.put(IP, port);
    }

    private void removeChild(String IP, int port) {
        kidsAddrs.remove(IP, port);
    }
}