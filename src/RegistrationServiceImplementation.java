import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.concurrent.ConcurrentHashMap;

/* Author : Serafino Gabriele 564411
 * Brief : RMI registration service implementation
 */

public class RegistrationServiceImplementation extends RemoteServer implements RegistrationService
{
    private int lastUserId;
    private ConcurrentHashMap<String, User> users;

    public RegistrationServiceImplementation(ConcurrentHashMap<String, User> users, int lastUserId)
    {
        this.users = users;
        this.lastUserId = lastUserId;
    }

    private ErrorCode getErrorCode(String username, String password)
    {
        ErrorCode result = ErrorCode.NO_ERROR;
        result = username.equals("") ? ErrorCode.EMPTY_USERNAME : result;
        result = password.equals("") ? ErrorCode.EMPTY_PASSWORD : result;
        return result;
    }

    @Override
    public ErrorCode register(String username, String password) throws RemoteException
    {
        ErrorCode errorCode = getErrorCode(username, password);
        if(errorCode == ErrorCode.NO_ERROR)
        {
            //The put operation needs to be synchronized to avoid that two users get the same username
            synchronized (users)
            {
                if(!users.containsKey(username))
                {
                    users.put(username, new User(++lastUserId, username, password));
                    Server.log("LOG", "Registered user : " + username);
                    Server.backupService.backup();
                }
                else
                {
                    errorCode = ErrorCode.USERNAME_ALREADY_IN_USE;
                }
            }
        }
        return errorCode;
    }
}
