package com.imagelab.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class JsonHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private JsonHandler() {}

    public static void exportHistory(List<HistoryEntry> history, File file) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(history, w);
        }
    }

    public static List<HistoryEntry> importHistory(File file) throws IOException {
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<HistoryEntry>>() {}.getType();
            
            return GSON.fromJson(r, type);
        }
    }
}
