import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

// tipo 0: flag 0 | nºsequencia                              // serve apenas para confirmar a receção de uma msg
// tipo 1: flag 1 | nºsequencia | nome pasta                 // msg com o nome da pasta que o cliente quer sincronizar
// tipo 2: flag 2 | nºsequencia | [nome ficheiros]           // nome dos ficheiros contidos na pasta
// tipo 3: flag 3 | nºsequencia | EOF | posicao | conteudo   // envia conteudo dos ficheiros onde EOF indica se o conteudo do ficheiro acaba ou nao nessa msg

public class ClientMsg {
    private final int flag;                 // tipo de msg
    private int sequencia;                  // nº sequencia
    private String pasta;                   // nome da pasta
    private ArrayList<String> ficheiros;    // nome dos ficheiros
    private long eof;                        // sinalizaçao de fim de ficheiro
    private int posicao;                    // indicador do ficheiro
    private byte[] conteudo;                // conteudo do ficheiro


    // construtor tipo 0
    public ClientMsg(int flag, int sequencia) {
        this.flag = flag;
        this.sequencia = sequencia;
    }
    // construtor tipo 1
    public ClientMsg(int flag, int sequencia, String nome) {
        this.flag = flag;
        this.sequencia = sequencia;
        this.pasta = nome;
    }
    // construtor tipo 2
    public ClientMsg(int flag, int sequencia, ArrayList<String> ficheiros) {
        this.flag = flag;
        this.sequencia = sequencia;
        this.ficheiros = new ArrayList<>(ficheiros);
    }
    // construtor tipo 3
    public ClientMsg(int flag, int sequencia, long eof, int posicao, byte[] conteudo) {
        this.flag = flag;
        this.sequencia = sequencia;
        this.eof = eof;
        this.posicao = posicao;
        this.conteudo = conteudo;
    }

    // gets
    public int getFlag(){
        return this.flag;
    }
    public int getSequencia(){
        return this.sequencia;
    }
    public String getPasta() {
        return this.pasta;
    }
    public ArrayList<String> getFicheiros() {
        return new ArrayList<>(this.ficheiros);
    }
    public long getEof(){
        return this.eof;
    }
    public int getPosicao(){
        return this.posicao;
    }
    public byte[] getConteudo(){
        return this.conteudo;
    }


    // converte uma mensagem do cliente num array de bytes
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final DataOutputStream daos = new DataOutputStream(baos);
        baos.write(this.flag);

        // msg tipo 0
        if (this.flag == 0) {
            baos.write(this.sequencia);
        }

        // msg tipo 1
        if (this.flag == 1) {
            baos.write(this.sequencia);
            baos.write(this.pasta.getBytes(StandardCharsets.UTF_8));
        }

        // msg tipo 2
        if (this.flag == 2) {
            baos.write(this.sequencia);
            for(String s: this.ficheiros) {
                baos.write(s.getBytes(StandardCharsets.UTF_8));
                baos.write(",".getBytes(StandardCharsets.UTF_8));
            }
        }

        // msg tipo 3
        if (this.flag == 3) {
            baos.write(this.sequencia);
            daos.writeLong(this.eof);
            baos.write(this.posicao);
            baos.write(this.conteudo);
        }
        daos.close();
        return baos.toByteArray();
    }


    // converte um array de bytes para uma mensagem do cliente
    public static ClientMsg deserialize (byte[] bytearray) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytearray);
        final DataInputStream dais = new DataInputStream(bais);
        int flag = bais.read(), sequencia, posicao;
        long eof;
        String pasta;
        ArrayList<String> ficheiros = new ArrayList<>();
        byte[] conteudo;
        ClientMsg msg = null;

        // msg tipo 0
        if (flag == 0) {
            sequencia = bais.read();
            msg = new ClientMsg(0,sequencia);
        }

        // msg tipo 1
        if (flag == 1) {
            sequencia = bais.read();

            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = bais.read()) != -1)
                sb.append((char) c);
            pasta = new String(sb).replace("\0", "");

            msg = new ClientMsg(1,sequencia,pasta);
        }

        // msg tipo 2
        if (flag == 2) {
            sequencia = bais.read();

            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = bais.read()) != -1)
                sb.append((char) c);
            String listaFicheiros = new String(sb);

            String[] tokens = listaFicheiros.split(",");
            ficheiros.addAll(Arrays.asList(tokens));
            ficheiros.remove(ficheiros.size()-1); // no serialize é adicionada uma virgula desnecessaria no ultimo ficheiro

            msg = new ClientMsg(2,sequencia,ficheiros);
        }

        // msg tipo 3
        if (flag == 3) {
            sequencia = bais.read();
            eof = dais.readLong();
            posicao = bais.read();
            conteudo = bais.readAllBytes();

            msg = new ClientMsg(3,sequencia,eof,posicao,conteudo);
        }

        return msg;
    }
}
