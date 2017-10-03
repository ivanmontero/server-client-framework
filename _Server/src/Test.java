import java.util.LinkedList;
import java.util.Queue;

public class Test {
    public static void main(String[] args) {
        ServerManager server = new ServerManager();
        server.connect();
        while(server.isConnected()) {
            if(server.hasPacket()) {
                String msg = (String) server.getPacket();
                System.out.println(msg);
                server.sendPacket(msg);
                if(msg.equals("stop server")) {
                    server.disconnect();
                }
            }
        }
    }
}
