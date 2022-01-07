import java.io.*;
import java.net.*;

class StatWorker implements Runnable {
    private final Socket socket;

    public StatWorker(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            InputStream sis = socket.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(sis));
            String request = br.readLine();

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            FileReader fr = new FileReader("stats.txt");
            BufferedReader bfr = new BufferedReader(fr);
            String line;
            while ((line = bfr.readLine()) != null)
                out.println(line);
            bfr.close();
            br.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
