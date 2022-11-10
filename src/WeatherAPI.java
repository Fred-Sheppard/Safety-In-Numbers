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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;

public class WeatherAPI {

    static float lat, lon;
    public static JSONObject configs;

    enum RequestType {
        GET,
        POST,
        RAPIDAPI
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        // Todo add processing.core to lib
        System.out.println("WeatherAPI.java");
        configs = PApplet.loadJSONObject(new File("data/config.json"));
        lat = configs.getFloat("lat");
        lon = configs.getFloat("lon");

        Model model = new Model("MetEir");
        Statement statement = initSQL();
        try {
            statement.executeUpdate("DELETE FROM OpenWeather_1h");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        String response = model.request();
        model.readAndUpload(response, true);

//        JSONObject models = configs.getJSONObject("Models");
//        for (Object o : models.keys()) {
//            String modelName = (String) o;
//            Model model = new Model(modelName);
//            String response = model.request();
//            model.readAndUpload(response, false);
//        }
    }

    // Loads JSON file, regardless if it's an Object or Array
    @SuppressWarnings("unused")
    public static Object loadJSON(String filename) {
        File file = new File(filename);
        try {
            return PApplet.loadJSONObject(file);
        } catch (RuntimeException e) {
            return PApplet.loadJSONArray(file);
        }
    }

    // Parses a JSON String, regardless if it's an Object or Array
    public static Object parseJSON(String data) {
        try {
            return JSONObject.parse(data);
        } catch (RuntimeException e) {
            return JSONArray.parse(data);
        }
    }

    // Taken from processing.data.XML
    @SuppressWarnings("unused")
    public static XML loadXML(String filename, String options) {
        try {
            BufferedReader reader = PApplet.createReader(new File(filename));
            return new XML(reader, options);
        } catch (ParserConfigurationException | SAXException | IOException var4) {
            throw new RuntimeException(var4);
        }
    }

