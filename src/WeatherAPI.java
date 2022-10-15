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

    enum ApiType {
        GET,
        POST,
        RAPIDAPI
    }

    ;

    public static void main(String[] args) {
        JSONObject json = PApplet.loadJSONObject(new File("data/Vars.json"));
        lat = json.getFloat("lat");
        lon = json.getFloat("lon");
        accuKey = json.getString("accuKey");

        String url1 = String.format("http://metwdb-openaccess.ichec.ie/metno-wdb2ts/locationforecast?lat=%f&long=%f",
                lat, lon);
        String url2 = String.format("https://api.weatherbit.io/v2.0/forecast/daily?&lat=%f&lon=%f" +
                "&key=WEATHERBIT_KEY", lat, lon);
        String url4 = "https://nomads.ncep.noaa.gov/pub/data/nccf/com/rap/prod/";
        String url5 = String.format(
                "http://dataservice.accuweather.com/forecasts/v1/hourly/12hour/%s?apikey=ACCUWEATHER_KEY&details=true&metric=true", accuKey);
        String url6 = String.format("https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f" +
                "&units=metric&appid=OPEN_WEATHER_KEY", lat, lon);
        String url7 = String.format("https://aerisweather1.p.rapidapi.com/forecasts/%f,%%20%f?" +
                "from=2022-10-16&filter=4hr&to=2022-10-17", lat, lon);
        String url8 = String.format("https://climacell-microweather-v1.p.rapidapi.com/weather/forecast/hourly?lat=%f&lon=%f" +
                "&fields=windSpeed%%2CwindDirection&unit_system=si", lat, lon);
        String url9 = String.format("https://visual-crossing-weather.p.rapidapi.com/forecast?aggregateHours=24" +
                "&location=%f%%2C%f&contentType=json&unitGroup=metric&shortColumnNames=true", lat, lon);

        String data1 = "aerisweather1.p.rapidapi.com";
        String data2 = "climacell-microweather-v1.p.rapidapi.com";
        String data3 = "visual-crossing-weather.p.rapidapi.com";

        Model metEir = new Model("MetEir.xml", url1);
        Model weatherBit = new Model("WeatherBit.json", url2);
        Model noaa = new Model("NOAA.txt", url4);
        Model accu = new Model("Accu.json", url5);
        Model openWeather = new Model("OpenWeather.json", url6);
        Model aeris = new Model("Aeris.json", url7, url7.split("/")[2], ApiType.RAPIDAPI);
        Model climacell = new Model("ClimaCell.json", url8, url8.split("/")[2], ApiType.RAPIDAPI);
        Model visual = new Model("Visual.json", url9, url9.split("/")[2], ApiType.RAPIDAPI);

//        Model[] models = {metEir, weatherBit, accu, openWeather, aeris,  climacell, visual};
        Model[] models = {visual};
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
        ApiType type;

        Model(String filename, String url) {
            this.filename = filename;
            this.url = url;
            this.type = ApiType.GET;
        }

        Model(String filename, String url, String data, ApiType type) {
            this.filename = filename;
            this.url = url;
            this.data = data;
            this.type = type;
        }

        public String toString() {
            return filename + " " + url;
        }

        void request() throws IOException, InterruptedException {
            HttpRequest request = switch (type) {
                case GET -> get(url);
                case POST -> post(url, data);
                case RAPIDAPI -> getRapid(url, data);
            };
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

        HttpRequest getRapid(String postEndpoint, String data) {
            return HttpRequest.newBuilder()
                    .uri(URI.create(postEndpoint))
                    .header("X-RapidAPI-Key", "RAPID_API_KEY")
                    .header("X-RapidAPI-Host", data)
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();
        }
    }
}
