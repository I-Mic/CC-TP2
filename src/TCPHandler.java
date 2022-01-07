import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

class TCPHandler implements Runnable {

    private final ServerSocket socket;

    public TCPHandler(ServerSocket socket) {
        this.socket = socket;
    }
    @Override
    public void run() {
        while (true) {
            try {
                // espera e aceita pedido
                // cria-se uma thread responsavel por tratar do pedido
                // e ao mesmo tempo já estamos à espera de outro
                Socket socketTCP = this.socket.accept();
                Thread statWorker = new Thread(new StatWorker(socketTCP));
                statWorker.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
