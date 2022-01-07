import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

class ClientFileReceiver implements Runnable {
    private final DatagramSocket socket;
    private final InetAddress ip;
    private final int porta;
    private final ArrayList<String> ficheiros;
    private final String pasta;

    private static final int TIMEOUT = 1000; // milisegundos

    public ClientFileReceiver(DatagramSocket socket, InetAddress ip, int porta, ArrayList<String> ficheiros, String pasta) {
        this.socket = socket;
        this.ip = ip;
        this.porta = porta;
        this.ficheiros = new ArrayList<>(ficheiros);
        this.pasta = pasta;
    }


    @Override
    public void run() {
        try {
            int numSeq = -1;
            socket.setSoTimeout(0);
            String ficheiro = "n/a";

            ClientMsg clientMsg = new ClientMsg(0,numSeq + 1);
            if (!enviaMsg(this.socket,clientMsg,this.ip,this.porta,numSeq)) {
                System.out.println("Conecção com o servidor perdida...");
                return;
            }

            System.out.println("A receber ficheiro...");
            byte[] receiveData = new byte[1000];
            DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
            this.socket.receive(receive);

            // retira msg do packet
            ServerMsg returnMsg = ServerMsg.deserialize(receiveData);

            int numSeqServidor = returnMsg.getSequencia();

            // envia ACK
            enviarACK(this.socket, receive, returnMsg.getSequencia());
            if (numSeq != numSeqServidor) {
                numSeq = numSeqServidor;

                // caso em que o conteudo inteiro do ficheiro vem em um só packet
                if (returnMsg.getFlag() == 2 && returnMsg.getEof() == 1) {
                    ficheiro = this.ficheiros.get(returnMsg.getPosicao());
                    writeToFile(ficheiro, returnMsg.getConteudo(), (int) returnMsg.getEof());
                }

                // caso em que o conteudo inteiro do ficheiro vem em vários packects
                if (returnMsg.getFlag() == 2 && returnMsg.getEof() == 0) {
                    ficheiro = this.ficheiros.get(returnMsg.getPosicao());
                    writeToFile(ficheiro, returnMsg.getConteudo(), (int) returnMsg.getEof());

                    while (returnMsg.getEof() == 0) {
                        receiveData = new byte[1000];
                        receive = new DatagramPacket(receiveData, receiveData.length);
                        this.socket.receive(receive);
                        returnMsg = ServerMsg.deserialize(receiveData);
                        numSeqServidor = returnMsg.getSequencia();

                        // envia ACK
                        enviarACK(this.socket, receive, numSeqServidor);

                        if (numSeq != numSeqServidor) {
                            writeToFile(ficheiro, returnMsg.getConteudo(), (int) returnMsg.getEof());
                            numSeq = numSeqServidor;
                        }
                    }
                }
            }
            System.out.println("Ficheiro " + ficheiro + " recebido.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void enviarACK(DatagramSocket socket, DatagramPacket receive, int numSeq) throws IOException {
        // ip e porta do server
        int portaServer = receive.getPort();
        InetAddress iPServer = receive.getAddress();

        byte[] sendData = new ClientMsg(0,numSeq).serialize();
        DatagramPacket send = new DatagramPacket(sendData, sendData.length, iPServer, portaServer);
        socket.send(send);
    }


    public void writeToFile(String nome, byte[] bytes, int size) {
        File outputFile = new File(this.pasta + "/" + nome);
        try (FileOutputStream fos = new FileOutputStream(outputFile,true)) {
            if (size != 0) fos.write(bytes,0, size);
            else fos.write(bytes,0,980);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean enviaMsg(DatagramSocket socket, ClientMsg clientMsg, InetAddress iPCliente, int portaCliente, int numSeq) throws IOException {
        int i = 0;
        // stop and wait control
        boolean timedOut = true;

        while (timedOut) {
            if (i >= 5) return false;

            socket.setSoTimeout(TIMEOUT);
            numSeq++;

            // Send and receive variables
            byte[] sendData = clientMsg.serialize();
            byte[] receiveData = new byte[1000];


            try{
                // cria e envia datagrama
                DatagramPacket send = new DatagramPacket(sendData, sendData.length, iPCliente, portaCliente);
                socket.send(send);

                // recebe packet do servidor
                DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receive);

                while (ServerMsg.deserialize(receiveData).getSequencia() != numSeq)
                    socket.receive(receive);

                // retira msg do packet
                ServerMsg returnMsg = ServerMsg.deserialize(receiveData);

                // Recebemos um ACK
                timedOut = false;

            } catch( SocketTimeoutException exception ){
                // ACK nao foi recebido, manda outra vez
                numSeq--;
            }
            i++;
        }
        i = 0;
        socket.setSoTimeout(0);
        return true;
    }
}
