import java.net.DatagramSocket;
import java.net.SocketException;

import static java.lang.System.exit;

public class Main {

    public static void main(String[] args) {
        String name = null;
        int loose = 0;
        int port = -1;
        String parentIP;
        int parentPort = -1;

        if (args.length < 3 || args.length == 4 || args.length > 5)
            wrongArgs();

        if (args.length >= 3) {
            name = args[0];                                     // собственное имя узла
            try {
                loose = Integer.parseInt(args[1]);              // процент потерь
                port = Integer.parseInt(args[2]);               // собственный порт
            } catch (NumberFormatException e) {
                System.out.println(e.toString());
                wrongArgs();
            }
        }

        Node node = new Node(name, loose, port);

        if (args.length == 5) {
            parentIP = args[3];                                 // опционально IP адрес и порт узла-предка.
            try {
                parentPort = Integer.parseInt(args[4]);         // Приложение, которому не был передан IP адрес и
            } catch (NumberFormatException e) {                 // порт узла-предка, становится корнем дерева.
                System.out.println(e.toString());
                wrongArgs();
            }
            node.setParentIP(parentIP);
            node.setParentPort(parentPort);
        }

        node.start();
    }

    private static void wrongArgs() {
        System.out.println("Error: wrong args");
        System.out.println("Should be: <name> <loose percent> <port> (opt.)<parent IP> (opt.)<parent port>");
        exit(1);
    }
}
