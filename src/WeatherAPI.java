import processing.core.PApplet;
import processing.data.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WeatherAPI {


    public static void main(String[] args) {
        JSONObject json = PApplet.loadJSONObject(new File("data/Vars.json"));
        float lat = json.getFloat("lat");
        float lon = json.getFloat("lon");

        String url1 = String.format("http://metwdb-openaccess.ichec.ie/metno-wdb2ts/locationforecast?lat=%f&long=%f", lat, lon);
        String url2 = String.format("https://api.weatherbit.io/v2.0/forecast/daily?&lat=%f&lon=%f&key=WEATHERBIT_KEY", lat, lon);
        String url3 = "https://api.windy.com/api/point-forecast/v2";
        String inputJson3 = String.format("""
                {
                    "lat": %f,
                    "lon": %f,
                    "model": "gfs",
                    "parameters": ["wind", "pressure"],
                    "key": "jTHRz0GvPkGUmKtvTkvFyInRQIakPawV"
                }""", lat, lon);

        Model metEir = new Model("MetEir.xml", url1);
        Model weatherBit = new Model("WeatherBit.json", url2);
        Model windy = new Model("Windy.json", url3, inputJson3);

        try {
            metEir.request();
            weatherBit.request();
            windy.request();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    static class Model {

        String url;
        String data;
        String filename;
        boolean isPOST;

        Model(String filename, String url) {
            this.filename = filename;
            this.url = url;
            isPOST = false;
        }

        Model(String filename, String url, String data) {
            this.filename = filename;
            this.url = url;
            this.data = data;
            isPOST = true;
        }

        void request() throws IOException, InterruptedException {
            HttpRequest request;
            if (isPOST) {
                request = post(url, data);
            } else {
                request = get(url);
            }
            var client = HttpClient.newHttpClient();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            PrintWriter output = PApplet.createWriter(new File("output/" + filename));
            System.out.println(filename + ": " + response.statusCode());
            output.println(response.body());
            output.flush();
            output.close();
        }

        HttpRequest get(String postEndpoint) {
            return HttpRequest.newBuilder()
                    .uri(URI.create(postEndpoint))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
        }

        HttpRequest post(String postEndpoint, String data) {
            return HttpRequest.newBuilder()
                    .uri(URI.create(postEndpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(data))
                    .build();
        }
    }
}
