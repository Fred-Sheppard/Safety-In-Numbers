import org.xml.sax.SAXException;
import processing.core.PApplet;
import processing.data.JSONArray;
import processing.data.JSONObject;
import processing.data.XML;

import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;

public class WeatherAPI {

    static float lat, lon;
    public static JSONObject globalConfig;

    enum RequestType {
        GET,
        POST,
        RAPIDAPI
    }

    static Connection sqlConnection;
    static Statement sqlStatement;
    static String PATH;
    static PrintWriter logger;


    public static void main(String[] args) throws SQLException, IOException {
        System.out.println("WeatherAPI.java");
        System.out.println(OffsetDateTime.now());
        // Path to directory containing jar file
        File jarPath = new File(WeatherAPI.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        if (jarPath.toString().contains(".jar")) {
            // If being run from a jar file
            PATH = jarPath.getParentFile().getAbsolutePath() + "/";
        } else {
            // Run from inside IDE
            PATH = "";
        }
        globalConfig = PApplet.loadJSONObject(new File(PATH + "config/config.json"));
        lat = globalConfig.getFloat("lat");
        lon = globalConfig.getFloat("lon");
        sqlStatement = initSQL();
        logger = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", true)));

        logger.println();
        logger.println(OffsetDateTime.now());
        JSONObject models = globalConfig.getJSONObject("Models");
        int errorCount = 0;
        for (Object o : models.keys()) {
            String modelName = (String) o;
            Model model = new Model(modelName);
            try {
                String response = model.request();
                model.readAndUpload(response);
            } catch (Exception e) {
                logger.println(e);
                errorCount++;
                System.out.println();
            }
        }
        sqlStatement.close();
        sqlConnection.close();
        String message = String.format("Complete. %d errors", errorCount);
        logger.println(message);
        logger.println();
        logger.flush();
        logger.close();
        System.out.println(message);
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

    private static Statement initSQL() {
        String sqlPath = globalConfig.getString("SQLPath") + globalConfig.getString("SQLDatabase");
        String sqlUser = globalConfig.getString("SQLUser");
        String sqlPass = globalConfig.getString("SQLPassword");
        try {
            sqlConnection = DriverManager.getConnection(sqlPath, sqlUser, sqlPass);
            return sqlConnection.createStatement();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


    static class Model {

        // Contains data for the model found in config.json, under the model name
        private final JSONObject modelConfig;
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
            modelConfig = (JSONObject) JSONPath.getValue(globalConfig, "Models/" + name);
            // Url to be used for HTTP requests
            setUrlFromConfig();
            // Value to be passed as a header in RapidAPI requests
            header = modelConfig.getString("Header");
            // JSON path to the array of time periods in the http request response.
            root = modelConfig.getString("Root");
            // Integer value of RequestType. Can be between 0 and the enum values' length -1
            int typeIndex = modelConfig.getInt("RequestType");
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
            modelConfig = new JSONObject();
            requestType = RequestType.GET;
        }

        // Returns response body for the url given in config.json
        @SuppressWarnings({"unused", "SameParameterValue"})
        String request() throws IOException, InterruptedException {
            System.out.print(name + ": Requesting...");
            HttpRequest request;
            try {
                request = switch (requestType) {
                    case GET -> get(url);
                    case POST -> post(url, header);
                    case RAPIDAPI -> getRapid(url, header);
                };
            } catch (Exception e) {
                throw new RuntimeException(String.format("%s: Error in HTTP url %nurl=%s%n", name, url));
            }
            var client = HttpClient.newHttpClient();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.body().equals("") || response.body() == null) {
                throw new RuntimeException(String.format("%s: HTTP returned empty or null%nurl=%s%n", name, url));
            }
            System.out.print("done. ");
            return response.body();
        }

        public void readAndUpload(String data) {
            System.out.print("Parsing...");
            if (root.equals("XML")) {
                // arr: array of four models
                // ArrayList: ArrayList of timeframes
                // TreeMap: Basically a JSONObject. Epoch, WindSpeed etc.
                ArrayList<TreeMap<String, Object>>[] arr;
                try {
                    arr = readXML(data);
                } catch (Exception e) {
                    throw new RuntimeException(String.format("%s: Error in parsing data.%n%s%n",
                            name, data));
                }
                JSONArray subModels = modelConfig.getJSONArray("SubModels");
                if (subModels == null) {
                    throw new RuntimeException("JSONObject SubModels not found in config.json/" + name);
                }
                for (int i = 0; i < arr.length; i++) {
                    ArrayList<TreeMap<String, Object>> treeMap = arr[i];
                    String modelName = subModels.getString(i);
                    try {
                        upload(treeMap, modelName);
                    } catch (Exception e) {
                        throw new RuntimeException(String.format("%s: Error in uploading to sql. %n%s%n",
                                name, data));
                    }
                }
            } else {
                ArrayList<TreeMap<String, Object>> parsedData;
                try {
                    parsedData = readJSON(data);
                } catch (Exception e) {
                    throw new RuntimeException(String.format("%s: Error in parsing data. %n%s%n",
                            name, data));
                }
                upload(parsedData);
            }
        }

        public ArrayList<TreeMap<String, Object>> readJSON(String data) {
            ArrayList<TreeMap<String, Object>> output = new ArrayList<>();
            // Source of data
            Object jsonSource;
            try {
                jsonSource = parseJSON(data);
            } catch (Exception e) {
                throw new RuntimeException(String.format("%s: Error parsing json. %n%s%n",
                        name, data));
            }
            // Array of timeFrames
            // new JSONArray(Aeris.json/response/periods)
            JSONArray times = (JSONArray) JSONPath.getValue(jsonSource,
                    modelConfig.getString("Root"));
            // List of keys you want to include
            JSONObject myKeys = modelConfig.getJSONObject("Keys");
            JSONObject myUnits = modelConfig.getJSONObject("Units");
            for (int i = 0; i < times.size(); i++) {
                JSONObject thisTime = times.getJSONObject(i);
                TreeMap<String, Object> timeOutput = new TreeMap<>();
                // e.g. Accu+3h. Offset = 3
                timeOutput.put("Offset", i);
                OffsetDateTime odt = OffsetDateTime.now();
                timeOutput.put("RequestTime", odt.toEpochSecond());
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
                        double dValue;
                        if (value instanceof Integer || value instanceof Double) {
                            dValue = ((Number) value).doubleValue() * (double) cFactor;
                            value = dValue;
                        }
                    }
                    // windSpeed: 12.5
                    timeOutput.put(myMetric, value);
                }
                // Add timeFrame to list of timeFrames
                output.add(timeOutput);
            }
            System.out.print("done. ");
            return output;
        }

        public ArrayList<TreeMap<String, Object>>[] readXML(String data) {
            XML xml = null;
            try {
                xml = XML.parse(data);
            } catch (IOException | ParserConfigurationException | SAXException e) {
                throw new RuntimeException(e);
            }
            JSONObject myKeys = modelConfig.getJSONObject("Keys");
            JSONObject myUnits = modelConfig.getJSONObject("Units");

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
                cutoffs[i] = timeFrameCutOffs[i].getString("from");
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
                // e.g. Accu+3h. Offset = 3
                timeOutput.put("Offset", i);
                OffsetDateTime odt = OffsetDateTime.now();
                timeOutput.put("RequestTime", odt.toEpochSecond());
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
                System.out.print(s + ", ");
            }
            return outputs;
        }

