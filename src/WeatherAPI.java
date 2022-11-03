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
import java.time.ZonedDateTime;

public class WeatherAPI {

    static float lat, lon;
    public static JSONObject configs;

    enum RequestType {
        GET,
        POST,
        RAPIDAPI
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("WeatherAPI.java");
        configs = PApplet.loadJSONObject(new File("data/config.json"));
        lat = configs.getFloat("lat");
        lon = configs.getFloat("lon");

        Model metEir = new Model("MetEir.xml");
        metEir.request();
        metEir.saveData();

//        JSONObject models = configs.getJSONObject("Models");
//        for (Object o : models.keys()) {
//            String modelName = (String) o;
//            String fileType = ".json";
//            if (modelName.equals("MetEir")) {
//                fileType = ".xml";
//            }
//            Model m = new Model(modelName + fileType);
//            m.request();
//            m.saveData();
//        }
    }

    // Loads JSON file, regardless if it's an Object or Array
    public static Object loadJSON(String filename) {
        File file = new File(filename);
        try {
            return PApplet.loadJSONObject(file);
        } catch (RuntimeException e) {
            return PApplet.loadJSONArray(file);
        }
    }

    static class Model {

        // Contains data for the model found in config.json, under the model name
        private final JSONObject config;
        // URL to be used for HTTP requests
        private String url; // "https://aerisweather1.p.rapidapi.com/forecasts/%f,%f?plimit=72&filter=1hr"
        // Header data to be used with RapidAPI requests
        private String header; // "aerisweather1.p.rapidapi.com"
        // Name of input and output file
        public String filename; // Aeris_1h.json
        // Type of HTTP request that will be called. GET, POST or RAPIDAPI
        private final RequestType requestType; // GET
        // JSON path to the array of time periods in the http request response.
        private String root; // "response/0/periods"

        Model(String filename) {
            this.filename = filename; // Aeris_1h.json
            String modelName = filename.split("\\.")[0]; // Aeris_1h
            // Entry in config.json that contains values for url, root, keys etc
            config = (JSONObject) JSONPath.getValue(configs, "Models/" + modelName);
            // Url to be used for HTTP requests
            url = String.format(config.getString("URL"), lat, lon);
            // Value to be passed as a header in RapidAPI requests
            header = config.getString("Header");
            // JSON path to the array of time periods in the http request response.
            root = config.getString("Root");
            // Integer value of RequestType. Can be between 0 and the enum values' length -1
            int typeIndex = config.getInt("RequestType");
            int maxIndexAllowed = RequestType.values().length - 1;
            if (typeIndex > maxIndexAllowed) {
                System.out.printf("Error. RequestType in %s cannot be greater than %d. Value: %d%n", modelName, maxIndexAllowed, typeIndex);
                requestType = RequestType.GET;
            } else if (typeIndex < 0) {
                System.out.printf("Error. RequestType in %s cannot be less than 0. Value: %d%n", modelName, typeIndex);
                requestType = RequestType.GET;
            } else {
                // 0 = GET, 1 = POST, 2 = RAPIDAPI
                requestType = RequestType.values()[typeIndex];
            }
        }

        @SuppressWarnings("unused")
        Model(String filename, boolean testing) {
            this.filename = filename;
            config = new JSONObject();
            requestType = RequestType.GET;
        }

        public void saveData() {
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
                    // Occurs if:
                    //   The path in config.json contains a typo
                    // OR
                    //   The timeframe does not contain this value
                    // e.g. Some OpenWeather timeframes don't contain a value for "rain"
                    if (value == null) {
                        System.out.printf("%s: Could not find value at %s%n", filename, theirMetric);
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
            System.out.println(filename);
        }

        public void saveXML() {
            XML xml = loadXML("output/GET/MetEir.xml", null);
            JSONArray output = new JSONArray();
            // List of metrics to request
            JSONObject myKeys = config.getJSONObject("Keys");
            JSONObject myUnits = config.getJSONObject("Units");
            // If more xml models are added, this will have to be streamlined
            // For now, the path can be hardcoded
            XML[] times = xml.getChild("product").getChildren("time");
            /*
            From the MetEireann docs:
                For each timestep of the API there are two distinct forecast blocks: A & B
                Block B is related to rainfall accumulations and weather symbol
                Block A is related to everything else
            So when iterating, every second timeFrame must be skipped
            */
            for (int i = 0; i < times.length-1; i += 2) {
                XML thisTimeA = times[i];
                JSONObject timeOutput = new JSONObject();
                String s = thisTimeA.getString("from");
                ZonedDateTime z = ZonedDateTime.parse(s);
                timeOutput.put("Epoch", z.toEpochSecond());

                XML thisTimeB = times[i+1];
                XML precipitation = thisTimeB.getChild("location/precipitation");
                timeOutput.put("Precipitation", precipitation.getFloat("value"));
                timeOutput.put("POP", precipitation.getFloat("probability"));
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
                    XML key = thisTimeA.getChild(theirMetric.replace("/" + attribute, ""));
                    // For further out timeframes, certain metrics aren't included, such as WindGust
                    // Just skip these metrics if that's the case
                    if (key == null) {
                        continue;
                    }
                    double value = Double.parseDouble(key.getString(attribute));
                    Object cFactor = myUnits.get(myMetric);
                    // If there is a calculation to be done
                    if (cFactor != null) {
                        value *= (double) cFactor;
                    }
                    timeOutput.put(myMetric, value);
                }
                output.append(timeOutput);
            }
            String f = filename.replace("xml", "json");
            output.save(new File("output/Refactor/" + f), null);
            System.out.println(f);
        }

        void request() throws IOException, InterruptedException {
            HttpRequest request = switch (requestType) {
                case GET -> get(url);
                case POST -> post(url, header);
                case RAPIDAPI -> getRapid(url, header);
            };
            var client = HttpClient.newHttpClient();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            PrintWriter output = PApplet.createWriter(new File("output/GET/" + filename));
            System.out.println(filename + ": " + response.statusCode());
            output.println(response.body());
            output.flush();
            output.close();
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
            setUrl(String.format(config.getString("URL"), lat, lon));
        }

        public void setUrlFromConfig() {
            setUrl((config.getString("URL")));
        }

        public void setHeader(String s) {
            header = s;
        }

        public void setRoot(String s) {
            root = s;
        }

        public String toString() {
            return filename + " " + url;
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
