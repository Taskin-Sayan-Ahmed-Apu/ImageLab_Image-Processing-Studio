package com.imagelab.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class Log {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private Log() {}

    public static void info(String msg)  { print("INFO ", msg); }
    public static void warn(String msg)  { print("WARN ", msg); }
    public static void error(String msg) { print("ERROR", msg); }

    private static void print(String level, String msg) {
        System.out.println("[" + LocalTime.now().format(FMT) + "] [" + level + "] " + msg);
    }
}