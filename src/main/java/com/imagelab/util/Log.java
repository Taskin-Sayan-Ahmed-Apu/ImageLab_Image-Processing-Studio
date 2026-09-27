package com.imagelab.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Multi-sink logger.
 *  - Prints ANSI-colored messages to stdout (for terminals that support it).
 *  - Forwards every message to a UI sink (the in-app ConsolePanel),
 *    so colors appear even inside IntelliJ where ANSI is disabled.
 */
public final class Log {

    public enum Level { INFO, SUCCESS, WARN, ERROR, DEBUG, ITEM, ZOOM, OP }

    @FunctionalInterface
    public interface Sink { void accept(Level level, String msg); }

    private static Sink uiSink;

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String DIM    = "\u001B[2m";
    private static final String GRAY   = "\u001B[90m";
    private static final String RED    = "\u001B[91m";
    private static final String GREEN  = "\u001B[92m";
    private static final String YELLOW = "\u001B[93m";
    private static final String BLUE   = "\u001B[94m";
    private static final String CYAN   = "\u001B[96m";
    private static final String WHITE  = "\u001B[97m";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Log() {}

    /** Register the in-app console so all messages show up there too. */
    public static void setUiSink(Sink sink) { uiSink = sink; }

    // ---------- Public API ----------
    public static void info(String msg)    { emit(Level.INFO, msg); }
    public static void success(String msg) { emit(Level.SUCCESS, msg); }
    public static void warn(String msg)    { emit(Level.WARN, msg); }
    public static void error(String msg)   { emit(Level.ERROR, msg); }
    public static void debug(String msg)   { emit(Level.DEBUG, msg); }
    public static void item(String msg)    { emit(Level.ITEM, msg); }
    public static void zoom(double pct)    { emit(Level.ZOOM, String.format("Zoom → %.0f%%", pct)); }
    public static void opStart(String op)  { emit(Level.OP, op + "  processing..."); }
    public static void opDone(String op, long ms) {
        emit(Level.SUCCESS, op + "  (" + ms + " ms)");
    }

    /** Terminal-only banner (ANSI art) — kept for IDE / shell runs. */
    public static void banner(String appName, String version) {
        String[] art = {
                "  ██╗███╗   ███╗ █████╗  ██████╗ ███████╗██╗      █████╗ ██████╗ ",
                "  ██║████╗ ████║██╔══██╗██╔════╝ ██╔════╝██║     ██╔══██╗██╔══██╗",
                "  ██║██╔████╔██║███████║██║  ███╗█████╗  ██║     ███████║██████╔╝",
                "  ██║██║╚██╔╝██║██╔══██║██║   ██║██╔══╝  ██║     ██╔══██║██╔══██╗",
                "  ██║██║ ╚═╝ ██║██║  ██║╚██████╔╝███████╗███████╗██║  ██║██║  ██║",
                "  ╚═╝╚═╝     ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚══════╝╚══════╝╚═╝  ╚═╝╚═╝  ╚═╝"
        };
        System.out.println();
        for (String line : art) System.out.println(CYAN + line + RESET);
        System.out.println("        " + BOLD + WHITE + appName
                + " — Image Processing Studio   " + GRAY + "v" + version + RESET);
        System.out.println();
    }

    public static void section(String title) {
        String bar = "─".repeat(Math.max(1, 50 - title.length()));
        System.out.println();
        System.out.println(BOLD + BLUE + "┌─ " + title + " " + bar + RESET);
    }

    public static void divider() {
        System.out.println("  " + DIM + GRAY
                + "────────────────────────────────────────────────────" + RESET);
    }

    public static void kv(String key, String value) {
        System.out.println("   " + GRAY + "│" + RESET + " "
                + BOLD + String.format("%-22s", key) + RESET
                + WHITE + value + RESET);
    }

    // ---------- Internals ----------
    private static void emit(Level level, String msg) {
        printAnsi(level, msg);
        Sink s = uiSink;
        
        if (s != null) s.accept(level, msg);
    }

    private static void printAnsi(Level level, String msg) {
        String icon, color;
        switch (level) {
            case INFO    -> { icon = "ℹ"; color = CYAN; }
            case SUCCESS -> { icon = "✔"; color = GREEN; }
            case WARN    -> { icon = "⚠"; color = YELLOW; }
            case ERROR   -> { icon = "✖"; color = RED; }
            case DEBUG   -> { icon = "·"; color = GRAY; }
            case ITEM    -> { icon = "▸"; color = CYAN; }
            case ZOOM    -> { icon = "🔍"; color = CYAN; }
            case OP      -> { icon = "⟳"; color = YELLOW; }
            default      -> { icon = "·"; color = WHITE; }
        }
        System.out.println(DIM + "[" + LocalTime.now().format(FMT) + "]" + RESET
                + " " + color + BOLD + icon + " " + String.format("%-4s", level) + RESET
                + "  " + msg);
    }
}
