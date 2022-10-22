import org.xml.sax.SAXException;
import processing.core.PApplet;
import processing.data.JSONArray;
import processing.data.JSONObject;
import processing.data.XML;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static processing.core.PApplet.loadJSONArray;
import static processing.core.PApplet.loadJSONObject;

public class WeatherAPI {

    static float lat, lon;
    static String accuKey;
    public static JSONObject configs;

    enum ApiType {
        GET,
        POST,
        RAPIDAPI
    }

    public static void main(String[] args) {
        // TODO Convert all units to same type i.e. kph, mps, etc.
        configs = loadJSONObject(new File("data/config.json"));
        lat = configs.getFloat("lat");
        lon = configs.getFloat("lon");

//        Model accu = new Model("Accu.json", ApiType.GET);
//        accu.setUrl();
//        accu.saveData();
        Model metEir = new Model("MetEir.xml", ApiType.GET);
        metEir.saveXML();
    }

    public static void requestAll() {
        JSONObject json = loadJSONObject(new File("data/config.json"));
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
        accu.setUrl(String.format(
                "http://dataservice.accuweather.com/forecasts/v1/hourly/12hour/%s?apikey=ACCUWEATHER_KEY&details=true&metric=true", accuKey));
        climacell.setUrl(String.format("https://climacell-microweather-v1.p.rapidapi.com/weather/forecast/hourly?lat=%f&lon=%f" +
                "&fields=windSpeed%%2CwindDirection&unit_system=si", lat, lon));

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

    public static Object loadJSON(String filename) {
        File file = new File(filename);
        try {
            return loadJSONObject(file);
        } catch (RuntimeException e) {
            return loadJSONArray(file);
        }
    }

    static class Model {

        private String url;
        private String data;
        public String filename;
        private final ApiType type;
        private final JSONObject config;

        Model(String filename, ApiType type) {
            this.filename = filename;
            this.type = type;
            String name = filename.split("\\.")[0];
            config = (JSONObject) JSONPath.jsonPathToObject(configs,
                    "Models/" + name);
        }

        public void saveData() {
            String root = config.getString("Root");
            if (root.equals("XML")) {
                saveXML();
                return;
            }
            JSONArray output = new JSONArray();
            // Source of data
            Object jsonSource = loadJSON("output/GET/" + filename);
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
                    // myMetric is the standardised name for outputting
                    // theirMetric is what they call the metric, and may be path through multiple json Objects
                    // e.g. Accu.json windSpeed: Wind/Speed/Value
                    String myMetric = (String) o; // windSpeed
                    String theirMetric = myKeys.getString(myMetric); //windSpeedKPH
                    Object value = JSONPath.jsonPathToObject(thisTime, theirMetric); // 22.6
                    // Might be caused by typo in config.json if this occurs
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

        public void saveXML() {
            XML xml = loadXML("output/GET/MetEir.xml", null);
            JSONArray output = new JSONArray();
            // List of metrics to request
            JSONObject myKeys = config.getJSONObject("Keys");
            Time:
            for (XML time : xml.getChild("product").getChildren("time")) {
                JSONObject timeOutput = new JSONObject();
                for (Object o : myKeys.keys()) {
                    // myMetric is the standardised name for outputting
                    // theirMetric is the path to the value
                    String myMetric = (String) o;
                    String theirMetric = myKeys.getString(myMetric);
                    // The final part of a path is an attribute and not a child
                    // It must be split and treated differently
                    String[] path = theirMetric.split("/");
                    String attribute = path[path.length-1];
                    // Get child from path minus attribute
                    // e.g. location/windSpeed/mps -> location/windSpeed/
                    XML key = time.getChild(theirMetric.replace("/" + attribute, ""));
                    // If one key in this timeframe is null, they all will be
                    // Just skip to the next timeframe
                    if (key == null) {
                        continue Time;
                    }
                    // TODO This returns a String, parse to float, int etc
                    timeOutput.put(myMetric, key.getString(attribute));
                }
                output.append(timeOutput);
            }
            String f = filename.replace("xml", "json");
            output.save(new File("output/Refactor/" + f), null);
        }

        public XML loadXML(String filename, String options) {
            try {
                BufferedReader reader = PApplet.createReader(new File(filename));
                return new XML(reader, options);
            } catch (ParserConfigurationException | SAXException | IOException var4) {
                throw new RuntimeException(var4);
            }
        }


        public void setUrl(String s) {
            url = s;
        }

        public void setUrl(float lat, float lon) {
            setUrl(String.format(config.getString("url"), lat, lon));
        }

        public void setUrl() {
            setUrl((config.getString("url")));
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
