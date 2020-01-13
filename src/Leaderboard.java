import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.util.ArrayList;

/* Author : Serafino Gabriele 564411
 * Brief : Represents a leaderboard item, a collection of ordered users based on their scores
 */

public class Leaderboard
{
    private ArrayList<LeaderboardUser> users;

    public Leaderboard()
    {
        users = new ArrayList<>();
    }

    public Leaderboard(JSONObject jsonObject)
    {
        users = new ArrayList<>();
        JSONArray leaderboardJSONArray = (JSONArray) jsonObject.get("leaderboard");
        for(int i=0;i<leaderboardJSONArray.size();i++)
        {
            JSONObject userJSON = (JSONObject) leaderboardJSONArray.get(i);
            users.add(new LeaderboardUser(userJSON));
        }
    }

    //Adds the new user in the correct position, keeping the list ordered
    public void addUser(String username, int score)
    {
        int index = 0;
        while(index < users.size() && users.get(index).getScore() > score)
        {
            index++;
        }
        users.add(index, new LeaderboardUser(username, score));
    }

    public JSONObject toJSON()
    {
        JSONArray leaderboardJSONArray = new JSONArray();
        for(int i=0;i<users.size();i++)
        {
            LeaderboardUser user = users.get(i);
            JSONObject userJSON = new JSONObject();
            userJSON.put("username", user.getUsername());
            userJSON.put("score", user.getScore());
            leaderboardJSONArray.add(userJSON);
        }
        JSONObject leaderboardJSONObject = new JSONObject();
        leaderboardJSONObject.put("leaderboard", leaderboardJSONArray);
        return leaderboardJSONObject;
    }

    public LeaderboardUser[] getUsers()
    {
        return users.toArray(new LeaderboardUser[0]);
    }
}
