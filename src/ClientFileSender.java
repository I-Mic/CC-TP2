import java.io.*;
import java.net.*;

class ClientFileSender implements Runnable {
    private final DatagramSocket socket;
    private final InetAddress ip;
    private final int porta;
    private final String ficheiro;
    private final int posicao;
    private final String pasta;

    private static final int TIMEOUT = 1000; // milisegundos
    private static final int SIZE = 980; // tamanho do conteudo a enviar

    public ClientFileSender(DatagramSocket socket, InetAddress ip, int porta, String ficheiro, int posicao, String pasta) {
        this.socket = socket;
        this.ip = ip;
        this.porta = porta;
        this.ficheiro = ficheiro;
        this.posicao = posicao;
        this.pasta = pasta;
    }


    @Override
    public void run() {
        try {
            int i = 0;
            int numSeq = 0;
            // envia conteudo do ficheiro
            DataInputStream in = new DataInputStream(new FileInputStream(this.pasta + "/" + this.ficheiro));
            byte[] dados = new byte[SIZE];
            System.out.println("A enviar ficheiro...");

            long bytesLidos = in.read(dados);
            while (bytesLidos == SIZE) {
                // stop and wait control
                boolean timedOut = true;

                while (timedOut) {
                    if (i >= 5) {
                        System.out.println("Conecção com o servidor perdida...");
                        return;
                    }

                    this.socket.setSoTimeout(TIMEOUT);

                    numSeq++;
                    if (numSeq == 256) numSeq = 0;


                    // Send and receive variables
                    byte[] sendData = new ClientMsg(3, numSeq, 0, this.posicao, dados).serialize();
                    byte[] receiveData = new byte[1000];


                    try {
                        // cria e envia datagrama
                        DatagramPacket send = new DatagramPacket(sendData, sendData.length, this.ip, this.porta);
                        this.socket.send(send);

                        // recebe packet do server
                        DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                        this.socket.receive(receive);

                        while (ClientMsg.deserialize(receiveData).getSequencia() != numSeq)
                            this.socket.receive(receive);

                        // Recebemos um ACK
                        timedOut = false;

                    } catch (SocketTimeoutException exception) {
                        // ACK nao foi recebido, manda outra vez
                        numSeq--;
                    }
                    i++;
                }
                i = 0;
                dados = new byte[SIZE];
                bytesLidos = in.read(dados);
            }

            // stop and wait control
            boolean timedOut = true;

            while (timedOut) {
                if (i >= 5) {
                    System.out.println("Conecção com o servidor perdida...");
                    return;
                }

                numSeq++;

                // Send and receive variables
                byte[] sendData = new ClientMsg(3, numSeq, bytesLidos, this.posicao, dados).serialize();
                byte[] receiveData = new byte[1000];


                try {
                    // cria e envia datagrama
                    DatagramPacket send = new DatagramPacket(sendData, sendData.length, this.ip, this.porta);
                    this.socket.send(send);

                    // recebe packet do cliente
                    DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                    this.socket.receive(receive);

                    while (ClientMsg.deserialize(receiveData).getSequencia() != numSeq)
                        this.socket.receive(receive);

                    // Recebemos um ACK
                    timedOut = false;

                } catch (SocketTimeoutException exception) {
                    // ACK nao foi recebido, manda outra vez
                    numSeq--;
                }
                i++;
            }
            System.out.println("Ficheiro " + this.ficheiro + " enviado.");
            i = 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}