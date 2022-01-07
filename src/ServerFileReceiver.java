import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

class ServerFileReceiver implements Runnable {
    private final DatagramSocket socket;
    private final InetAddress ip;
    private final int porta;
    private final ArrayList<String> ficheiros;
    private final String pasta;
    private final File log;

    private static final int TIMEOUT = 1000; // milisegundos

    public ServerFileReceiver(DatagramSocket socket, InetAddress ip, int porta, ArrayList<String> ficheiros, String pasta, File log){
        this.socket = socket;
        this.ip = ip;
        this.porta = porta;
        this.ficheiros = new ArrayList<>(ficheiros);
        this.pasta = pasta;
        this.log = log;
    }


    @Override
    public void run() {
        try {
            int n = 0;
            int numSeq = -1;
            socket.setSoTimeout(0);
            String ficheiro = "n/a";

            ServerMsg serverMsg = new ServerMsg(0,numSeq + 1);
            if (!enviaMsg(this.socket,serverMsg,this.ip,this.porta,numSeq)) {
                writeToFileLog(this.log, "Conecção com o cliente perdida...");
                System.out.println("Conecção com o cliente perdida...");
                return;
            }



            // começa a transferencia do ficheiro
            long start = System.currentTimeMillis();
            System.out.println("A receber ficheiro...");



            byte[] receiveData = new byte[1000];
            DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
            this.socket.receive(receive);

            // retira msg do packet
            ClientMsg returnMsg = ClientMsg.deserialize(receiveData);

            int numSeqCliente = returnMsg.getSequencia();

            // envia ACK
            enviarACK(this.socket, receive, numSeqCliente);
            if (numSeq != numSeqCliente) {
                numSeq = numSeqCliente;

                // caso em que o conteudo inteiro do ficheiro vem em um só packet
                if (returnMsg.getFlag() == 3 && returnMsg.getEof() > 0) {
                    ficheiro = this.ficheiros.get(returnMsg.getPosicao());
                    writeToFile(ficheiro, returnMsg.getConteudo(), (int) returnMsg.getEof());
                }

                // caso em que o conteudo inteiro do ficheiro vem em vários packects
                if (returnMsg.getFlag() == 3 && returnMsg.getEof() == 0) {
                    n++;
                    ficheiro = this.ficheiros.get(returnMsg.getPosicao());
                    writeToFile(ficheiro, returnMsg.getConteudo(), (int) returnMsg.getEof());

                    while (returnMsg.getEof() == 0) {
                        n++;
                        receiveData = new byte[1000];
                        receive = new DatagramPacket(receiveData, receiveData.length);
                        this.socket.receive(receive);
                        returnMsg = ClientMsg.deserialize(receiveData);
                        numSeqCliente = returnMsg.getSequencia();

                        // envia ACK
                        enviarACK(this.socket, receive, numSeqCliente);
                        writeToFileLog(this.log,"ACK atual " + "("+ ficheiro +"): " + numSeq);
                        writeToFileLog(this.log,"Enviado ACK NumSeq " + "("+ ficheiro +"): " + numSeqCliente);

                        if (numSeq != numSeqCliente) {
                            writeToFile(ficheiro, returnMsg.getConteudo(), (int) returnMsg.getEof());
                            numSeq = numSeqCliente;
                        }
                    }
                }
            }


            // acaba a transferencia do ficheiro
            float time = (System.currentTimeMillis() - start)/1000F;
            float debito = (n*980 + returnMsg.getEof())*8 / time; // bits/seg
            System.out.println("Ficheiro " + ficheiro + " recebido.");
            writeToFileLog(this.log,"Tempo de transferência do ficheiro " + ficheiro + ": " + time + " segundos.\nDébito " + debito + " bits por segundo.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void enviarACK(DatagramSocket socket, DatagramPacket receive, int numSeq) throws IOException {
        // ip e porta do cliente
        int portaCliente = receive.getPort();
        InetAddress iPCliente = receive.getAddress();

        byte[] sendData = new ServerMsg(0,numSeq).serialize();
        DatagramPacket send = new DatagramPacket(sendData, sendData.length, iPCliente, portaCliente);
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


    public void writeToFileLog(File file, String text) {
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(text + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static boolean enviaMsg(DatagramSocket socket, ServerMsg serverMsg, InetAddress iPCliente, int portaCliente, int numSeq) throws IOException {
        int i = 0;
        // stop and wait control
        boolean timedOut = true;

        while (timedOut) {
            if (i >= 5) return false;

            socket.setSoTimeout(TIMEOUT);
            numSeq++;

            // Send and receive variables
            byte[] sendData = serverMsg.serialize();
            byte[] receiveData = new byte[1000];


            try {
                // cria e envia datagrama
                DatagramPacket send = new DatagramPacket(sendData, sendData.length, iPCliente, portaCliente);
                socket.send(send);

                // recebe packet do cliente
                DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receive);

                while (ClientMsg.deserialize(receiveData).getSequencia() != numSeq)
                    socket.receive(receive);

                // Recebemos um ACK
                timedOut = false;

            } catch( SocketTimeoutException exception ) {
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
