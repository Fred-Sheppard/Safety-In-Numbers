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

    static float lat, lon;
    static String accuKey;

    public static void main(String[] args) {
        JSONObject json = PApplet.loadJSONObject(new File("data/Vars.json"));
        lat = json.getFloat("lat");
        lon = json.getFloat("lon");
        accuKey = json.getString("accuKey");

        String url1 = String.format("http://metwdb-openaccess.ichec.ie/metno-wdb2ts/locationforecast?lat=%f&long=%f", lat, lon);
        String url2 = String.format("https://api.weatherbit.io/v2.0/forecast/daily?&lat=%f&lon=%f&key=WEATHERBIT_KEY", lat, lon);
        String url3 = "https://api.windy.com/api/point-forecast/v2";
        String url4 = "https://nomads.ncep.noaa.gov/pub/data/nccf/com/rap/prod/";
        String url5 = String.format(
                "http://dataservice.accuweather.com/forecasts/v1/hourly/12hour/%s?apikey=ACCUWEATHER_KEY&details=true&metric=true", accuKey);
        String url6 = String.format("https://pro.openweathermap.org/data/2.5/forecast/hourly?lat=%f&lon=%f&appid=OPEN_WEATHER_KEY2", lat, lon);
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
        Model noaa = new Model("NOAA.txt", url4);
        Model accu = new Model("Accu.json", url5);
        Model openWeather = new Model("openWeather.json", url6);

        Model[] models = {openWeather};
        try {
            for (Model m : models) {
                m.request();
            }
        } catch (Exception e) {
            System.out.println(e);
        }

    }

    static void checkAccuKey() {
        String url = String.format(
                "http://dataservice.accuweather.com/locations/v1/cities/geoposition/search?apikey=ACCUWEATHER_KEY&q=%f%%2C%f",
                lat, lon);
        Model m = new Model("temp.json", url);
        try {
            m.request();
        } catch (Exception e) {
            System.out.println(e);
        }
        File temp = new File("output/temp.json");
        JSONObject json = PApplet.loadJSONObject(temp);
        String key = json.getString("Key");
        if (!key.equals(accuKey)) {
            System.out.println("Accuweather key changed. New Location key is: " + key);
        } else {
            System.out.println("Accuweather: Key is good");
        }
        temp.delete();
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
