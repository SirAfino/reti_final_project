import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/* Author : Serafino Gabriele 564411
 * Brief : The backup service loads and saves the user data on the permanent storage in a file called users.dat
 *         The save function is called every X seconds, where X can be specified in the configuration file
 */

public class BackupService extends TimerTask
{
    private ConcurrentHashMap<String, User> users;
    private String path;
    private boolean flag;

    public BackupService(ConcurrentHashMap<String, User> users, String path)
    {
        this.users = users;
        this.path = path;
        flag = false;
    }

    public void notifyChange()
    {
        this.flag = true;
    }

    public void backup()
    {
        JSONArray usersJSONArray = new JSONArray();
        synchronized (users)
        {
            for(User user : users.values())
            {
                usersJSONArray.add(user.toJSON());
            }
        }
        JSONObject usersJSONObject = new JSONObject();
        usersJSONObject.put("users", usersJSONArray);
        File file = new File(path);
        file.renameTo(new File("old.json"));
        FileInteraction.write(usersJSONObject.toString().getBytes(), path);
        new File("old.json").delete();
    }

    public int load()
    {
        int lastUserId = 0;
        JSONParser parser = new JSONParser();
        byte[] data = FileInteraction.read(Server.usersJSONPath);
        if(data != null)
        {
            String usersJSONString = new String(data);
            try {
                JSONObject usersJSONObject = (JSONObject) parser.parse(usersJSONString);
                JSONArray usersJSONArray = (JSONArray) usersJSONObject.get("users");
                for(Object userJSON : usersJSONArray)
                {
                    User user = new User((JSONObject) userJSON);
                    users.put(user.getUsername(), user);
                    lastUserId = Math.max(lastUserId, user.getId());
                }
                Server.log("INIT", "Loaded backup (" + users.size() + " users)");
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return lastUserId;
    }

    @Override
    public void run() {
        if(flag)
        {
            flag = false;
            backup();
            Server.log("LOG", "Backup executed");
        }
    }
}
