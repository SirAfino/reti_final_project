import java.net.DatagramPacket;
import java.net.DatagramSocket;

/* Author : Serafino Gabriele 564411
 * Brief : This service keeps listening on the UDP Socket for asynchronous match request responses from the users
 */

public class MatchNotificationService implements Runnable
{
    DatagramSocket datagramSocket;

    public MatchNotificationService(DatagramSocket datagramSocket)
    {
        this.datagramSocket = datagramSocket;
    }

    private void processMessage(String message)
    {
        String[] parameters = message.split(" ");
        if(parameters[0].equals("match_request_r"))
        {
            Integer matchId = Integer.parseInt(parameters[1]);
            String response = parameters[2];
            Match match = Server.matches.get(matchId);
            if(match != null)
            {
                synchronized (match)
                {
                    if(response.equals("rejected"))
                    {
                        match.notifyRejected("voluntarily");
                    }
                    else if(response.equals("accepted"))
                    {
                        match.notifyAccepted();
                    }
                }
            }
        }
    }

    @Override
    public void run()
    {
        while(true)
        {
            DatagramPacket receivePacket = SocketInteraction.UDPRead(Server.datagramSocket);
            processMessage(new String(receivePacket.getData(), 0, receivePacket.getLength()));
        }
    }
}