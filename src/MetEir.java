import processing.core.PApplet;
import processing.data.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MetEir {

    static String lat;
    static String lon;

    public static void main(String[] args) {
        JSONObject json = PApplet.loadJSONObject(new File("src/Vars.json"));
        lat = json.getString("lat");
        lon = json.getString("lon");
        try {
            request();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    static void request() throws IOException, InterruptedException {
        String postEndpoint = "http://metwdb-openaccess.ichec.ie/metno-wdb2ts/locationforecast?"
        + "lat=" + lat + ";"
        + "long=" + lon + ";";
        var request = HttpRequest.newBuilder()
                .uri(URI.create(postEndpoint))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        var client = HttpClient.newHttpClient();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        PrintWriter output = PApplet.createWriter(new File("output/MetEir.xml"));
        System.out.println("Status: " + response.statusCode());
        output.println(response.body());
        output.flush();
        output.close();
    }

}
