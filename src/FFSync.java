import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;

public class FFSync {

    private static final int TIMEOUT = 1000; // milisegundos
    private static int numSeqCliente = 0;
    private static int porta = 80;
    private static int i = 0;

    public static void main(String[] args) {

        try {
            // cria um socket UDP
            DatagramSocket socket = new DatagramSocket();


            // ler argumentos pasta e ip
            String pasta = args[0];
            InetAddress serverIP = InetAddress.getByName(args[1]);


            // envia nome da pasta
            ClientMsg clientMsg1 = new ClientMsg(1, numSeqCliente + 1, pasta);
            if (!enviaMsg(socket, serverIP, clientMsg1)) {
                System.out.println("Conecção com o servidor não estabelecida...");
                return;
            }


            // ficheiros da pasta
            ArrayList<String> ficheiros = getFiles(pasta);


            // envia ficheiros da pasta
            ClientMsg clientMsg2 = new ClientMsg(2, numSeqCliente + 1, ficheiros);
            if (!enviaMsg(socket, serverIP, clientMsg2)) {
                System.out.println("Conecção com o servidor perdida...");
                return;
            }


            // recebe lista com o nome dos ficheiros que o server não tem
            ServerMsg serverMsg1 = recebeMsg(socket);
            int numSeqServer = serverMsg1.getSequencia();
            while (numSeqCliente == numSeqServer && serverMsg1.getFlag() != 1)
                serverMsg1 = recebeMsg(socket);
            numSeqCliente = numSeqServer;


            ArrayList<String> ficheirosAEnviar = serverMsg1.getFicheiros();
            System.out.println("CLIENT >> ficheiros a enviar: " + ficheirosAEnviar);


            int N1 = ficheirosAEnviar.size();
            Thread[] at1 = new Thread[N1];

//// TRHEADS (uma por cada cada file a enviar)
            for (int i = 0; i < N1; i++) {
                byte[] receiveData = new byte[1000];
                DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receive);

                // retira msg do packet
                ServerMsg returnMsg = ServerMsg.deserialize(receiveData);

                // enviar ACK
                enviarACK(socket,receive,returnMsg.getSequencia());

                at1[i] = new Thread(new ClientFileSender(socket,serverIP,receive.getPort(),ficheirosAEnviar.get(i),i,pasta));
            }

            for (int i = 0; i < N1; i++)
                at1[i].start();

            for (int i = 0; i < N1; i++)
                at1[i].join();



            // recebe lista com o nome dos ficheiros que o server vai mandar
            ServerMsg serverMsg2 = recebeMsg(socket);
            numSeqServer = serverMsg2.getSequencia();
            while (numSeqCliente == numSeqServer && serverMsg2.getFlag() != 1)
                serverMsg2 = recebeMsg(socket);
            numSeqCliente = numSeqServer;


            ArrayList<String> ficheirosAReceber = serverMsg2.getFicheiros();
            System.out.println("CLIENT >> ficheiros a receber: " + ficheirosAReceber);


            int N2 = ficheirosAReceber.size();
            Thread[] at2 = new Thread[N2];

//// TRHEADS (uma por cada cada file a receber)
            for (int i = 0; i < N2; i++)
                at2[i] = new Thread(new ClientFileReceiver(new DatagramSocket(),serverIP,porta,ficheirosAReceber,pasta));

            for (int i = 0; i < N2; i++)
                at2[i].start();

            for (int i = 0; i < N2; i++)
                at2[i].join();


            // fecha socket
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static ArrayList<String> getFiles(String pasta) {
        File directoryPath = new File(pasta);
        String[] contents = directoryPath.list();
        return new ArrayList<>(Arrays.asList(contents));
    }


    public static ServerMsg recebeMsg(DatagramSocket socket) throws IOException {
        // receive variable
        byte[] receiveData = new byte[1000];

        // recebe packet do servidor
        DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receive);

        // retira msg do packet
        ServerMsg returnMsg = ServerMsg.deserialize(receiveData);

        // envia ACK
        enviarACK(socket, receive, returnMsg.getSequencia());

        return returnMsg;
    }


    public static void enviarACK(DatagramSocket socket, DatagramPacket receive, int numSeq) throws IOException {
        // ip e porta do servidor
        int portaServidor = receive.getPort();
        InetAddress iPServidor = receive.getAddress();

        // send variable
        byte[] sendData = new ClientMsg(0,numSeq).serialize();
        DatagramPacket send = new DatagramPacket(sendData, sendData.length, iPServidor, portaServidor);
        socket.send(send);
    }


    public static boolean enviaMsg(DatagramSocket socket, InetAddress serverIP, ClientMsg clientMsg) throws IOException {
        // stop and wait control
        boolean timedOut = true;

        while (timedOut) {
            if (i >= 5) return false;

            socket.setSoTimeout(TIMEOUT);
            numSeqCliente++;

            // Send and receive variables
            byte[] sendData = clientMsg.serialize();
            byte[] receiveData = new byte[1000];


            try{
                // cria e envia datagrama
                DatagramPacket send = new DatagramPacket(sendData, sendData.length, serverIP, porta);
                socket.send(send);

                // recebe packet do cliente
                DatagramPacket receive = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receive);

                while (ClientMsg.deserialize(receiveData).getSequencia() != numSeqCliente)
                    socket.receive(receive);

                porta = receive.getPort();

                // Recebemos um ACK
                timedOut = false;

            } catch (SocketTimeoutException exception ) {
                // ACK nao foi recebido, manda outra vez
                numSeqCliente--;
            }
            i++;
        }
        i = 0;
        socket.setSoTimeout(0);
        return true;
    }
}
