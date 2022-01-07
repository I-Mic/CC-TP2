import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;

public class Server {
    public static void main(String[] args) {
        try {
            ServerSocket tcpSocket = new ServerSocket(80); // TCP
            DatagramSocket udpSocket = new DatagramSocket(80); // UDP

            // ficheiro responsavel por guardar o estado de funcionamento do servidor
            File file = new File("stats.txt");
            FileWriter fw = new FileWriter(file);
            fw.write("ESTADO FUNCIONAMENTO DO SERVIDOR\n");
            fw.close();

            // package responsavel por guardar ficheiros txt com as operações executadas para cada cliente
            File logs = new File("logs/");
            if (!logs.exists()) logs.mkdir();

            // thread responsavel por atender pedidos tcp
            Thread tcp = new Thread(new TCPHandler(tcpSocket));
            tcp.start();

            // thread responsavel por atender pedidos udp
            Thread udp = new Thread(new UDPHandler(udpSocket,file));
            udp.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
