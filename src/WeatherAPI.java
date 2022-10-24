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

    enum REQUESTTYPE {
        GET,
        POST,
        RAPIDAPI
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        /*
         TODO Need separate configs for hourly, daily. Find some way to do this
         TODO Maybe put "1h" "1d" inside "Accu", maybe just copy and make separate
         */
        configs = loadJSONObject(new File("data/config.json"));
        lat = configs.getFloat("lat");
        lon = configs.getFloat("lon");

        JSONObject queries = configs.getJSONObject("Queries");
        for (Object o : queries.keys()) {
            String modelName = (String) o;
            System.out.println(modelName);
            String url = queries.getString(modelName);
            if (modelName.equals("OpenWeather")) {
                Model m1 = new Model("OpenWeather_1h.json");
                Model m2 = new Model("OpenWeather_1d.json");
                m1.setRoot("hourly");
                m2.setRoot("daily");
                m1.setUrl(url);
                m2.setUrl(url);
                m1.request();
                m2.request();
            } else {
                Model m = new Model(modelName + ".json");
                m.setUrl(url);
                m.request();
            }
            break;
        }
    }

    public static void requestAll() {
        JSONObject json = loadJSONObject(new File("data/config.json"));
        lat = json.getFloat("lat");
        lon = json.getFloat("lon");
        accuKey = json.getString("accuKey");

        Model metEir = new Model("MetEir.xml");
        Model weatherBit = new Model("WeatherBit.json");
        Model accu = new Model("Accu.json");
        Model openWeather = new Model("OpenWeather.json");
        Model aeris = new Model("Aeris.json");
        Model visual = new Model("Visual.json");

//        Model[] models = {metEir, weatherBit, accu, openWeather, aeris,  climacell, visual};
        Model[] models = {visual};
        try {
            for (Model m : models) {
                m.setUrlFromConfig();
                m.request();
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    // Loads JSON file, regardless if it's an Object or Array
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
        private final REQUESTTYPE requestType;
        private final JSONObject config;
        private String root;

        Model(String filename) {
            this.filename = filename; // Aeris_1h.json
            String modelName = filename.split("\\.")[0]; // Aeris_1h
            String configName = modelName.split("_")[0]; // Aeris
            config = (JSONObject) JSONPath.getValue(configs,
                    "Models/" + configName);
            int typeIndex = config.getInt("RequestType");
            requestType = REQUESTTYPE.values()[typeIndex];
        }

        Model(String filename, boolean testing) {
            this.filename = filename;
            config = new JSONObject();
            requestType = REQUESTTYPE.GET;
        }

        public void saveData() {
            if (root == null) {
                root = config.getString("Root");
            }
            if (root.equals("XML")) {
                saveXML();
                return;
            }
            JSONArray output = new JSONArray();
            // Source of data
            Object jsonSource = loadJSON("output/GET/" + filename);
            // Array of timeFrames
            // new JSONArray(Aeris.json/response/periods)
            JSONArray times = (JSONArray) JSONPath.getValue(jsonSource,
                    config.getString("Root"));
            // List of keys you want to include
            JSONObject myKeys = config.getJSONObject("Keys");
            JSONObject myUnits = config.getJSONObject("Units");
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
                    Object value = JSONPath.getValue(thisTime, theirMetric); // 22.6
                    // Might be caused by typo in config.json if this occurs
                    if (value == null) {
                        System.out.printf("""
                                %s
                                Could not find value at "%s"%n""", filename, theirMetric);
                        continue;
                    }
                    // Conversion factor between kts, kph etc.
                    Object cFactor = myUnits.get(myMetric);
                    // If there is a calculation to be done
                    if (cFactor != null) {
                        // Basically, multiply value by conversion factor
                        value = switch (value) {
                            case Integer v -> v.doubleValue() * (double) cFactor;
                            case Double v -> v * (double) cFactor;
                            default -> value;
                        };
                    }
                    // windSpeed: 12.5
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
            // If more xml models are added, this will have to be streamlined
            // For now, the path can be hardcoded
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
                    String attribute = path[path.length - 1];
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

        // Taken from processing.data.XML
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

        public void setUrlFromConfig() {
            setUrl((config.getString("url")));
        }

        public void setData(String s) {
            data = s;
        }

        public void setRoot(String s) {
            root = s;
        }

        public String toString() {
            return filename + " " + url;
        }

        void request() throws IOException, InterruptedException {
            HttpRequest request = switch (requestType) {
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
    }
}
