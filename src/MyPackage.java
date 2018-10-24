import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.UUID;

/*PROTOCOL:
 * type:      cmd:   hash:   data:
 * hello msg     0   data    length + IP & port
 * ACK           1   data    length + hash of client
 * regular msg   2   data    length + text
 * bb msg        3   data    length + IP & port */

class MyPackage {
    private int cmd;
    int hash;
    private String data;
    private ByteBuffer buf;
    private int bufLen;
    private String myAddress;
    private int myPort;
    DatagramPacket packet;

    MyPackage(int cmd, String data, String myAddress, int myPort) {
        this.cmd = cmd;
        this.data = data;
        String uuidStr = UUID.randomUUID().toString();
        this.hash = uuidStr.hashCode();
        this.myAddress = myAddress;
        this.myPort = myPort;
    }

    private void genPackage() {
        byte[] msg = data.getBytes();
        byte[] myIP = myAddress.getBytes();
        byte[] myIPSize = ByteBuffer.allocate(Integer.BYTES).putInt(myIP.length).array();
        byte[] myPort = ByteBuffer.allocate(Integer.BYTES).putInt(this.myPort).array();
        byte[] cmdBytes = ByteBuffer.allocate(Integer.BYTES).putInt(cmd).array();
        byte[] hashBytes = ByteBuffer.allocate(Integer.BYTES).putInt(hash).array();
        byte[] msgLenBytes = ByteBuffer.allocate(Integer.BYTES).putInt(msg.length).array();
        this.bufLen = cmdBytes.length + hashBytes.length + myIPSize.length + myIP.length + myPort.length;
        this.bufLen += msgLenBytes.length + msg.length;
        this.buf = ByteBuffer.allocate(this.bufLen);
        this.buf.put(cmdBytes).put(hashBytes).put(myIPSize).put(myIP).put(myPort).put(msgLenBytes).put(msg);
    }

    DatagramPacket generateDatagramPacket(String IP, int port) throws UnknownHostException {
        genPackage();
        InetAddress address = InetAddress.getByName(IP);
        this.packet = new DatagramPacket(buf.array(), bufLen, address, port);
        return this.packet;
    }
}
