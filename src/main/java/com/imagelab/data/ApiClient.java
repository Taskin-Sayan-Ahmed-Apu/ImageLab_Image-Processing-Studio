package com.imagelab.data;

import com.google.gson.Gson;

import java.net.URI;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class ApiClient {

    private static final Gson GSON = new Gson();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(
                    ConfigLoader.get().getApi().getTimeoutSeconds()))
            .build();

    private ApiClient() {}

    public static DogApiResponse fetchRandomDog() throws Exception {
        String endpoint = ConfigLoader.get().getApi().getRandomImageEndpoint();
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new  RuntimeException("API  returned HTTP " + resp.statusCode());
        DogApiResponse parsed = GSON.fromJson(resp.body(), DogApiResponse.class);
        if (parsed == null || !parsed.isSuccess() || parsed.getMessage() == null)
            throw new RuntimeException("API response was not successful");
        return parsed;
    }

    public static byte[] downloadImage(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200)
            throw new RuntimeException("Image download failed: HTTP " + resp.statusCode());
        return resp.body();
    }
}
