/* Author : Serafino Gabriele 564411
 * Brief : Represents a leaderboardUser item, containing the username and the score of a user
 */

import org.json.simple.JSONObject;

public class LeaderboardUser
{
    private String username;
    private int score;

    public LeaderboardUser(String username, int score)
    {
        this.username = username;
        this.score = score;
    }

    public LeaderboardUser(JSONObject jsonObject)
    {
        this.username = (String) jsonObject.get("username");
        this.score = (int) ((long) jsonObject.get("score"));
    }

    public String toString()
    {
        return username + " - Score " + score;
    }

    public int getScore()
    {
        return score;
    }

    public String getUsername()
    {
        return username;
    }
}
