import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Timer;
import java.util.TimerTask;

/* Author : Serafino Gabriele 564411
 * Brief : Processes a single request sent from a user
 */

public class RequestHandler implements Runnable
{
    private SelectionKey key;
    private String request;
    private int ops;

    public RequestHandler(SelectionKey key, String request)
    {
        this.key = key;
        this.request = request;
    }

    private String basicControls(SelectionKey key, String request, int expectedParameters, UserState expectedState)
    {
        String parameters[] = request.split(" ");
        ErrorCode errorCode = null;
        if(key.attachment() == null)
        {
            errorCode = ErrorCode.NOT_LOGGED;
        }
        else if(((User) key.attachment()).getState() != expectedState)
        {
            errorCode = ErrorCode.NOT_PERMITTED;
        }
        else if(parameters.length < expectedParameters + 1)
        {
            errorCode = ErrorCode.BAD_REQUEST;
        }
        return errorCode == null ? null : parameters[0] + "_r ko " + errorCode;
    }

    private void setUserResponse(SelectionKey key, String response)
    {
        if(key.attachment() == null)
        {
            key.attach(new User(0, "", ""));
        }
        ((User) key.attachment()).setResponse(response);
    }

    private String loginControls(SelectionKey key, String request)
    {
        ErrorCode errorCode = null;
        if(key.attachment() != null)
        {
            errorCode =  ErrorCode.ALREADY_LOGGED;
        }
        else if (request.split(" ").length != 4)
        {
            errorCode = ErrorCode.BAD_REQUEST;
        }
        return errorCode == null ? null : "login_r ko " + errorCode;
    }

    private void loginHandler(SelectionKey key, String request)
    {
        String response = loginControls(key, request);
        if(response == null)
        {
            String[] parameters = request.split(" ");
            String username = parameters[1];
            String password = parameters[2];
            int port = Integer.parseInt(parameters[3]);
            User user = Server.users.get(username);
            if(user == null)
            {
                response = "login_r ko " + ErrorCode.WRONG_USERNAME;
            }
            else synchronized (user)
            {
                response = user.login(password, key, port);
            }
        }
        setUserResponse(key, response);
    }

    private void logoutHandler(SelectionKey key, String request)
    {
        String response = basicControls(key, request, 0, UserState.LOGGED);
        if(response == null)
        {
            response = ((User) key.attachment()).logout();
        }
        setUserResponse(key, response);
    }

    private void scoreHandler(SelectionKey key, String request)
    {
        String response = basicControls(key, request, 0, UserState.LOGGED);
        if(response == null)
        {
            User user = (User) key.attachment();
            response = "score_r ok " + user.getScore();
            Server.log("LOG", "Score from : " + user.getUsername());
        }
        setUserResponse(key, response);
    }

    private void addFriendHandler(SelectionKey key, String request)
    {
        String response = basicControls(key, request, 1, UserState.LOGGED);
        if(response == null)
        {
            User user = (User) key.attachment();
            String[] parameters = request.split(" ");
            String friend = parameters[1];
            synchronized (user)
            {
                if(!Server.users.containsKey(friend))
                {
                    response = "add_friend_r ko " + ErrorCode.WRONG_USERNAME;
                }
                else
                {
                    response = user.addFriend(friend, true);
                }
            }
        }
        setUserResponse(key, response);
    }

    private void friendsListHandler(SelectionKey key, String request)
    {
        String response = basicControls(key, request, 0, UserState.LOGGED);
        if(response == null)
        {
            response = ((User) key.attachment()).getFriendsJSON();
        }
        setUserResponse(key, response);
    }

    private void leaderboardHandler(SelectionKey key, String request)
    {
        String response = basicControls(key, request, 0, UserState.LOGGED);
        if(response == null)
        {
            response = ((User) key.attachment()).getLeaderboardJSON();

        }
        setUserResponse(key, response);
    }

    private void unknownHandler(SelectionKey key)
    {
        setUserResponse(key, "unknown_r");
    }