        public void upload(ArrayList<TreeMap<String, Object>> inputArray) {
            try {
                upload(inputArray, name);
            } catch (Exception e) {
                throw new RuntimeException(String.format("%s: Error in parsing data. %n%s%n",
                        name, inputArray));
            }
        }

        // Uploads data from the model's filename to SQL database
        public void upload(ArrayList<TreeMap<String, Object>> inputArray, String tableName) throws SQLException {
            System.out.print("Uploading...");
            // Gets list of columns, i.e. what needs to be queried
            String keyQuery = String.format("select column_name from information_schema.columns where table_schema='%s'" +
                    " and table_name='%s'", globalConfig.getString("SQLDatabase"), tableName);
            ResultSet keyResponse = sqlStatement.executeQuery(keyQuery);
            // If the response returns empty
            if (!keyResponse.next()) {
                throw new RuntimeException(String.format("Error querying table for %s. Possible typo? %nTableName=%s%n",
                        tableName, tableName));
            }
            ArrayList<String> keysList = new ArrayList<>();
            // By querying keyResponse.next() above, the head has been moved to the next index
            // So, to read the 1st element, use a do while loop. next() is only called after the first read
            do {
                // SQL indexes from 1
                keysList.add(keyResponse.getString(1));
            } while (keyResponse.next());
            Collections.sort(keysList);
            // JDBC object to execute sql queries
            // Array of eventual inputs into the table
            // e.g. (1667523600, 10.9, 241),
            StringBuilder[] inputsBuilders = new StringBuilder[inputArray.size()];
            // Eventual list of headers
            // e.g. (Epoch, WindSpeed, WindDir)
            // Needs to be sorted first
            // It's a Set of keys, it wil always contain Strings
            StringBuilder keysBuilder = new StringBuilder();
            // Iterate through sorted keys
            for (String key : keysList) {
                if (key.equalsIgnoreCase("ID")) continue;
                keysBuilder.append(key);
                keysBuilder.append(","); // Epoch,
            } // Epoch, WindSpeed, WindDir,
            // Removes last comma
            keysBuilder.deleteCharAt(keysBuilder.length() - 1); // Epoch, WindSpeed, WindDir
            for (int i = 0; i < inputArray.size(); i++) {
                // JSON Object for current timeframe
                TreeMap<String, Object> thisTime = inputArray.get(i);
                // Init builder for list of inputs
                inputsBuilders[i] = new StringBuilder();
                StringBuilder builder = inputsBuilders[i];
                builder.append("(");
                for (String key : thisTime.keySet()) { // WindSpeed
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
                int response = sqlStatement.executeUpdate(query.toString());
                System.out.printf("Query OK, %d rows affected%n", response);
            } catch (SQLException e) {
                throw new RuntimeException(String.format("Error uploading in %s. %n%s%n",
                        name, query));
            }
        }

        public void setUrl(String s) {
            url = s;
        }

        @SuppressWarnings("unused")
        public void setUrl(float lat, float lon) {
            setUrl(String.format(modelConfig.getString("URL"), lat, lon));
        }

        public void setUrlFromConfig() {
            setUrl(String.format(modelConfig.getString("URL"), lat, lon));
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
                    .header("X-RapidAPI-Key", "c4bcddcd70msh61696ab501cad75p134af7jsndf9884823caf")
                    .header("X-RapidAPI-Host", data)
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();
        }
    }
}
