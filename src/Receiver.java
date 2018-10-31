import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


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
    private static final int CMD_ACK = 1;
    private static final int CMD_REG = 2;
    private static final int PACK_SIZE = 2048;
    private byte data[] = new byte[PACK_SIZE];
    private DatagramPacket packet;
    private DatagramSocket socket;
    private int loose;
    private String myIP;
    private int myPort;
    private String parentIP;
    private int parentPort;
    private Map<Integer, MyPackage> sentMessages;
    private Map<Integer, Integer> numOfRepeats;
    private Map<User, MyPackage> messages;
    private ArrayList<User> users;

    Receiver(int loose, String IP, int port, String parentIP, int parentPort) throws SocketException {
        packet = new DatagramPacket(data, PACK_SIZE);
        socket = new DatagramSocket(port);
        myIP = IP;
        myPort = port;
        this.loose = loose;
        this.parentIP = parentIP;
        this.parentPort = parentPort;
        sentMessages = new ConcurrentHashMap<>();
        numOfRepeats = new ConcurrentHashMap<>();
        users = new ArrayList<>();
        messages = new ConcurrentHashMap<>();
        if (parentIP != null && parentPort != -1) {
            User parent = new User("user1", parentIP, parentPort);
            users.add(parent);
        }
    }

    public void run() {
        Random random = new Random(System.currentTimeMillis());
        Thread ACKThread = new Thread(() -> {
            while (true) {
                try {
                    sleep(2000);
                    if (!sentMessages.isEmpty())
                        for (Object obj : sentMessages.entrySet()) {
                            Map.Entry pair = (Map.Entry) obj;
                            int hash = (int) pair.getKey();
                            MyPackage pack = (MyPackage) pair.getValue();
                            if (numOfRepeats.containsKey(hash) && numOfRepeats.get(hash) == 3) {    //if sent 3 times
                                if (isKnown(pack.IPtoSend, pack.portToSend)) {
                                    removeUser(pack.IPtoSend, pack.portToSend);
                                    System.out.print("Lost my child " + pack.IPtoSend + ":" + String.valueOf(pack.portToSend));
                                    System.out.println(", my wife gonna kill me");
                                }
                                if (isParent(pack.IPtoSend, pack.portToSend)) {
                                    System.out.println("Daddy, where are you?");
                                    removeUser(parentIP, parentPort);
                                    parentIP = null;
                                    parentPort = -1;
                                }
                                numOfRepeats.remove(hash, numOfRepeats.get(hash));
                                sentMessages.remove(hash);
                            } else {
                                sendMsg(pack.IPtoSend, pack.portToSend, pack);
                                if (numOfRepeats.containsKey(hash)) {
                                    int repeats = numOfRepeats.get(hash);
                                    numOfRepeats.replace(hash, repeats, ++repeats);
                                } else {
                                    numOfRepeats.put(hash, 2);
                                }
                                System.out.println("No ACK" + ": sending pack again");
                            }
                        }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
        ACKThread.start();
        while (true) {
            try {
                socket.receive(packet);
                int rndNum = random.nextInt(100 + 1);
                if (loose > rndNum) {                                        //imitating message lost
                    if (data.length != 0) {
                        User user = null;
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

                        bb = ByteBuffer.wrap(data, offset, Integer.BYTES);
                        int port = bb.getInt();
                        offset += Integer.BYTES;

                        if (!isKnown(IP, port)) {
                            String name = "user" + String.valueOf(users.size() + 1);
                            user = new User(name, IP, port);
                            users.add(user);
                        } else {
                            for (User u : users) {
                                if (u.IP.equals(IP) && u.port == port) {
                                    user = u;
                                    break;
                                }
                            }
                        }

                        if (user != null) {
                            bb = ByteBuffer.wrap(data, offset, Integer.BYTES);
                            int msgLenSize = bb.getInt();
                            offset += Integer.BYTES;

                            bais = new ByteArrayInputStream(data, offset, msgLenSize);
                            byte[] msgBytes = new byte[msgLenSize];
                            bais.read(msgBytes, 0, msgLenSize);
                            String msg = new String(msgBytes);

                            MyPackage myPackage = new MyPackage(cmd, msg, myIP, myPort);
                            myPackage.generateDatagramPacket(IP, port);
                            if (cmd != CMD_ACK && !isDub(user, myPackage)) {
                                System.out.println("\n" + msg);
                                messages.put(user, myPackage);
                            }

                            switch (cmd) {
                                case CMD_ACK: {
                                    //System.out.println("SYSTEM: got ACK");
                                    sentMessages.remove(Integer.parseInt(msg));                   //ACK message is hash of prev message
                                    break;
                                }
                                case CMD_REG: {
                                    if (isKnown(IP, port)) {
                                        MyPackage ACKpack = new MyPackage(CMD_ACK, String.valueOf(hash), myIP, myPort);
                                        sendMsg(IP, port, ACKpack);
                                        MyPackage pack = new MyPackage(CMD_REG, msg, myIP, myPort);
                                        sendToOthers(pack, IP, port);                            //IP and port not to send
                                    }
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    System.out.println("lost pack");
                }
            } catch (SocketTimeoutException e) {
                e.printStackTrace();
                removeUser(String.valueOf(packet.getAddress()), packet.getPort());
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

    private void sendMsg(String IP, int port, MyPackage pack) throws IOException {
        DatagramPacket packet = pack.generateDatagramPacket(IP, port);
        socket.send(packet);
        User user = getUser(IP, port);
        if (pack.cmd != CMD_ACK) {
            sentMessages.put(pack.hash, pack);                         //do not send ACK for ACK
            messages.put(user, pack);
        }
    }

    private User getUser(String IP, int port) {
        for (User u : users) {
            if (u.IP.equals(IP) && u.port == port)
                return u;
        }
        return null;
    }

    void sendToOthers(MyPackage pack, String selfIP, int selfPort) throws IOException {
        for (User u : users) {
            if (!u.IP.equals(selfIP) || u.port != selfPort)
                sendMsg(u.IP, u.port, pack);
        }
    }

    private boolean isKnown(String IP, int port) {
        for (User u : users) {
            if (u.IP.equals(IP) && u.port == port)
                return true;
        }
        return false;
    }

    private void removeUser(String IP, int port) {
        User u = getUser(IP, port);
        if (u != null) {
            u.name = u.name.replace("user", "");
            int num = Integer.parseInt(u.name) - 1;
            users.remove(num);
        }
    }

    void printUsers() {
        if (users.isEmpty()) System.out.println("No kids, hopefully");
        else {
            for (User u : users)
                System.out.println(u.name + " " + u.IP + ":" + String.valueOf(u.port));
        }
    }

    void printMsgs() {
        for (Object o : messages.entrySet()) {
            Map.Entry pair = (Map.Entry) o;
            MyPackage pack = (MyPackage) pair.getValue();
            System.out.println(pack.data);
        }
    }

    private boolean isDub(User user, MyPackage msg) {
        if (messages.isEmpty()) return false;
        if (messages.containsKey(user)) {
            MyPackage pack = messages.get(user);
            return pack.hash == msg.hash;
        }
        return false;
    }
}