    public static Statement initSQL() {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/models", "root", "12345678");
            return connection.createStatement();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    public static void clearTables() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/models", "root", "12345678");
        Statement statement = connection.createStatement();
        String s = "delete from %s";
        String[] data = PApplet.loadStrings(new File("data/temp.txt"));
        for (String s1 : data) {
            statement.executeUpdate(String.format(s, s1));
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
        public String name; // Aeris_1h.json
        // Type of HTTP request that will be called. GET, POST or RAPIDAPI
        private final RequestType requestType; // GET
        // JSON path to the array of time periods in the http request response.
        private String root; // "response/0/periods"

        Model(String name) {
            this.name = name;
            // Entry in config.json that contains values for url, root, keys etc
            config = (JSONObject) JSONPath.getValue(configs, "Models/" + name);
            // Url to be used for HTTP requests
            setUrlFromConfig();
            // Value to be passed as a header in RapidAPI requests
            header = config.getString("Header");
            // JSON path to the array of time periods in the http request response.
            root = config.getString("Root");
            // Integer value of RequestType. Can be between 0 and the enum values' length -1
            int typeIndex = config.getInt("RequestType");
            int maxIndexAllowed = RequestType.values().length - 1;
            if (typeIndex > maxIndexAllowed) {
                System.out.printf("Error. RequestType in %s cannot be greater than %d. Value: %d%n", name, maxIndexAllowed, typeIndex);
                requestType = RequestType.GET;
            } else if (typeIndex < 0) {
                System.out.printf("Error. RequestType in %s cannot be less than 0. Value: %d%n", name, typeIndex);
                requestType = RequestType.GET;
            } else {
                // 0 = GET, 1 = POST, 2 = RAPIDAPI
                requestType = RequestType.values()[typeIndex];
            }
        }

        @SuppressWarnings("unused")
        Model(String name, boolean testing) {
            this.name = name;
            config = new JSONObject();
            requestType = RequestType.GET;
        }

        // Returns response body for the url given in config.json
        @SuppressWarnings("unused")
        String request() throws IOException, InterruptedException {
            System.out.print(name + ": Requesting...");
            HttpRequest request = switch (requestType) {
                case GET -> get(url);
                case POST -> post(url, header);
                case RAPIDAPI -> getRapid(url, header);
            };
            var client = HttpClient.newHttpClient();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.print("done. ");
            return response.body();
        }

        public void readAndUpload(String data, boolean doLog) {
            System.out.print("Parsing...");
            if (root.equals("XML")) {
                var arr = readXML(data, doLog);
                JSONArray subModels = config.getJSONArray("SubModels");
                if (subModels == null) {
                    throw new RuntimeException("JSONObject SubModels not found in " + name);
                }
                for (int i = 0; i < arr.length; i++) {
                    // Todo will this arraylist be in the correct order?
                    //  If not, sort it and reorder submodels in config.json
                    ArrayList<TreeMap<String, Object>> treeMap = arr[i];
                    String modelName = subModels.getString(i);
                    upload(treeMap, modelName);
                }
            } else {
                upload(readJSON(data, doLog));
            }
        }

    public ArrayList<TreeMap<String, Object>> readJSON(String data, boolean doLog) {
        ArrayList<TreeMap<String, Object>> output = new ArrayList<>();
        // Source of data
        Object jsonSource = parseJSON(data);
        // Array of timeFrames
        // new JSONArray(Aeris.json/response/periods)
        JSONArray times = (JSONArray) JSONPath.getValue(jsonSource,
                config.getString("Root"));
        // List of keys you want to include
        JSONObject myKeys = config.getJSONObject("Keys");
        JSONObject myUnits = config.getJSONObject("Units");
        for (int i = 0; i < times.size(); i++) {
            JSONObject thisTime = times.getJSONObject(i);
            TreeMap<String, Object> timeOutput = new TreeMap<>();
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
//                        System.out.printf("%s: Could not find value at %s%n", name, theirMetric);
                    timeOutput.put(myMetric, null);
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
                timeOutput.put(myMetric, value);
            }
            // Add timeFrame to list of timeFrames
            output.add(timeOutput);
        }
        System.out.print("done. ");
        if (doLog) {
            String logPath = "logs/" + name + ".txt";
            PrintWriter pw = PApplet.createWriter(new File(logPath));
            for (var v : output) {
                pw.println(v);
            }
            pw.flush();
            pw.close();
            System.out.print("Logged to " + logPath + ". ");
        }
        return output;
    }

    public ArrayList<TreeMap<String, Object>>[] readXML(String data, boolean doLog) {
        XML xml = null;
        try {
            xml = XML.parse(data);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        }
        // List of metrics to request
        JSONObject myKeys = config.getJSONObject("Keys");
        JSONObject myUnits = config.getJSONObject("Units");

        // MetEir.xml contains 4 model timeframes
        // Each model spans a specific timeframe
        // Output each to a separate file

        // Go through header at top of xml file, find time range for each model
        // Save the starting time for each model to "cutoffs"
        // When iterating through times, if the current time starts at a cutoff time,
        // add the following times to the next file (outputs[modelIndex])
        XML[] timeFrameCutOffs = xml.getChild("meta").getChildren("model");
        // Set cutoff times
        String[] cutoffs = new String[timeFrameCutOffs.length];
        for (int i = 0; i < timeFrameCutOffs.length; i++) {
            String from = timeFrameCutOffs[i].getString("from");
            cutoffs[i] = from;
        }
        // Initialise outputs
        @SuppressWarnings("unchecked")
        ArrayList<TreeMap<String, Object>>[] outputs = new ArrayList[4];
        for (int i = 0; i < outputs.length; i++) {
            outputs[i] = new ArrayList<>();
        }

        // If more xml models are added, this will have to be streamlined
        // For now, the path can be hardcoded
        XML[] times = xml.getChild("product").getChildren("time");
        // Index of which file to output to
        // Harmonie, ECMWF 1h, 3h, 6h
        int modelIndex = 0;
            /*
            From the MetEireann docs:
                For each timestep of the API there are two distinct forecast blocks: A & B
                Block B is related to rainfall accumulations and weather symbol
                Block A is related to everything else
            So when iterating, every second timeFrame must be skipped
            */
        for (int i = 0; i < times.length - 1; i += 2) {
            XML thisTimeA = times[i];
            TreeMap<String, Object> timeOutput = new TreeMap<>();
            String s = thisTimeA.getString("from");
            // If this time's "from" value is the same as the next cutoff,
            // Start appending to that cutoff
            if (modelIndex < cutoffs.length - 1) {
                if (s.equals(cutoffs[modelIndex + 1])) {
                    modelIndex++;
                }
            }
            OffsetDateTime z = OffsetDateTime.parse(s);
            timeOutput.put("Epoch", z.toEpochSecond());

            XML thisTimeB = times[i + 1];
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
                // We need: Child, Attribute
                // Child = Path - Attribute
                // e.g. location/windSpeed/mps ->
                // (Child) location/windSpeed/
                // (Attribute) mps
                String childPath = theirMetric.replace("/" + attribute, "");
                XML key = thisTimeA.getChild(childPath);
                // For further out timeframes, certain metrics aren't included, such as WindGust
                // Just skip these metrics if that's the case
                if (key == null) {
                    // Moves on to next Metric e.g. WindGust -> WindSpeed
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
            outputs[modelIndex].add(timeOutput);
        }
        String[] filenames = {
                "Harmonie_1h",
                "ECMWF_1h",
                "ECMWF_3h",
                "ECMWF_6h"};
        for (String s : filenames) {
            System.out.print(s);
        }
        if (doLog) {
            for (int i = 0; i < filenames.length; i++) {
                String filename = filenames[i];
                String logPath = "logs/" + filename + ".txt";
                PrintWriter pw = PApplet.createWriter(new File(logPath));
                pw.println(outputs[i]);
                pw.flush();
                pw.close();
                System.out.print("Logged to " + logPath + ". ");
            }
        }
        return outputs;
    }

    public void upload(ArrayList<TreeMap<String, Object>> inputArray) {
        upload(inputArray, name);
    }

    // Uploads data from the model's filename to SQL database
    public void upload(ArrayList<TreeMap<String, Object>> inputArray, String tableName) {
        System.out.print("Uploading...");
        JSONObject keys = config.getJSONObject("Keys");
        // JDBC object to execute sql queries
        Statement statement = initSQL();
        // Array of eventual inputs into the table
        // e.g. (1667523600, 10.9, 241),
        StringBuilder[] inputsBuilders = new StringBuilder[inputArray.size()];
        // Eventual list of headers
        // e.g. (Epoch, WindSpeed, WindDir)
        // Needs to be sorted first
        @SuppressWarnings("unchecked")
        // It's a Set of keys, it wil always contain Strings
        ArrayList<String> keysList = new ArrayList<String>(keys.keys());
        Collections.sort(keysList);
        StringBuilder keysBuilder = new StringBuilder();
        // Iterate through sorted keys
        for (String key : keysList) {
            keysBuilder.append(key);
            keysBuilder.append(","); // Epoch,
        } // Epoch, WindSpeed, WindDir,
        // Removes last comma
        keysBuilder.deleteCharAt(keysBuilder.length() - 1); // Epoch, WindSpeed, WindDir
        for (int i = 0; i < inputArray.size(); i++) {
            // JSON Object for current time
            TreeMap<String, Object> thisTime = inputArray.get(i);
            // Init builder for list of inputs
            inputsBuilders[i] = new StringBuilder();
            StringBuilder builder = inputsBuilders[i];
            builder.append("(");
            for (String key : thisTime.keySet()) {
                Object val = thisTime.get(key); // 10.4
                String valStr = val == null ? null : val.toString();
                builder.append(valStr); // 10.4
                builder.append(","); // 10.4,
            } // (1667523600, 10.9, 241,
            builder.deleteCharAt(builder.length() - 1); // (1667523600, 10.9, 241
            if (i < inputArray.size() - 1) {
                // There are more timeframes to parse
                builder.append("),\n"); // (1667523600, 10.9, 241),
            } else {
                // Final timeframe parsed, add a semicolon
                builder.append(");\n"); // (1667523600, 10.9, 241);
            }
        }
        StringBuilder query = new StringBuilder();
        query.append("INSERT INTO ");
        query.append(tableName);
        query.append(" (");
        query.append(keysBuilder); // Epoch, WindSpeed, WindDir
        query.append(")\nVALUES\n");
        for (StringBuilder sb : inputsBuilders) {
            query.append(sb);
        }
        // INSERT INTO test4 (Epoch, WindSpeed, WindDir)
        // VALUES
        // (1667523600, 10.9, 241),
        // (1667526326, 11.2, 356);
        try {
            int response = statement.executeUpdate(query.toString());
            System.out.printf("Query OK, %d rows affected%n", response);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setUrl(String s) {
        url = s;
    }

    @SuppressWarnings("unused")
    public void setUrl(float lat, float lon) {
        setUrl(String.format(config.getString("URL"), lat, lon));
    }

    public void setUrlFromConfig() {
        setUrl(String.format(config.getString("URL"), lat, lon));
    }

    @SuppressWarnings("unused")
    public void setHeader(String s) {
        header = s;
    }

    @SuppressWarnings("unused")
    public void setRoot(String s) {
        root = s;
    }

    public String toString() {
        return name + " " + url;
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
