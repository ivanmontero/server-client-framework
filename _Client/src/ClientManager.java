import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ClientManager {
    private volatile Socket socket;
    private volatile ObjectInputStream socketIn;
    private volatile ObjectOutputStream socketOut;
    private volatile boolean isConnected;

    private ServerListener tServerListener;
    private Queue<Object> packets;
    private String serverIP;
    private int port;
    private PacketHandler packetHandler;

    //TODO: Generalize class to specify packet type?
    //TODO: Make ClientManager static? no
    //TODO: Make connect and start into one method
    //TODO: Work on shutting down normally
    //TODO: Disconnecting

    public ClientManager() {
        this("localhost", 1337);
    }

    public ClientManager(String ip) {
        this(ip, 1337);
    }

    public ClientManager(String ip, int port) {
        packets = new ConcurrentLinkedQueue<Object>();
        this.serverIP = ip;
        this.port = port;
    }

    public boolean connect() {
        if(isConnected)
            return true;
        try {
            socket = new Socket(serverIP, port);
            socketIn = new ObjectInputStream(socket.getInputStream());
            socketOut = new ObjectOutputStream(socket.getOutputStream());
        } catch (Exception e) {
            System.err.println("Failed to connect to server");
            return false;
        }
        System.out.println("Connection accepted.");
        isConnected = true;
        tServerListener = new ServerListener();
        tServerListener.start();
        return true;
    }

    public void sendPacket(Object packet) {
        if(!(packet instanceof Serializable)) {
            System.err.println("Packet does not implement Serializable interface");
            return;
        }
        if(isConnected) {
            try {
                socketOut.writeUnshared(packet);
                socketOut.reset();
            } catch (Exception e) {
                System.err.println("Connection lost to server");
                disconnect();
            }
        }
    }

    public boolean hasPacket() {
        return !packets.isEmpty();
    }

    public Object getPacket() {
        return packets.remove();
    }

    public void disconnect() {    // Gets called by various threads
        if(tServerListener != null)
            tServerListener.interrupt();
        if(socket != null) {
            try {
                socket.close();
                socket = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        isConnected = false;
    }

    public void setServerIP(String ip) {
        this.serverIP = ip;
    }

    public void setServerPort(int port) {
        this.port = port;
    }

    public boolean isConnected()  {
        return isConnected;
    }

    // null -> Sequential, packetHandler -> Concurrent
    public void setPacketHandler(PacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    public static String getPublicIP() {
        try {
            URL whatsMyIP = new URL("http://checkip.amazonaws.com");
            return (new BufferedReader(new InputStreamReader(whatsMyIP.openStream()))).readLine();
        } catch(Exception e) {
            return "UNKNOWN";
        }
    }

    private class ServerListener extends Thread {
        public ServerListener() {
            super("Server Listener");
        }

        @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    if(packetHandler == null) {
                        packets.add(socketIn.readObject());
                    } else {
                        packetHandler.execute(socketIn.readObject());
                    }
                } catch (Exception e) {
                    System.out.println("Connection lost to server");
                    ClientManager.this.disconnect();
                }
            }
        }
    }

    public interface PacketHandler {
        void execute(Object packet);
    }

    private class Packet {
        public static final int CONNECTING = 0;
        public static final int NORMAL = 1;
        public static final int DISCONNECTING = 2;

        public final int TYPE;
        public final Object DATA;

        public Packet(int packetType, Object data) {
            this.TYPE = packetType;
            this.DATA = data;
        }
    }
}
