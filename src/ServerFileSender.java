import java.io.*;
import java.net.*;

class ServerFileSender implements Runnable {
    private final DatagramSocket socket;
    private final InetAddress ip;
    private final int porta;
    private final String ficheiro;
    private final int posicao;
    private final String pasta;
    private final File log;

    private static final int TIMEOUT = 1000; // milisegundos
    private static final int SIZE = 980; // tamanho do conteudo a enviar

    public ServerFileSender(DatagramSocket socket, InetAddress ip, int porta, String ficheiro, int posicao, String pasta, File log) {
        this.socket = socket;
        this.ip = ip;
        this.porta = porta;
        this.ficheiro = ficheiro;
        this.posicao = posicao;
        this.pasta = pasta;
        this.log = log;
    }


    @Override
    public void run() {
        try {
            // começa a transferencia do ficheiro
            long start = System.currentTimeMillis();
            System.out.println("A enviar ficheiro...");

            int n = 0;
            int i = 0;
            int numSeq = 0;
            // envia conteudo do ficheiro
            DataInputStream in = new DataInputStream(new FileInputStream(this.pasta + "/" + this.ficheiro));
            byte[] dados = new byte[SIZE];

            long bytesLidos = in.read(dados);
            while (bytesLidos == SIZE) {
                n++;
                // stop and wait control
                boolean timedOut = true;

                while (timedOut) {
                    if (i >= 5) {
                        writeToFile(log,"Conecção com o cliente perdida...");
                        System.out.println("Conecção com o cliente perdida...");
                        return;
                    }

                    this.socket.setSoTimeout(TIMEOUT);

                    numSeq++;
                    if (numSeq == 256) {
                        numSeq = 0;
                        writeToFile(log,"Numero de sequencia resetado");
                    }


                    // Send and receive variables
                    byte[] sendData = new ServerMsg(2, numSeq, 0, this.posicao, dados).serialize();
                    byte[] receiveData = new byte[1000];
                    writeToFile(log,"Thread " + Thread.currentThread().getId() +
                                        " sending content from file " + this.ficheiro +
                                        " (Sequence Number " + numSeq + ")");


                    try {
                        // cria e envia datagrama
                        DatagramPacket send = new DatagramPacket(sendData, sendData.length, this.ip, this.porta);
                        this.socket.send(send);

                        // recebe packet do cliente
                        DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                        this.socket.receive(receive);
                        ClientMsg clientMsg = ClientMsg.deserialize(receiveData);

                        writeToFile(log,"NSeq Ack recebido: " + clientMsg.getSequencia() + " NSeq desejado: " + numSeq);

                        while (clientMsg.getSequencia() != numSeq) {
                            this.socket.receive(receive);
                            clientMsg = ClientMsg.deserialize(receiveData);
                            writeToFile(log,"NSeq Ack recebido: " + clientMsg.getSequencia() + " NSeq desejado: " + numSeq);
                        }

                        // retira msg do packet
                        writeToFile(log, "FROM CLIENT: " + clientMsg.getFlag());

                        // Recebemos um ACK
                        timedOut = false;

                    } catch (SocketTimeoutException exception) {
                        // ACK nao foi recebido, manda outra vez
                        writeToFile(log,"Timeout (Sequence Number " + numSeq + ")");
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
                    float time = (System.currentTimeMillis() - start)/1000F;
                    float debito = (n*SIZE + bytesLidos)*8 / time; // bits/seg
                    System.out.println("Ficheiro " + this.ficheiro + " enviado.");
                    writeToFile(log,"Tempo de transferência do ficheiro " + this.ficheiro + ": " + time + " segundos.\nDébito " + debito + " bits por segundo.");
                    writeToFile(log,"Conecção com o cliente perdida...");
                    System.out.println("Conecção com o cliente perdida...");
                    return;
                }

                numSeq++;

                // Send and receive variables
                byte[] sendData = new ServerMsg(2, numSeq, bytesLidos, this.posicao, dados).serialize();
                byte[] receiveData = new byte[1000];
                writeToFile(log,"Thread " + Thread.currentThread().getId() +
                                    " sending last content from file " + this.ficheiro +
                                    " (Sequence Number " + numSeq + ")");


                try {
                    // cria e envia datagrama
                    DatagramPacket send = new DatagramPacket(sendData, sendData.length, this.ip, this.porta);
                    this.socket.send(send);

                    // recebe packet do cliente
                    DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                    this.socket.receive(receive);
                    ClientMsg clientMsg = ClientMsg.deserialize(receiveData);

                    writeToFile(log,"NSeq Ack recebido: " + clientMsg.getSequencia() + " NSeq desejado: " + numSeq);

                    while (clientMsg.getSequencia() != numSeq) {
                        this.socket.receive(receive);
                        clientMsg = ClientMsg.deserialize(receiveData);
                        writeToFile(log,"NSeq Ack recebido: " + clientMsg.getSequencia() + " NSeq desejado: " + numSeq);
                    }

                    // retira msg do packet
                    writeToFile(log, "FROM CLIENT: " + clientMsg.getFlag());

                    // Recebemos um ACK
                    timedOut = false;

                } catch (SocketTimeoutException exception) {
                    // ACK nao foi recebido, manda outra vez
                    writeToFile(this.log,"Timeout (Sequence Number " + numSeq + ")");
                    numSeq--;
                }
                i++;
            }
            i = 0;


            // acaba a transferencia de ficheiros SERVIDOR->CLIENTE
            float time = (System.currentTimeMillis() - start)/1000F;
            float debito = (n*SIZE + bytesLidos)*8 / time; // bits/seg
            System.out.println("Ficheiro " + this.ficheiro + " enviado.");
            writeToFile(log,"Tempo de transferência do ficheiro " + this.ficheiro + ": " + time + " segundos.\nDébito " + debito + " bits por segundo.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeToFile(File file, String text) {
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(text + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}