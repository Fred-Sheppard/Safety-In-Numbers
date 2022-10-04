//No longer needed, API is too expensive

import processing.core.PApplet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Windy {

    public static void main(String[] args) {
        try {
            request();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    static void request() throws IOException, InterruptedException {
        String postEndpoint = "https://api.windy.com/api/point-forecast/v2";

        String inputJson = """
                {
                    "lat": 52.930166,
                    "lon": -8.282975,
                    "model": "gfs",
                    "parameters": ["wind", "pressure"],
                    "key": "jTHRz0GvPkGUmKtvTkvFyInRQIakPawV"
                }""";
        var request = HttpRequest.newBuilder()
                .uri(URI.create(postEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(inputJson))
                .build();

        var client = HttpClient.newHttpClient();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        PrintWriter output = PApplet.createWriter(new File("output/Windy.json"));
        System.out.println("Status: " + response.statusCode());
        output.println(response.body());
        output.flush();
        output.close();
    }

}
