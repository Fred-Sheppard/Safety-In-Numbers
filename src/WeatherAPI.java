import processing.core.PApplet;
import processing.data.JSONArray;
import processing.data.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static processing.core.PApplet.loadJSONObject;

public class WeatherAPI {

    static float lat, lon;
    static String accuKey;

    enum ApiType {
        GET,
        POST,
        RAPIDAPI
    }

    public static JSONObject configs;

    public static void main(String[] args) {
        configs = loadJSONObject(new File("data/Vars.json"));
        Model aeris = new Model("Visual.json", ApiType.GET);
        aeris.saveData();
//        requestAll();
    }

    public static void requestAll() {
        JSONObject json = loadJSONObject(new File("data/Vars.json"));
        lat = json.getFloat("lat");
        lon = json.getFloat("lon");
        accuKey = json.getString("accuKey");

        Model metEir = new Model("MetEir.xml", ApiType.GET);
        Model weatherBit = new Model("WeatherBit.json", ApiType.GET);
        Model accu = new Model("Accu.json", ApiType.GET);
        Model openWeather = new Model("OpenWeather.json", ApiType.GET);
        Model aeris = new Model("Aeris.json", ApiType.RAPIDAPI);
        Model climacell = new Model("ClimaCell.json", ApiType.RAPIDAPI);
        Model visual = new Model("Visual.json", ApiType.RAPIDAPI);

        metEir.setUrl(String.format("http://metwdb-openaccess.ichec.ie/metno-wdb2ts/locationforecast?lat=%f&long=%f",
                lat, lon));
        weatherBit.setUrl(String.format("https://api.weatherbit.io/v2.0/forecast/daily?&lat=%f&lon=%f" +
                "&key=WEATHERBIT_KEY", lat, lon));
        accu.setUrl(String.format(
                "http://dataservice.accuweather.com/forecasts/v1/hourly/12hour/%s?apikey=ACCUWEATHER_KEY&details=true&metric=true", accuKey));
        openWeather.setUrl(String.format("https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f" +
                "&units=metric&appid=OPEN_WEATHER_KEY", lat, lon));
        aeris.setUrl(String.format("https://aerisweather1.p.rapidapi.com/forecasts/%f,%%20%f?" +
                "from=2022-10-16&filter=4hr&to=2022-10-17", lat, lon));
        climacell.setUrl(String.format("https://climacell-microweather-v1.p.rapidapi.com/weather/forecast/hourly?lat=%f&lon=%f" +
                "&fields=windSpeed%%2CwindDirection&unit_system=si", lat, lon));
        visual.setUrl(String.format("https://visual-crossing-weather.p.rapidapi.com/forecast?aggregateHours=24" +
                "&location=%f%%2C%f&contentType=json&unitGroup=metric&shortColumnNames=true", lat, lon));

//        Model[] models = {metEir, weatherBit, accu, openWeather, aeris,  climacell, visual};
        Model[] models = {climacell};
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
        Model m = new Model("temp.json", ApiType.GET);
        m.setUrl(url);
        try {
            m.request();
        } catch (Exception e) {
            System.out.println(e);
        }
        File temp = new File("output/GET/temp.json");
        JSONObject json = loadJSONObject(temp);
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
        JSONObject config;

        Model(String filename, ApiType type) {
            this.filename = filename;
            this.type = type;
            String name = filename.split("\\.")[0];
            config = (JSONObject) JSONPath.jsonPathToObject(configs,
                    "Models/" + name);
        }

        public void saveData() {
            JSONArray output = new JSONArray();
            // Source of data
            JSONObject jsonSource = loadJSONObject(new File("output/GET/" + filename));
            // Array of timeFrames
            // new JSONArray(Aeris.json/response/periods)
            JSONArray times = (JSONArray) JSONPath.jsonPathToObject(jsonSource,
                    config.getString("Root"));
            // List of keys you want to include
            JSONObject myKeys = config.getJSONObject("Keys");
            for (int i = 0; i < times.size(); i++) {
                JSONObject thisTime = times.getJSONObject(i);
                JSONObject timeOuput = new JSONObject();
                // Find their key's name, get value at this key
                for (Object o : myKeys.keys()) {
                    String myMetric = (String) o; // windSpeed
                    String theirMetric = myKeys.getString(myMetric); //windSpeedKPH
                    Object value = thisTime.get(theirMetric); // 22.6
                    // Maybe a typo in Vars.json
                    if (value == null) System.out.printf("""
                            %s
                            Could not find value at "%s"%n""", filename, theirMetric);
                    timeOuput.put(myMetric, value);
                }
                // Add timeFrame to list of timeFrames
                output.append(timeOuput);
            }
            output.save(new File("output/Refactor/" + filename), null);
        }


        public void setUrl(String s) {
            url = s;
        }

        public void setData(String s) {
            data = s;
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

            PrintWriter output = PApplet.createWriter(new File("output/GET/" + filename));
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