    private void matchHandler(SelectionKey key, String request)
    {
        String response = basicControls(key, request, 1, UserState.LOGGED);
        if(response == null) //No error in the request
        {
            User user = (User) key.attachment();
            String[] parameters = request.split(" ");
            String friendUsername = parameters[1];
            if(!Server.users.containsKey(friendUsername))
            {
                response = "match_r ko " + ErrorCode.WRONG_USERNAME;
            }
            else if(!user.isFriendOf(friendUsername))
            {
                response = "match_r ko " + ErrorCode.NOT_FRIEND;
            }
            else
            {
                User friend = Server.users.get(friendUsername);
                if(friend.getState() == UserState.OFFLINE)
                {
                    response = "match_r ko " + ErrorCode.USER_OFFLINE;
                }
                else if(friend.getState() != UserState.LOGGED)
                {
                    response = "match_r ko " + ErrorCode.USER_BUSY;
                }
                else
                {
                    response = null;
                    ops = 0;
                    new Match(Server.lastMatchId, user, friend);
                }
            }
        }
        setUserResponse(key, response);
    }

    private void matchInfoHandler(SelectionKey key, String request)
    {
        String response = basicControls(key, request, 0, UserState.MATCH_STARTED);
        if(response == null)
        {
            User user = (User) key.attachment();
            Match match = user.getMatch();
            synchronized (match)
            {
                ops = 0;
                if(match.setInfoRequested(user))
                {
                    match.start();
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            match.notifyTimeout("match");
                        }
                    },Server.matchDuration);
                    response = "match_info_r ok " + Server.matchWordNumber + " " + Server.matchDuration + " " + match.getWord(0);
                    for(int i=0;i<2;i++)
                    {
                        User u = match.getUser(i);
                        synchronized (u)
                        {
                            u.setResponse(response);
                            u.getKey().interestOps(SelectionKey.OP_WRITE);
                            u.setState(UserState.MATCH_PLAYING);
                        }
                    }
                    ops = SelectionKey.OP_WRITE;
                }
            }
        }
        setUserResponse(key, response);
    }

    private void translationHandler(SelectionKey key, String request)
    {
        String response = basicControls(key, request, 1, UserState.MATCH_PLAYING);
        if(response == null)
        {
            User user = (User) key.attachment();
            String translatedWord = request.substring(request.indexOf(" ") + 1);
            response = user.getMatch().addUserTranslation(user, translatedWord);
        }
        setUserResponse(key, response);
    }

    private void matchResultsHandler(SelectionKey key, String request)
    {
        String response = basicControls(key, request, 0, UserState.MATCH_FINISHED);
        if(response == null)
        {
            User user = (User) key.attachment();
            synchronized (user)
            {
                Match match = user.getMatch();
                if(match != null)
                {
                    synchronized (match)
                    {
                        response = match.setResultsRequested(user);
                        ops = response == null ? 0 : SelectionKey.OP_WRITE;
                    }
                }
            }
        }
        setUserResponse(key, response);
    }

    private void surrendHandler(SelectionKey key, String request)
    {
        String response = basicControls(key, request, 0, UserState.MATCH_PLAYING);
        if(response == null)
        {
            response = ((User) key.attachment()).surrend();
        }
        setUserResponse(key, response);
    }

    @Override
    public void run()
    {
        ops = SelectionKey.OP_WRITE;
        switch (request.split(" ")[0])
        {
            case "login": loginHandler(key, request); break;
            case "logout": logoutHandler(key, request); break;
            case "score": scoreHandler(key, request); break;
            case "add_friend": addFriendHandler(key, request); break;
            case "friends_list": friendsListHandler(key, request); break;
            case "leaderboard": leaderboardHandler(key, request); break;
            case "match": matchHandler(key, request); break;
            case "match_info": matchInfoHandler(key, request); break;
            case "translation": translationHandler(key, request); break;
            case "match_results": matchResultsHandler(key, request); break;
            case "surrend": surrendHandler(key, request); break;
            default: unknownHandler(key);
        }
        key.interestOps(ops);
        Server.selector.wakeup();
    }
}