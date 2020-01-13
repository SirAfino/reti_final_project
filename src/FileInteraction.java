import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/* Author : Serafino Gabriele 564411
 * Brief : Exposes two static methods for reading and writing data from and to a file
 */

public class FileInteraction
{
    public static byte[] read(String filePath)
    {
        byte[] result = null;
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate((int) new File(filePath).length());
            FileChannel inChannel = FileChannel.open(Paths.get(filePath), StandardOpenOption.READ);
            while(byteBuffer.position() < byteBuffer.limit() - 1)
            {
                inChannel.read(byteBuffer);
            }
            byteBuffer.flip();
            result = byteBuffer.array();
            inChannel.close();
        } catch (IOException e) { }
        return result;
    }

    public static void write(byte[] data, String filePath)
    {
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(data);
            FileChannel outChannel = FileChannel.open(Paths.get(filePath), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
            while(byteBuffer.hasRemaining())
            {
                outChannel.write(byteBuffer);
            }
            outChannel.close();
        } catch (IOException e) { e.printStackTrace(); }
    }
}