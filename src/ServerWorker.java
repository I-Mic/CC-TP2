import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class ServerWorker implements Runnable {

    private final DatagramSocket socket;
    private final DatagramPacket receive;
    private final byte[] receiveData;
    private final File stats;

    private static final int TIMEOUT = 1000; // milisegundos
    private static final String pathPastaLogs = "logs";


    public ServerWorker(DatagramSocket socket, DatagramPacket receive, byte[] receiveData, File stats) {
        this.socket = socket;
        this.receive = receive;
        this.receiveData = receiveData;
        this.stats = stats;
    }

    @Override
    public void run() {
        try {
            int numSeqServer = -1;


            // ip e porta do cliente
            InetAddress iPCliente = this.receive.getAddress();
            int portaCliente = this.receive.getPort();
            File log = criaLogs(iPCliente);


            // envia ACK
            enviarACK(this.socket, this.receive, ClientMsg.deserialize(this.receiveData).getSequencia());


            // retira msg do packet
            ClientMsg clientMsg1 = ClientMsg.deserialize(this.receiveData);
            String pasta = clientMsg1.getPasta();
            writeToFile(log,"FROM CLIENT: package: " + pasta);


            if (pasta != null) {
                // escrever conecao estabelecida
                FileWriter fw = new FileWriter(this.stats, true);
                fw.write("coneccao estabelecida com o cliente :: " + iPCliente + "\n");
                fw.close();


                // recebe mensagem do cliente com o nome dos ficheiros
                ClientMsg clientMsg2 = recebeMsg(this.socket);
                int numSeqCliente = clientMsg2.getSequencia();
                while (numSeqServer == numSeqCliente && clientMsg2.getFlag() != 2)
                    clientMsg2 = recebeMsg(this.socket);
                numSeqServer = numSeqCliente;


                // ficheiros que o server precisa
                ArrayList<String> ficheirosAReceber = ficheirosAReceber(pasta, clientMsg2.getFicheiros());
                writeToFile(log,"SERVER >> ficheiros a receber: " + ficheirosAReceber);
                System.out.println("SERVER >> ficheiros a receber: " + ficheirosAReceber);


                // envia nome dos ficheiros que precisa
                ServerMsg serverMsg = new ServerMsg(1, numSeqServer + 1, ficheirosAReceber);
                if (!enviaMsg(this.socket, serverMsg, iPCliente, portaCliente, numSeqServer)) {
                    writeToFile(log,"Conecção com o cliente perdida...");
                    System.out.println("Conecção com o cliente perdida...");
                    return;
                }
                numSeqServer++;


                int N1 = ficheirosAReceber.size();
                Thread[] at1 = new Thread[N1];

//// TRHEADS (uma por cada cada file a receber)
                for (int i = 0; i < N1; i++)
                    at1[i] = new Thread(new ServerFileReceiver(new DatagramSocket(),iPCliente,portaCliente,ficheirosAReceber,pasta, log));

                for (int i = 0; i < N1; i++)
                    at1[i].start();

                for (int i = 0; i < N1; i++)
                    at1[i].join();


                // escrever ficheiros recebidos
                fw = new FileWriter(this.stats, true);
                fw.write("ficheiros recebidos do cliente :: " + iPCliente + " ::");
                for (String s: ficheirosAReceber)
                    fw.write(" " + s);
                fw.write("\n");
                fw.close();


                // ficheiros que o cliente precisa
                ArrayList<String> ficheirosAEnviar = ficheirosAEnviar(pasta, clientMsg2.getFicheiros());
                writeToFile(log,"SERVER >> ficheiros a enviar: " + ficheirosAEnviar);
                System.out.println("SERVER >> ficheiros a enviar: " + ficheirosAEnviar);

                // envia nome dos ficheiros que o cliente precisa
                ServerMsg serverMsg2 = new ServerMsg(1, numSeqServer + 1, ficheirosAEnviar);
                if (!enviaMsg(this.socket, serverMsg2, iPCliente, portaCliente, numSeqServer)) {
                    writeToFile(log,"Conecção com o cliente perdida...");
                    System.out.println("Conecção com o cliente perdida...");
                    return;
                }
                numSeqServer++;


                int N2 = ficheirosAEnviar.size();
                Thread[] at2 = new Thread[N2];

//// TRHEADS (uma por cada cada file a enviar)
                for (int i = 0; i < N2; i++) {
                    byte[] receiveData = new byte[1000];
                    DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                    this.socket.receive(receive);

                    // retira msg do packet
                    ClientMsg returnMsg = ClientMsg.deserialize(receiveData);

                    // enviar ACK
                    enviarACK(this.socket,receive, returnMsg.getSequencia());

                    at2[i] = new Thread(new ServerFileSender(this.socket, iPCliente, receive.getPort(), ficheirosAEnviar.get(i), i, pasta, log));
                }

                for (int i = 0; i < N2; i++)
                    at2[i].start();

                for (int i = 0; i < N2; i++)
                    at2[i].join();


                // escrever ficheiros enviados
                fw = new FileWriter(this.stats, true);
                fw.write("ficheiros enviados para o cliente :: " + iPCliente + " ::");
                for (String s: ficheirosAEnviar)
                    fw.write(" " + s);
                fw.write("\n");
                fw.close();

                // escrever coneccao fechada
                fw = new FileWriter(this.stats, true);
                fw.write("coneccao fechada com o cliente :: " + iPCliente + "\n");
                fw.close();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public File criaLogs(InetAddress ip) {
        try {
            File logs = new File(pathPastaLogs + ip + "_logs.txt");
            FileWriter fw = new FileWriter(logs);
            fw.write("FICHEIRO DE REGISTOS DO CLIENTE " + ip  + "\n");
            fw.close();
            return logs;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void writeToFile(File file, String text) {
        try (FileWriter fw = new FileWriter(file, true)) {
            fw.write(text + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<String> ficheirosAReceber(String file, ArrayList<String> ficheirosDoCliente) {
        ArrayList<String> ficheirosAReceber = new ArrayList<>();
        File directoryPath = new File(file);
        String[] contents = directoryPath.list();
        List<String> validar = Arrays.asList(contents);

        for (String s : ficheirosDoCliente) {
            if (!validar.contains(s))
                ficheirosAReceber.add(s);
        }

        return ficheirosAReceber;
    }


    public static ArrayList<String> ficheirosAEnviar(String file, ArrayList<String> ficheirosDoCliente) {
        ArrayList<String> ficheirosAEnviar = new ArrayList<>();
        File directoryPath = new File(file);
        String[] contents = directoryPath.list();

        for (String s : contents) {
            if (!ficheirosDoCliente.contains(s))
                ficheirosAEnviar.add(s);
        }

        return ficheirosAEnviar;
    }


    public static boolean enviaMsg(DatagramSocket socket, ServerMsg serverMsg, InetAddress iPCliente, int portaCliente, int numSeqServer) throws IOException {
        int i = 0;
        // stop and wait control
        boolean timedOut = true;

        while (timedOut) {
            if (i >= 5) return false;

            socket.setSoTimeout(TIMEOUT);
            numSeqServer++;

            // Send and receive variables
            byte[] sendData = serverMsg.serialize();
            byte[] receiveData = new byte[1000];


            try{
                // cria e envia datagrama
                DatagramPacket send = new DatagramPacket(sendData, sendData.length, iPCliente, portaCliente);
                socket.send(send);

                // recebe packet do cliente
                DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receive);

                while (ClientMsg.deserialize(receiveData).getSequencia() != numSeqServer)
                    socket.receive(receive);

                // Recebemos um ACK
                timedOut = false;

            } catch (SocketTimeoutException exception) {
                // ACK nao foi recebido, manda outra vez
                numSeqServer--;
            }
            i++;
        }
        i = 0;
        socket.setSoTimeout(0);
        return true;
    }


    public static ClientMsg recebeMsg(DatagramSocket socket) throws IOException {
        // receive variable
        byte[] receiveData = new byte[1000];

        // reccebe packet do cliente
        DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receive);

        // retira msg do packet
        ClientMsg returnMsg = ClientMsg.deserialize(receiveData);

        // envia ACK
        enviarACK(socket, receive, returnMsg.getSequencia());

        return returnMsg;
    }


    public static void enviarACK(DatagramSocket socket, DatagramPacket receive, int numSeq) throws IOException {
        // ip e porta do cliente
        int portaCliente = receive.getPort();
        InetAddress iPCliente = receive.getAddress();

        // send variable
        byte[] sendData = new ServerMsg(0,numSeq).serialize();
        DatagramPacket send = new DatagramPacket(sendData, sendData.length, iPCliente, portaCliente);
        socket.send(send);
    }
}
