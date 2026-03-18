package com.obsidian.core.flash;

/**
 * Configuration for flash notification system.
 * Allows customization of appearance, duration and position.
 */
public class FlashConfig
{
    /** Custom CSS to inject */
    private static String customCSS = "";

    /** Duration in milliseconds before auto-dismiss */
    private static int duration = 3000;

    /** Position on screen */
    private static String position = "bottom-right";

    /**
     * Sets custom CSS for flash notifications.
     *
     * @param css Custom CSS string
     */
    public static void setCustomCSS(String css) {
        customCSS = css;
    }

    /**
     * Gets custom CSS.
     *
     * @return Custom CSS string
     */
    public static String getCustomCSS() {
        return customCSS;
    }

    /**
     * Sets notification duration.
     *
     * @param ms Duration in milliseconds
     */
    public static void setDuration(int ms) {
        duration = ms;
    }

    /**
     * Gets notification duration.
     *
     * @return Duration in milliseconds
     */
    public static int getDuration() {
        return duration;
    }

    /**
     * Sets notification position.
     * Valid values: top-right, top-left, bottom-left, bottom-right, top-center, bottom-center
     *
     * @param pos Position identifier
     */
    public static void setPosition(String pos) {
        position = pos;
    }

    /**
     * Gets notification position.
     *
     * @return Position identifier
     */
    public static String getPosition() {
        return position;
    }

    /**
     * Converts position identifier to CSS positioning rules.
     *
     * @return CSS positioning string
     */
    public static String getPositionCSS()
    {
        return switch (position) {
            case "top-right" -> "top: 2rem; right: 2rem;";
            case "top-left" -> "top: 2rem; left: 2rem;";
            case "bottom-left" -> "bottom: 2rem; left: 2rem;";
            case "top-center" -> "top: 2rem; left: 50%; transform: translateX(-50%);";
            case "bottom-center" -> "bottom: 2rem; left: 50%; transform: translateX(-50%);";
            default -> "bottom: 2rem; right: 2rem;";
        };
    }
}