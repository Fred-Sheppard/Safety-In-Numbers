import processing.core.PApplet;
import processing.data.JSONArray;
import processing.data.JSONObject;
import processing.data.StringDict;

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

    public static void main(String[] args) {
        // TODO Move to own function - model.getTimes()
        JSONObject models = PApplet.loadJSONObject(new File("data/Vars.json")).getJSONObject("Models");
        for (Object o : models.keys()) {
            String key = (String) o;
            JSONObject model = models.getJSONObject(key);
            String path = model.getString("Root");
            String filename = key + ".json";
            Object json = PApplet.loadJSONObject(new File("output/GET/" + filename));
            JSONArray times = (JSONArray) JSONPath.jsonPathToObject(json, path);
            System.out.println(key + times.size());
        }
    }
    public static void main1(String[] args) {
        JSONObject json = PApplet.loadJSONObject(new File("data/Vars.json"));
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
                "&key=a5db704eb5ec4fb9b98149eb8811f575", lat, lon));
        accu.setUrl(String.format(
                "http://dataservice.accuweather.com/forecasts/v1/hourly/12hour/%s?apikey=1cfVw9AtSxOswPYM5eTwhfSqqlLKSeWt&details=true&metric=true", accuKey));
        openWeather.setUrl(String.format("https://api.openweathermap.org/data/2.5/forecast?lat=%f&lon=%f" +
                "&units=metric&appid=35ce7696a59f9ed02b794ab6d155e6c1", lat, lon));
        aeris.setUrl(String.format("https://aerisweather1.p.rapidapi.com/forecasts/%f,%%20%f?" +
                "from=2022-10-16&filter=4hr&to=2022-10-17", lat, lon));
        climacell.setUrl(String.format("https://climacell-microweather-v1.p.rapidapi.com/weather/forecast/hourly?lat=%f&lon=%f" +
                "&fields=windSpeed%%2CwindDirection&unit_system=si", lat, lon));
        visual.setUrl(String.format("https://visual-crossing-weather.p.rapidapi.com/forecast?aggregateHours=24" +
                "&location=%f%%2C%f&contentType=json&unitGroup=metric&shortColumnNames=true", lat, lon));

        JSONArray arr = PApplet.loadJSONObject(new File("output/GET/Aeris.json"))
                .getJSONArray("response")
                .getJSONObject(0)
                .getJSONArray("periods");
        aeris.setKey("windSpeed", "windSpeedKPH");
        aeris.setKey("windDir", "windDirDEG");
        System.out.println(aeris.refactor(arr));
        System.exit(0);

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
                "http://dataservice.accuweather.com/locations/v1/cities/geoposition/search?apikey=1cfVw9AtSxOswPYM5eTwhfSqqlLKSeWt&q=%f%%2C%f",
                lat, lon);
        Model m = new Model("temp.json", ApiType.GET);
        try {
            m.request();
        } catch (Exception e) {
            System.out.println(e);
        }
        File temp = new File("output/GET/temp.json");
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
        StringDict keys;

        Model(String filename, ApiType type) {
            this.filename = filename;
            this.type = type;
            keys = new StringDict();
        }

//        Model(String filename, String url, String data, ApiType type) {
//            this.filename = filename;
//            this.url = url;
//            this.data = data;
//            this.type = type;
//        }

        public void setUrl(String s) {
            url = s;
        }

        public void setData(String s) {
            data = s;
        }

        public void setKey(String key, String value) {
            keys.set(key, value);
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
                    .header("X-RapidAPI-Key", "c4bcddcd70msh61696ab501cad75p134af7jsndf9884823caf")
                    .header("X-RapidAPI-Host", data)
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();
        }

        public JSONArray refactor(JSONArray times) {
            //Empty array
            JSONArray out = new JSONArray();
            //Iterate through timeframes
            for (int i = 0; i < times.size(); i++) {
                //This timeframe
                JSONObject thisJSON = times.getJSONObject(i);
                //Object to be appended to array
                JSONObject time = new JSONObject();
                //myKey: windSpeed
                //theirKey: wspKPH
                for (String myKey : keys.keys()) {
                    String theirKey = keys.get(myKey);
                    //Put my key, their value
                    time.put(myKey, thisJSON.get(theirKey));
                }
                out.append(time);
            }
            return out;
        }
    }
}
