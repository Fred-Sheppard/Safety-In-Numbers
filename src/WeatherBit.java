import processing.core.PApplet;
import processing.data.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WeatherBit {

    static String lat;
    static String lon;

    public static void main(String[] args) {
        JSONObject json = PApplet.loadJSONObject(new File("data/Vars.json"));
        lat = json.getString("lat");
        lon = json.getString("lon");
        try {
            request();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    static void request() throws IOException, InterruptedException {
        String postEndpoint = "https://api.weatherbit.io/v2.0/forecast/daily?"
        + "lat=" + lat
        + "&lon=" + lon
        + "&key=a5db704eb5ec4fb9b98149eb8811f575";
//        System.out.println(postEndpoint);
//        System.exit(0);
//        String postEndpoint = "https://api.weatherbit.io/v2.0/forecast/daily?&lat=52.930166&lon=-8.282975&key=a5db704eb5ec4fb9b98149eb8811f575";
        var request = HttpRequest.newBuilder()
                .uri(URI.create(postEndpoint))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        var client = HttpClient.newHttpClient();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        PrintWriter output = PApplet.createWriter(new File("output/WeatherBit.json"));
        System.out.println("Status: " + response.statusCode());
        System.out.println(response.body());
        output.println(response.body());
        output.flush();
        output.close();
    }

}
