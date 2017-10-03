import java.util.Scanner;

public class Test {
    public static void main(String[] args) {
        ClientManager client = new ClientManager();
        client.connect();
        Scanner input = new Scanner(System.in);
        Thread server = new Thread(new Runnable() {
            @Override
            public void run() {
                while(!Thread.currentThread().isInterrupted()) {
                    if(client.hasPacket()) {
                        System.out.println((String) client.getPacket());
                    }
                }
                client.disconnect();
            }
        });
        server.start();
        while(true) {
            String msg;
            msg = input.nextLine();
            if(msg.equals("stop")) {
                break;
            }
            client.sendPacket(msg);
        }
        server.interrupt();

    }



}
