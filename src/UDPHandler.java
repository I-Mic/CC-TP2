import javax.xml.crypto.Data;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

class UDPHandler implements Runnable {

    private final DatagramSocket socket;
    private final File file;

    public UDPHandler(DatagramSocket socket, File file) {
        this.socket = socket;
        this.file = file;
    }
    @Override
    public void run() {
        while (true) {
            try {
                // espera e aceita pedido (os pedidos aqui serao todos com flag 1 do cliente, ou seja, msg com o nome da pasta)
                // cria-se uma thread responsavel por tratar do pedido
                // e ao mesmo tempo já estamos à espera de outro
                byte[] receiveData = new byte[1000];
                DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                this.socket.receive(receive);

                Thread serverWorker = new Thread(new ServerWorker(new DatagramSocket(),receive,receiveData,this.file));
                serverWorker.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
