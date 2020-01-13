/* Author : Serafino Gabriele 564411
 * Brief : Represents a match between two users, keeps track of the progress of each user and the match
 */

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class Match
{
    private int id;
    private MatchState state;
    private User[] users;
    private boolean[] info_requested;
    private String[] words;
    private String[][] translations;
    private int[] positions;
    private int[] scores;
    private boolean[] results_requested;
    private int[] correctAnswers;
    private boolean[] surrended;

    public Match(int id, User user1, User user2)
    {
        this.id = id;
        this.users = new User[]{user1, user2};
        this.state = MatchState.PENDING;
        this.info_requested = new boolean[]{false, false};
        this.positions = new int[]{0, 0};
        this.scores = new int[]{0, 0};
        this.results_requested = new boolean[]{false, false};
        this.correctAnswers = new int[]{0, 0};
        this.surrended = new boolean[]{false, false};

        synchronized (Server.lastMatchId)
        {
            Server.lastMatchId++;
            String message = "match_request " + user1.getUsername() + " " + Server.lastMatchId + " " + Server.matchRequestTimeout;
            SocketInteraction.UDPWrite(Server.datagramSocket, user2.getAddress(), user2.getSidePort(), message);
            Server.matches.put(Server.lastMatchId, this);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    notifyTimeout("match_request");
                }
            }, Server.matchRequestTimeout);
        }
        Server.log("LOG", "Match from : " + user1.getUsername() + " (other: " + user2.getUsername() + ")");
    }

    public void start()
    {
        HashSet<String> words = new HashSet<>();
        Random random = new Random();
        while(words.size() < Server.matchWordNumber)
        {
            words.add(Server.dictionary[random.nextInt(Server.dictionary.length)]);
        }
        this.words = words.toArray(new String[0]);
        this.translations = Server.translationService.getTranslations(this.words);
        this.state = MatchState.STARTED;
    }

    public void notifyRejected(String mode)
    {
        if(this.state == MatchState.PENDING)
        {
            Server.log("LOG", "Match rejected (" + mode + ") from : " + users[1].getUsername() + " (other: " + users[0].getUsername() + ", match: " + id + ")");
            Server.matches.remove(this.id);
            this.state = MatchState.FINISHED;
            setUserResponse(users[0], "match_r ok rejected");
        }
    }

    public void notifyTimeout(String timeoutType)
    {
        synchronized (this)
        {
            if(timeoutType.equals("match_request") && this.state == MatchState.PENDING)
            {
                Server.log("TIMEOUT", "Match request timeout (match: " + this.id + ")");
                notifyRejected("timeout");
            }
            else if(timeoutType.equals("match") && state == MatchState.STARTED)
            {
                state = MatchState.FINISHED;
                Server.log("TIMEOUT", "Match timeout (match: " + this.id + ")");
                for (int i=0; i<2; i++)
                {
                    users[i].setState(UserState.MATCH_FINISHED);
                    if (!SocketInteraction.TCPWrite((SocketChannel) users[i].getKey().channel(),
                            ByteBuffer.allocate(10), "ping"))
                    {
                        users[i].setState(UserState.OFFLINE);
                        users[i].getKey().cancel();
                        setSurrended(users[i]);
                        Server.log("CRASH", users[i].getUsername());
                    }
                }
            }
        }
    }

    public void notifyAccepted()
    {
        Server.log("LOG", "Match accepted from : " + users[1].getUsername() + " (other: " + users[0].getUsername() + ", match: " + id + ")");
        this.state = MatchState.ACCEPTED;
        users[0].setMatch(this);
        users[1].setMatch(this);
        setUserResponse(users[0], "match_r ok accepted");
    }

    private void setUserResponse(User user, String response)
    {
        user.setResponse(response);
        user.getKey().interestOps(SelectionKey.OP_WRITE);
        Server.selector.wakeup();
    }

    private int getUserIndex(User user)
    {
        int result = (user == users[0]) ? 0 : -1;
        return (user == users[1]) ? 1 : result;
    }

    public boolean setInfoRequested(User user)
    {
        info_requested[getUserIndex(user)] = true;
        Server.log("LOG", "Match info from : " + user.getUsername() + " (match: " + this.id+ ")");
        return info_requested[0] && info_requested[1];
    }

    public String getWord(int index)
    {
        return words[index];
    }

    public User getUser(int index)
    {
        return users[index];
    }

    public String setResultsRequested(User user)
    {
        String response = null;
        results_requested[getUserIndex(user)] = true;
        Server.log("LOG", "Match results from : " + user.getUsername() + " (match: " + this.id + ")");
        if(results_requested[0] && results_requested[1])
        {
            for(int i=0;i<2;i++)
            {
                if(!surrended[i])
                {
                    int bonusPoints = 0;
                    if(scores[i] >= scores[(i+1) % 2] || surrended[(i+1) % 2])
                    {
                        bonusPoints = Server.matchWinnerScore;
                    }
                    synchronized (users[i])
                    {
                        int correctAnswers = this.correctAnswers[i];
                        int incorrectAnswers = positions[i] - this.correctAnswers[i];
                        int untranslatedWords = words.length - positions[i];
                        int score = scores[i];
                        int otherScore = scores[(i+1) % 2];
                        users[i].setResponse("match_results_r ok "
                                + correctAnswers + " "
                                + incorrectAnswers + " "
                                + untranslatedWords + " "
                                + score + " "
                                + otherScore + " "
                                + bonusPoints);
                        users[i].addPoints(score + bonusPoints);
                        users[i].getKey().interestOps(SelectionKey.OP_WRITE);
                        users[i].setState(UserState.LOGGED);
                        users[i].setMatch(null);
                    }
                }
            }
            Server.backupService.notifyChange();
            response = user.getResponse();
            this.state = MatchState.FINISHED;
            Server.matches.remove(id);
        }
        return response;
    }

    private boolean checkTranslation(String translation, int i)
    {
        boolean correct = false;
        if(translations[i] != null)
        {
            for(int j=0;!correct && j<translations[i].length;j++)
            {
                correct = translations[i][j].equalsIgnoreCase(translation);
            }
        }
        return correct;
    }

    public String addUserTranslation(User user, String translatedWord)
    {
        String response = "translation_r ok";
        synchronized (this)
        {
            int userIndex = getUserIndex(user);
            if(positions[userIndex] < words.length && state == MatchState.STARTED)
            {
                boolean correct = checkTranslation(translatedWord, positions[userIndex]);
                scores[userIndex] += correct ? Server.correctWordScore : Server.incorrectScore;
                correctAnswers[userIndex] += correct ? 1 : 0;
                positions[userIndex]++;
            }
            if(positions[0] == words.length && positions[1] == words.length)
            {
                state = MatchState.FINISHED;
            }

            Server.log("LOG", "Translation from : " + user.getUsername() + " (translation: " + translatedWord + ")");
            if(positions[userIndex] < Server.matchWordNumber)
            {
                response += " " + words[positions[userIndex]];
            }
            else synchronized (user)
            {
                user.setState(UserState.MATCH_FINISHED);
            }
        }
        return response;
    }

    public void setSurrended(User user)
    {
        int index = getUserIndex(user);
        surrended[index] = true;
        Server.log("LOG", "Surrend from : " + user.getUsername() + " (match: " + this.id+ ")");
        positions[index] = words.length;
        scores[index] = 0;
        results_requested[index] = true;
        if(results_requested[0] && results_requested[1])
        {
            setResultsRequested(users[(index + 1) % 2]);
        }
        if(surrended[0] && surrended[1])
        {
            Server.matches.remove(this.id);
        }
    }
}
