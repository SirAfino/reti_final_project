import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

/* Author : Serafino Gabriele 564411
 * Brief : RMI service for the user registration
 */

public interface RegistrationService extends Remote, Serializable
{
    ErrorCode register(String username, String password)  throws RemoteException;
}
