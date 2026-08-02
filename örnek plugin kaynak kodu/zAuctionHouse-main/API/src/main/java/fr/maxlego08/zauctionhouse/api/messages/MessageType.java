package fr.maxlego08.zauctionhouse.api.messages;

/**
 * Defines the display format for messages.
 */
public enum MessageType {

    /**
     * Display message in the action bar above the hotbar.
     */
    ACTION,

    /**
     * Display message in the chat window.
     */
    TCHAT,

    /**
     * Display message as a title on screen.
     */
    TITLE,

    /**
     * Display message centered in chat.
     */
    CENTER,

    /**
     * Do not display the message.
     */
    NONE,

    /**
     * Display message in chat without the plugin prefix.
     */
    WITHOUT_PREFIX,

    /**
     * Display message as a boss bar at the top of the screen.
     */
    BOSSBAR,
    ;

    /**
     * Parses a message type from a string.
     *
     * @param string the string to parse
     * @return the message type, or {@code null} if invalid
     */
    public static MessageType fromString(String string) {
        try {
            return MessageType.valueOf(string.toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }

}