import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Vector;

/* Author : Serafino Gabriele 564411
 * Brief : Represents a user, eventually with his associated match pointer
 */

public class User implements Serializable
{
    private int id;
    private String username;
    private String password;
    private int score;
    private UserState state;
    private Vector<String> friends;
    private String response;
    private SelectionKey key;
    private int sidePort;
    private Match match;

    public User(int id, String username, String password)
    {
        this.id = id;
        this.username = username;
        this.password = password;
        this.score = 0;
        this.state = UserState.OFFLINE;
        this.friends = new Vector<>();
        this.response = null;
        this.match = null;
        this.key = null;
    }

    private boolean isOnline()
    {
        boolean result = false;
        synchronized (this)
        {
            if(this.state != UserState.OFFLINE && key.channel() != null)
            {
                result = SocketInteraction.TCPWrite((SocketChannel) key.channel(), ByteBuffer.allocate(10), "ping");
            }
        }
        return result;
    }

    public String login(String password, SelectionKey key, int port)
    {
        String response;
        if(!password.equals(this.password))
        {
            response = "login_r ko " + ErrorCode.WRONG_PASSWORD;
        }
        else if(isOnline())
        {
            response = "login_r ko " + ErrorCode.ALREADY_LOGGED;
        }
        else
        {
            response = "login_r ok";
            this.state = UserState.LOGGED;
            this.key = key;
            this.sidePort = port;
            key.attach(this);
            Server.log("LOG", "Login from : " + username);
        }
        return response;
    }

    public String logout()
    {
        this.state = UserState.OFFLINE;
        this.key.attach(null);
        this.key = null;
        Server.log("LOG", "Logout from : " + this.username);
        return "logout_r ok";
    }

    public void setResponse(String response)
    {
        this.response = response;
    }

    public int getSidePort()
    {
        return sidePort;
    }

    public SelectionKey getKey()
    {
        return key;
    }

    public Match getMatch()
    {
        return match;
    }

    public String getResponse()
    {
        return response;
    }

    public User(JSONObject jsonObject)
    {
        this.id = (int)((long) jsonObject.get("id"));
        this.username = (String) jsonObject.get("username");
        this.password = (String) jsonObject.get("password");
        this.score = (int) ((long) jsonObject.get("score"));
        this.state = UserState.OFFLINE;

        JSONArray friendsJSON = (JSONArray) jsonObject.get("friends");
        friends = new Vector<>();
        for(Object friend : friendsJSON)
        {
            friends.add((String) friend);
        }
    }

    public int getId()
    {
        return id;
    }

    public String getUsername()
    {
        return username;
    }

    public UserState getState()
    {
        return state;
    }

    public int getScore()
    {
        return score;
    }

    public void setState(UserState state)
    {
        this.state = state;
    }

    public JSONObject toJSON()
    {
        JSONObject result = new JSONObject();
        result.put("id", id);
        result.put("username", username);
        result.put("password", password);
        result.put("score", score);

        JSONArray friendsJSON = new JSONArray();
        for(String friend : friends)
        {
            friendsJSON.add(friend);
        }

        result.put("friends", friendsJSON);
        return result;
    }

    public boolean isFriendOf(String friend)
    {
        return friends.contains(friend);
    }

    public String addFriend(String friend, boolean isRequester)
    {
        String response = "add_friend_r ko " + ErrorCode.ALREADY_FRIEND;
        if(!isRequester)
        {
            friends.add(friend);
            Server.backupService.notifyChange();
            response = null;
        }
        else if(!friends.contains(friend))
        {
            response = "add_friend_r ok";
            friends.add(friend);
            Server.users.get(friend).addFriend(this.username, false);
            Server.log("LOG", "Add friend from : " + this.username + " (other: " + friend + ")");
        }
        return response;
    }

    public String getFriendsJSON()
    {
        String response;
        String[] friends;

        synchronized (this)
        {
            friends = this.friends.toArray(new String[this.friends.size()]);
        }

        JSONArray friendsJSONArray = new JSONArray();
        for(int i=0;i<friends.length;i++)
        {
            friendsJSONArray.add(friends[i]);
        }
        JSONObject friendsJSONObject = new JSONObject();
        friendsJSONObject.put("friends", friendsJSONArray);

        response = "friends_list_r ok\n" + friendsJSONObject;
        Server.log("LOG", "Friends list from : " + this.username);
        return response;
    }

    public String getLeaderboardJSON()
    {
        String response;
        String[] friends;
        synchronized (this)
        {
            friends = this.friends.toArray(new String[this.friends.size()]);
        }

        Leaderboard leaderboard = new Leaderboard();
        leaderboard.addUser(this.username, this.score);
        for(int i=0;i<friends.length;i++)
        {
            User friend = Server.users.get(friends[i]);
            leaderboard.addUser(friend.getUsername(), friend.getScore());
        }

        response = "leaderboard_r ok\n" + leaderboard.toJSON();
        Server.log("LOG", "Leaderboard from : " + this.username);
        return response;
    }

    public void addPoints(int points)
    {
        score += points;
    }

    public String getAddress()
    {
        String remoteAddress = null;
        try{
            if(key != null)
            {
                remoteAddress = ((SocketChannel)key.channel()).getRemoteAddress().toString();
                remoteAddress = remoteAddress.substring(remoteAddress.indexOf("/") + 1, remoteAddress.indexOf(":"));

            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return remoteAddress;
    }

    public void setMatch(Match match)
    {
        this.match = match;
        this.state = match != null ? UserState.MATCH_STARTED : this.state;
    }

    public String surrend()
    {
        synchronized (this.match)
        {
            this.match.setSurrended(this);
            synchronized (this)
            {
                this.state = UserState.LOGGED;
                this.match = null;
            }
        }
        return "surrend_r ok";
    }
}
