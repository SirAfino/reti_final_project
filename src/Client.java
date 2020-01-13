import java.io.IOException;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Client
{
    private static String configPath = "../client_config.properties";
    public static String username;

    public static RegistrationService registrationService;
    public static DatagramSocket datagramSocket;
    public static SocketChannel socketChannel;
    public static String serverAddress;
    private static int registryPort;
    private static int mainPort;
    public static int localSidePort;

    public static LoginForm loginForm;
    public static ProfileForm profileForm;
    public static MatchForm matchForm;

    private static void loadConfigs(String path)
    {
        String configContent = new String(FileInteraction.read(path));
        String[] lines = configContent.split("\r\n");
        for(String line : lines)
        {
            if(!line.equals("") && !line.startsWith("#"))
            {
                int equalPosition = line.indexOf("=");
                String parameter = line.substring(0, equalPosition - 1);
                String value = line.substring(equalPosition + 2);

                switch (parameter)
                {
                    case "server_address": serverAddress = value; break;
                    case "registry_port": registryPort = Integer.parseInt(value); break;
                    case "main_port": mainPort = Integer.parseInt(value); break;
                }
            }
        }
        log("INIT", "Loaded configs");
    }

    public static void log(String type, String message)
    {
        System.out.println("[" + type + "] " + message);
    }

    public static boolean openTCPConnection()
    {
        boolean result = false;
        try {
            SocketAddress address = new InetSocketAddress(serverAddress, mainPort);
            socketChannel = SocketChannel.open(address);
            socketChannel.configureBlocking(false);
            log("INIT", "Opened TCP connection");
            result = true;
        } catch (IOException e) {
            log("ERROR", "Cannot connect to server!");
        }
        return result;
    }

    public static void openUDPConnection()
    {
        try {
                datagramSocket = new DatagramSocket();
                localSidePort = datagramSocket.getLocalPort();
                log("INIT", "Opened UDP connection");
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    /* Client configFile
     * configFile (String) - path of the configuration file for the client
     */
    public static void main(String args[]) throws IOException, NotBoundException
    {
        //Parsing argument if present
        if(args.length > 0)
        {
            configPath = args[0];
        }
        
        loadConfigs(configPath);

        Registry registry = LocateRegistry.getRegistry(serverAddress, registryPort);
        try{
            registrationService = (RegistrationService) registry.lookup("RegistrationService");
            loginForm = new LoginForm();
        } catch (Exception e)
        {
            log("ERROR", "Cannot connect to server!");
        }
    }
}
