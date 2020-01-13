import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.Timer;
import java.util.concurrent.*;

public class Server
{
    static String configPath = "../server_config.properties";
    static String usersJSONPath = "users.json";

    private static int registryPort;
    private static int mainPort;
    public static int sidePort;
    private static String translationServerAddress;
    private static String dictionaryPath;
    private static int threadPoolSize;
    private static int backupTime;

    public static int matchRequestTimeout;
    public static int matchWordNumber;
    public static int matchDuration;

    public static int correctWordScore;
    public static int incorrectScore;
    public static int matchWinnerScore;

    public static ConcurrentHashMap<String, User> users;
    public static BackupService backupService;
    public static TranslationService translationService;

    private static boolean run = true;
    public static Selector selector;
    public static DatagramSocket datagramSocket;
    private static ThreadPoolExecutor threadPoolExecutor;
    public static ConcurrentHashMap<Integer, Match> matches;
    public static Integer lastMatchId = 0;
    public static String[] dictionary;
    private static ByteBuffer byteBuffer;

    private static void loadConfigs(String path) throws Exception
    {
        byte[] data = FileInteraction.read(path);
        if(data != null)
        {
            String configContent = new String(data);
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
                        case "registry_port": registryPort = Integer.parseInt(value); break;
                        case "main_port": mainPort = Integer.parseInt(value); break;
                        case "side_port": sidePort = Integer.parseInt(value); break;
                        case "translation_server_address": translationServerAddress = value; break;
                        case "dictionary_path": dictionaryPath = value; break;
                        case "thread_pool_size": threadPoolSize = Integer.parseInt(value); break;
                        case "backup_time": backupTime = Integer.parseInt(value); break;
                        case "match_request_timeout": matchRequestTimeout = Integer.parseInt(value); break;
                        case "match_word_number": matchWordNumber = Integer.parseInt(value); break;
                        case "match_duration": matchDuration = Integer.parseInt(value); break;
                        case "correct_word_score": correctWordScore = Integer.parseInt(value); break;
                        case "incorrect_word_score": incorrectScore = Integer.parseInt(value); break;
                        case "match_winner_score": matchWinnerScore = Integer.parseInt(value); break;
                    }
                }
            }
            log("INIT", "Loaded configs");
        }
        else
        {
            throw new Exception("Config file not found!");
        }
    }

    private static void loadDictionary(String path) throws Exception
    {
        byte[] data = FileInteraction.read(path);
        if(data != null)
        {
            dictionary = new String(FileInteraction.read(path)).split("\r\n");
            log("INIT", "Loaded dictionary (" + dictionary.length + " words)");
        }
        else
        {
            throw new Exception("Dictionary not found!");
        }
    }

    public static void log(String type, String message)
    {
        System.out.println("[" + type + "] " + message);
    }

    private static void createRegistrationService(int lastUserId)
    {
        try {
            RegistrationService registrationService = new RegistrationServiceImplementation(users, lastUserId);
            RegistrationService stub = (RegistrationService) UnicastRemoteObject.exportObject(registrationService, 0);
            LocateRegistry.createRegistry(registryPort);
            Registry registry = LocateRegistry.getRegistry(registryPort);
            registry.bind("RegistrationService", stub);
            log("INIT", "Created registration service");
        }
        catch (RemoteException | AlreadyBoundException e)
        {
            log("ERROR", "Creating registration service");
            e.printStackTrace();
        }
    }

    static void acceptHandler(SelectionKey key)
    {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        try {
            SocketChannel client = serverSocketChannel.accept();
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
            key.attach(null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void readHandler(SelectionKey key)
    {
        SocketChannel client = (SocketChannel) key.channel();
        String request = SocketInteraction.TCPRead(client, byteBuffer);
        if(request != null)
        {
            if(request.equals("_crash"))
            {
                key.cancel();
            }
            else
            {
                request = request.trim().replaceAll(" +", " ");
                key.interestOps(0);
                threadPoolExecutor.execute(new RequestHandler(key, request));
            }
        }
        else
        {
            key.cancel();
        }
    }

    static void writeHandler(SelectionKey key)
    {
        User user = (User) key.attachment();
        SocketChannel client = (SocketChannel) key.channel();
        if(user != null && user.getResponse() != null)
        {
            synchronized (user)
            {
                SocketInteraction.TCPWrite(client, byteBuffer, user.getResponse());
                user.setResponse(null);
                if(user.getId() == 0)
                {
                    //The user object was created just to store the response to a non-logged client
                    key.attach(null);
                }
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    static void shutdownManager()
    {
        log("SHUTDOWN", "Received shutdown command");
        matches.clear();
        backupService.backup();
        log("SHUTDOWN", "Backup executed");
    }

    /* Server configFile
     * configFile (String) - path of the configuration file for the server
     */
    public static void main(String args[])
    {
        //Parsing argument if present
        if(args.length > 0)
        {
            configPath = args[0];
        }

        try{
            //Loading configuration file and dictionary
            loadConfigs(configPath);
            loadDictionary(dictionaryPath);

            //Initializing data structures
            users = new ConcurrentHashMap<>();
            matches = new ConcurrentHashMap<>();

            //Starting Translation, Backup and Registration services
            translationService = new TranslationService(translationServerAddress);
            backupService = new BackupService(users, usersJSONPath);
            int lastUserId = backupService.load();
            new Timer().scheduleAtFixedRate(backupService, Server.matchRequestTimeout, Server.matchRequestTimeout);
            createRegistrationService(lastUserId);

            //Opening TCP Socket
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(mainPort));
            serverSocketChannel.configureBlocking(false);
            byteBuffer = ByteBuffer.allocate(1024);

            //Opening UDP Socket
            datagramSocket = new DatagramSocket(sidePort);
            Thread matchNotificationService = new Thread(new MatchNotificationService(datagramSocket));
            matchNotificationService.start();

            //Starting selector
            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            //Starting thread pool
            threadPoolExecutor = new ThreadPoolExecutor(threadPoolSize, threadPoolSize, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownManager()));

            log("INIT", "Server ready");
            while(run)
            {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while(iterator.hasNext())
                {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if(key.isAcceptable()) { acceptHandler(key); }
                    else if(key.isReadable()) { readHandler(key); }
                    else if(key.isWritable()) { writeHandler(key); }
                }
            }
        } catch (Exception e)
        {
            log("ERROR", e.getMessage());
            e.printStackTrace();
        }
    }
}
