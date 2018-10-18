import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;


/*This thread receives message and sends them to child node (if exists) and to parent node (if exists)*/
/*Message is either received or ignored based depending on loose percent and random number generated  */
/*When msg is received ACK-package is sent back*/
/*PROTOCOL:
* type:      cmd:   hash:   data:
* hello msg     0   data    IP & port
* ACK           1   data    hash of client
* regular msg   2   data    text
* bb msg        3   data    IP & port
* */

public class Receiver extends Thread {
    private static final int PACK_SIZE = 2048;
    private byte data[] = new byte[PACK_SIZE];
    private DatagramPacket packet;
    private DatagramSocket socket;
    private HashMap<InetAddress, Integer> kidsAddrs;
    private int loose;


    Receiver(int port, int loose) throws SocketException {
        packet = new DatagramPacket(data, PACK_SIZE);
        socket = new DatagramSocket(port);
        this.loose = loose;
    }

    public void run() {
        //TODO: use loose percent
        Random random = new Random(System.currentTimeMillis());
        InetAddress newChildAddr;
        while (true) {
            try {
                socket.receive(packet);
                int rndNum = random.nextInt(100 + 1);
                if (loose < rndNum){
                    //TODO: ignore msg
                }
                else {
                    ByteBuffer bb = ByteBuffer.wrap(data, 0, Integer.BYTES);
                    int sz = bb.getInt();
                    if (data.length != 0) {
                        String str = new String(data, Integer.BYTES, sz);
                        System.out.println(str);
                        if (new String(data, Integer.BYTES, 10).equals("hello from")) {     //
                            kidsAddrs.put(packet.getAddress(), packet.getPort());
                        } else {
                            //TODO:
                            //if has parent
                            //sendToParent();
                            if (kidsAddrs.size() > 1)
                                sendToChildren(packet.getAddress(), packet.getPort(), str);
                        }
                        sendMsg(packet.getAddress(), packet.getPort(), "ACK");          //sending ACK
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void sendMsg(InetAddress addr, int port, String str) throws IOException {
        DatagramSocket sendSocket = new DatagramSocket();
        byte[] helloMsg = str.getBytes();
        byte[] dataLen = ByteBuffer.allocate(Integer.BYTES).putInt(helloMsg.length).array();
        ByteBuffer bb = ByteBuffer.allocate(helloMsg.length + dataLen.length).put(dataLen).put(helloMsg);
        DatagramPacket packet = new DatagramPacket(bb.array(), helloMsg.length + dataLen.length, addr, port);
        sendSocket.send(packet);
    }

    private void sendToChildren(InetAddress self, int port, String msg) throws IOException {
        Iterator it = kidsAddrs.entrySet().iterator();
        while (it.hasNext()){
            Map.Entry pair = (Map.Entry)it.next();
            if (pair.getKey() != self)
                sendMsg((InetAddress) pair.getKey(), (Integer)pair.getValue(), msg);
        }
    }
}
