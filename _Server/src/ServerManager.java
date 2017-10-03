import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ServerManager {
    private static int currentClientID = 0;
    private Map<Integer, ClientListener> tClientListeners;
    private Queue<Object> packets;
    private ServerSocket serverSocket;
    private Thread tClientConnect;
    //private String ip;
    private int port;
    private boolean isConnected;
    private PacketHandler packetHandler;

    //TODO: Throw exception if methods are called and server isn't connected
    //TODO: Work on shutting down normally
    //TODO: Create packet class

    public ServerManager() {
        this(1337);
    }

    public ServerManager(int port) {
        this.tClientListeners = new ConcurrentHashMap<Integer, ClientListener>(32, 0.75f, 100);
        this.packets = new ConcurrentLinkedQueue<Object>();
        this.tClientConnect = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Client connect thread started.");
                while(!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket socket = serverSocket.accept();
                        socket.setPerformancePreferences(0, 2, 1);
                        ClientListener cl = new ClientListener(socket, currentClientID);
                        System.out.println("Client ID:" + currentClientID + " ("
                                + socket.getLocalAddress().getHostAddress() + ") connected.");
                        cl.start();
                        tClientListeners.put(currentClientID, cl);
                        currentClientID++;
                    } catch (Exception e) {
                        //e.printStackTrace();
                        Thread.currentThread().interrupt();
                    }
                }
                System.out.println("Client connect thread terminated");
            }
        }, "Client Connect");
        this.port = port;
    }

    public boolean connect() {
        if(isConnected)
            return true;
        try {
            serverSocket = new ServerSocket(port);
        } catch (Exception e) {
            System.err.println("Failed to bind to port " + port);
            return false;
        }
        serverSocket.setPerformancePreferences(0, 2, 1);
        System.out.println("Successfully bound to port " + port);
        tClientConnect.start();
        System.out.println("Server listening on " + getPublicIP() + ":" + port);
        isConnected = true;
        return true;
    }

    public void sendPacket(Object packet) {
        for(int id : tClientListeners.keySet()) {
            sendPacket(packet, id);
        }
    }

    public void sendPacket(Object packet, int id) {
        if(!(packet instanceof Serializable)) { //Possible check in the client listener?
            System.err.println("Packet does not implement Serializable interface");
            return;
        }
        if(isConnected) {
            ClientListener cl = tClientListeners.get(id);
            if(!cl.sendPacket(packet)) {
                tClientListeners.remove(id);
            }
        }
    }

    // For use with sequential packet handling (not concurrent)
    public boolean hasPacket() {
        return !packets.isEmpty();
    }

    public Object getPacket() {
        if(packets.isEmpty()) {
            throw new NoSuchElementException("There are no packets left");
        }
        return packets.remove();
    }

    public void disconnect() {
        disconnectAll();
        if(!serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        serverSocket = null;
        packets.clear();
        isConnected = false;
    }

    public void disconnectAll() {
        for(int id : tClientListeners.keySet()) {
            disconnectClient(id);
        }
    }

    public void disconnectClient(int id) {
        if(tClientListeners.containsKey(id)) {
            tClientListeners.get(id).interrupt();
            tClientListeners.remove(id);
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    // null -> Sequential, packetHandler -> Concurrent
    public void setPacketHandler(PacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    //TODO: Disconnect and Disconnect all

    public static String getPublicIP() {
        try {
            URL whatsMyIP = new URL("http://checkip.amazonaws.com");
            return (new BufferedReader(new InputStreamReader(whatsMyIP.openStream()))).readLine();
        } catch(Exception e) {
            return "UNKNOWN";
        }
    }

    //STOP by interrupting
    private class ClientListener extends Thread {
        //TODO: Store client information
        private Socket socket;
        private ObjectInputStream socketIn;
        private ObjectOutputStream socketOut;
        public final int ID;

        public ClientListener(Socket socket, int id) {
            this.socket = socket;
            this.ID = id;
            try {
                this.socketOut = new ObjectOutputStream(socket.getOutputStream());
                this.socketIn = new ObjectInputStream(socket.getInputStream());
                // TODO: Send initial packets for initialization.
            } catch (Exception e) {
                interrupt();
            }
        }

        @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()) {
                try {
                    Object packet = socketIn.readObject();
                    if(packetHandler == null) {
                        packets.add(packet);
                    } else {
                        packetHandler.execute(packet);
                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                    if(!Thread.currentThread().isInterrupted()) {
                        interrupt();
                    }
                }
            }
        }

        @Override
        public void interrupt() {
            super.interrupt();
            close();
        }

        public boolean sendPacket(Object packet) {
            try {
                socketOut.writeUnshared(packet);
                socketOut.reset();
            } catch (Exception e) {
                interrupt();
                return false;
            }
            return true;
        }

        private void close() {
            if(!socket.isClosed()) {
                try {
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Client ID:" + ID + " (" + socket.getLocalAddress().getHostAddress()
                    + ") disconnected");
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
