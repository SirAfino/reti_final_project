import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/* Author : Serafino Gabriele 564411
 * Brief : Takes care of the communication with the translation server, asks translations with GET requests
 *         and returns the words to the caller
 */

/* It seems that the translation server closes the connection after the first GET even if you put the "keep-alive" parameter
 * so I open a connection for each word i need to translate
 */

public class TranslationService
{
    private static String serverAddress;

    public TranslationService(String serverAddress)
    {
        this.serverAddress = serverAddress;
    }

    /* getResponseContent response
     * Brief : returns the content body of the HTTP response if the response code is a "200 OK"
     */
    private static String getResponseContent(String response)
    {
        String headerDelimiter = "\r\n\r\n";
        String result = null;
        if(response.startsWith("HTTP/1.1 200 OK"))
        {
            result = response.substring(response.indexOf(headerDelimiter) + headerDelimiter.length());
        }
        return result;
    }

    /* parseResponse response
     * Brief : parses the server http response body and returns the translated word
     */
    private static String[] parseResponse(String response)
    {
        String[] result = null;
        try {
            JSONParser jsonParser = new JSONParser();
            JSONObject responseJSON = (JSONObject) jsonParser.parse(response);
            JSONArray matches = (JSONArray) responseJSON.get("matches");
            result = new String[matches.size()];
            for(int i=0;i<matches.size();i++)
            {
                JSONObject match = (JSONObject) matches.get(i);
                result[i] = ((String) match.get("translation")).toLowerCase();
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return result;
    }

    /* getTranslations words
     * Brief : returns a matrix of Strings containing the translated words and their synonyms
     * obtained from the translation server
     */
    public static String[][] getTranslations(String[] words)
    {
        int wordsNumber = words.length;
        String[][] results = new String[wordsNumber][];
        for(int i=0;i<wordsNumber;i++)
        {
            results[i] = null;
        }

        try {
            //Opens the socket channel to the translation server
            SocketAddress socketAddress = new InetSocketAddress(serverAddress, 80);
            ByteBuffer byteBuffer = ByteBuffer.allocate(512);

            for(int i=0;i<wordsNumber;i++)
            {
                SocketChannel socketChannel = SocketChannel.open(socketAddress);
                socketChannel.configureBlocking(true);

                //Creates the request string and sends it in a buffer
                String word = words[i];
                String request = "GET /get?q=" + word + "&langpair=it|en HTTP/1.1\r\n" +
                                "Connection: close\r\n" +
                                "Host: " + serverAddress + "\r\n\r\n";
                byteBuffer.put(request.getBytes());
                byteBuffer.flip();
                socketChannel.write(byteBuffer);
                byteBuffer.clear();

                //Reads the response from the server
                String response = "";
                while(socketChannel.read(byteBuffer) > 0)
                {
                    byteBuffer.flip();
                    response += new String(byteBuffer.array(), 0, byteBuffer.limit());
                    byteBuffer.clear();
                }

                socketChannel.close();

                //Parses the response
                String responseContent = getResponseContent(response);
                if(responseContent != null)
                {
                    results[i] = parseResponse(responseContent);
                    if(results[i] == null)
                    {
                        Server.log("WARNING", "No translations found for : " + word);
                    }
                }
            }
        }
        catch (IOException e) { e.printStackTrace(); }
        return results;
    }
}