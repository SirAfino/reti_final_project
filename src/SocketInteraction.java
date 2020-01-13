import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/* Author : Serafino Gabriele 564411
 * Brief : Exposes static methods for reading and writing data from and to a socket (TCP and UDP)
 */

public class SocketInteraction
{
    public static String TCPRead(SocketChannel channel, ByteBuffer byteBuffer)
    {
        byteBuffer.clear();
        boolean crash = false;
        String message;
        do{
            message = "";
            int messageLength;
            try {
                while (byteBuffer.position() < 4 && !crash)
                {
                    if(channel.read(byteBuffer) == -1)
                    {
                        crash = true;
                        message = "_crash";
                    }
                }
                if(!crash)
                {
                    byteBuffer.flip();
                    messageLength = byteBuffer.getInt();
                    byteBuffer.compact();
                    while(message.length() < messageLength)
                    {
                        channel.read(byteBuffer);
                        byteBuffer.flip();
                        byte[] data = new byte[byteBuffer.capacity()];
                        int toRead = Math.min(messageLength, byteBuffer.limit());
                        byteBuffer.get(data, 0, toRead);
                        message += new String(data, 0, toRead);
                        byteBuffer.compact();
                    }
                }
            } catch (Exception e) { e.printStackTrace(); message = null;}
        }while(message != null && message.equals("ping") && !crash);
        return message;
    }

    public static boolean TCPWrite(SocketChannel channel, ByteBuffer byteBuffer, String message)
    {
        boolean result = false;
        try{
            byte[] data = message.getBytes();
            int messageLength = message.length();
            int sent = 0;
            byteBuffer.clear();
            byteBuffer.putInt(messageLength);
            byteBuffer.flip();
            while(byteBuffer.hasRemaining())
            {
                channel.write(byteBuffer);
            }
            byteBuffer.clear();
            while(sent < messageLength)
            {
                int toSend = Math.min(messageLength - sent, byteBuffer.capacity());
                byteBuffer.put(data, sent, toSend);
                sent += toSend;
                byteBuffer.flip();
                while (byteBuffer.hasRemaining())
                {
                    channel.write(byteBuffer);
                }
            }
            result = true;
        } catch (Exception e) { }
        return result;
    }

    public static void UDPWrite(DatagramSocket datagramSocket, String address, int port, String message)
    {
        try
        {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length);
            packet.setAddress(InetAddress.getByName(address));
            packet.setPort(port);
            packet.setData(data, 0, data.length);
            packet.setLength(data.length);
            datagramSocket.send(packet);
        }
        catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static DatagramPacket UDPRead(DatagramSocket datagramSocket)
    {
        byte[] receiveBuffer = new byte[256];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        try {
            datagramSocket.receive(receivePacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return receivePacket;
    }
}
