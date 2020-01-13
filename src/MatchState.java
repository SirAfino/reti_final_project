/* Author : Serafino Gabriele 564411
 * Brief : Represents the possible states of a match
 */

public enum MatchState
{
    PENDING, //The user has sent a request to his friend and his waiting for a response
    ACCEPTED, //The friend has accepted the match request
    STARTED, //Both users sent the match_info command
    FINISHED //Timeout occurred or both users sent all translations
}
