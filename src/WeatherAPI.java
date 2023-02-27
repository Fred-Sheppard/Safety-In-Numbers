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


/**
 * Class to read a set of weather models in config.json,
 * Query their API using java.net's HTTP library,
 * Parse the data
 * And upload to a MariaDB MySQL database.
 */
public class WeatherAPI {

    /**
     * JSON file containing list of weather models,
     * latitude and longitude and SQL credentials
     */
    public static JSONObject globalConfig;
    /**
     * The coordinates of the location for the forecasts
     */
    static float lat, lon;
    /**
     * SQL object for querying the database
     */
    static Connection sqlConnection;
    /**
     * SQL object for querying the database
     */
    static Statement sqlStatement;
    /**
     * Path to parent directory of where the code is being run from
     * Either the main class's or the jar file's directory
     */
    static String PATH;
    /**
     * Object to write to logs.txt, which logs time of app running and any errors encountered
     */
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
        // Log to "log.txt" in the same directory as the running jar file
        // true appends to existing file
        logger = new PrintWriter(new BufferedWriter(new FileWriter(PATH + "log.txt", true)));

        File configFilePath;
        if (args.length > 0) {
            if (args.length == 1) {
                configFilePath = new File(args[0]);
            } else {
                logger.printf("Invalid number of arguments to file: %d%n", args.length);
                configFilePath = new File(PATH + "config/config.json");
            }
        } else {
            configFilePath = new File(PATH + "config/config.json");
        }
        globalConfig = PApplet.loadJSONObject(configFilePath);
        System.out.println(configFilePath);

        lat = globalConfig.getFloat("lat");
        lon = globalConfig.getFloat("lon");
        sqlStatement = initSQL();

        logger.println();
        logger.println(OffsetDateTime.now());
        logger.println(configFilePath);
        JSONObject models = globalConfig.getJSONObject("Models");
        int errorCount = 0;
        // For every model in config.json
        for (Object o : models.keys()) {
            String modelName = (String) o;
            Model model = new Model(modelName);
            try {
                // HTTP request
                String response = model.request();
                // Parse and upload the data to SQL
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

    /**
     * Loads JSON file, regardless if it's an Object or Array.
     *
     * @param path Path to the file to be parsed
     */
    public static Object loadJSON(String path) {
        File file = new File(path);
        try {
            return PApplet.loadJSONObject(file);
        } catch (RuntimeException e) {
            return PApplet.loadJSONArray(file);
        }
    }

    /**
     * Parses a JSON String, regardless if it's an Object or Array.
     *
     * @param data JSON data in String format
     */
    public static Object parseJSON(String data) {
        try {
            return JSONObject.parse(data);
        } catch (RuntimeException e) {
            return JSONArray.parse(data);
        }
    }

    /**
     * Function to load and parse an XML file.
     * Taken from processing.data.XML.
     *
     * @param path    Path to file to be parsed
     * @param options Options for loading - use null as default
     * @return XML object created from the parsed data
     */
    public static XML loadXML(String path, String options) {
        try {
            BufferedReader reader = PApplet.createReader(new File(path));
            return new XML(reader, options);
        } catch (ParserConfigurationException | SAXException | IOException var4) {
            throw new RuntimeException(var4);
        }
    }

    /**
     * Creates a new SQL statement from the database path found in config.json.
     *
     * @return new SQL Statement
     */
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

    /**
     * Types of HTTP request possible
     * RapidAPI uses a GET request with additional headers, and gets its own function
     */
    enum RequestType {
        GET,
        POST,
        RAPIDAPI
    }

    /**
     * Class containing various methods for requesting, parsing and uploading forecast data
     */
    static class Model {

        /**
         * Contains data for the model found in config.json, under the model name
         */
        private final JSONObject modelConfig;
        /**
         * Type of HTTP request that will be called. GET, POST or RAPIDAPI
         */
        private final RequestType requestType; // GET
        /**
         * Name of input and output file
         */
        public String name; // Aeris_1h.json
        /**
         * URL to be used for HTTP requests
         */
        private String url; // "https://aerisweather1.p.rapidapi.com/forecasts/%f,%f?plimit=72&filter=1hr"
        /**
         * Header data to be used with RapidAPI requests
         */
        private String header; // "aerisweather1.p.rapidapi.com"
        /**
         * JSON path to the array of time periods in the http request response.
         */
        private String root; // "response/0/periods"

        /**
         * Creates a new Model, retrieving data from config.json
         *
         * @param name The name of the model's entry in config.json
         */
        Model(String name) {
            this.name = name;
            modelConfig = (JSONObject) JSONPath.getValue(globalConfig, "Models/" + name);
            // Url to be used for HTTP requests
            setUrlFromConfig();
            header = modelConfig.getString("Header");
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

        /**
         * Runs a HTTP request to the url found in config.json
         *
         * @return response body
         */
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

        /**
         * Reads the supplied data, and sends the data to be uploaded.
         *
         * @param data HTTP request response body
         */
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

        /**
         * Standardises the parsed JSON data, ensuring all models use the same format
         *
         * @param data Parsed JSON data
         * @return A list of key-value pairs whose keys match those in config.json
         */
        public ArrayList<TreeMap<String, Object>> readJSON(String data) {
            ArrayList<TreeMap<String, Object>> output = new ArrayList<>();
            // Source of data
            Object jsonSource;
            try {
                // Using PApplet's parser
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
            // List of conversion factors to multiply by
            JSONObject myUnits = modelConfig.getJSONObject("Units");
            for (int i = 0; i < times.size(); i++) {
                JSONObject thisTime = times.getJSONObject(i);
                TreeMap<String, Object> timeOutput = new TreeMap<>();
                // Amount of hours/days in the future the request lies
                timeOutput.put("Offset", i);
                OffsetDateTime odt = OffsetDateTime.now();
                // Time of request in Unix time
                timeOutput.put("RequestTime", odt.toEpochSecond());
                // Find their key's name, get value at this key
                for (Object o : myKeys.keys()) {
                    // myMetric is the standardised name for outputting
                    // theirMetric is what they call the metric, and may be path through multiple json Objects
                    // e.g. Accu.json WindSpeed: Wind/Speed/Value
                    String myMetric = (String) o; // WindSpeed
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
                        double factorAsDouble = (double) cFactor;
                        if (value instanceof Integer || value instanceof Double) {
                            value = ((Number) value).doubleValue() * factorAsDouble;
                        }
                    }
                    // WindSpeed: 12.5
                    timeOutput.put(myMetric, value);
                }
                // Add timeFrame to list of timeFrames
                output.add(timeOutput);
            }
            System.out.print("done. ");
            return output;
        }

        /**
         * Standardises the parsed XML data, ensuring all models use the same format
         *
         * @param data Parsed XML data
         * @return A list of key-value pairs whose keys match those in config.json
         */
        public ArrayList<TreeMap<String, Object>>[] readXML(String data) {
            XML xml = null;
            try {
                xml = XML.parse(data);
            } catch (IOException | ParserConfigurationException | SAXException e) {
                throw new RuntimeException(e);
            }
            JSONObject myKeys = modelConfig.getJSONObject("Keys");
            JSONObject myUnits = modelConfig.getJSONObject("Units");

            /*
             MetEir.xml contains 4 model timeframes
             Each model spans a specific timeframe
             Save each to a unique ArrayList

             Go through header at top of xml file, find time range for each model
             Save the starting time for each model to "cutoffs"
             When iterating through times, if the current time starts at a cutoff time,
               add the following times to the next ArrayList (outputs[modelIndex])
            */

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
                // Amount of hours/days in the future the request lies
                timeOutput.put("Offset", i);
                // Time of request in Unix time
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

                // Block B
                XML thisTimeB = times[i + 1];
                XML precipitation = thisTimeB.getChild("location/precipitation");
                timeOutput.put("Precipitation", precipitation.getFloat("value"));
                timeOutput.put("POP", precipitation.getFloat("probability"));

                // Block A
                for (Object o : myKeys.keys()) {
                    // myMetric is the standardised name for outputting
                    // theirMetric is the path to the value
                    String myMetric = (String) o;
                    String theirMetric = myKeys.getString(myMetric);
                    // The final part of a path is an attribute and not a child
                    // It must be split and treated differently
                    String[] path = theirMetric.split("/");
                    String attribute = path[path.length - 1];
                    /*
                     We need: Child, Attribute
                     Child = Path - Attribute
                     e.g. location/windSpeed/mps ->
                     (Child) location/windSpeed/
                     (Attribute) mps
                    */
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

        /**
         * Uploads data to SQL, sending any errors to be logged
         *
         * @param inputArray Standardised data to be uploaded
         */
        public void upload(ArrayList<TreeMap<String, Object>> inputArray) {
            try {
                upload(inputArray, name);
            } catch (Exception e) {
                throw new RuntimeException(String.format("%s: Error in parsing data. %n%s%n",
                        name, inputArray));
            }
        }


        /**
         * Uploads data into MariaDB database
         *
         * @param inputArray Standardised data to be uploaded
         * @param tableName  The name of the table to upload to
         */
        public void upload(ArrayList<TreeMap<String, Object>> inputArray, String tableName) throws SQLException {
            System.out.print("Uploading...");
            // Gets list of columns, i.e. what needs to be queried
            String keyQuery = String.format(
                    "select column_name from information_schema.columns where table_schema='%s' and table_name='%s'",
                    globalConfig.getString("SQLDatabase"), tableName);
            ResultSet keyResponse = sqlStatement.executeQuery(keyQuery);
            // If the response returns empty
            if (!keyResponse.next()) {
                throw new RuntimeException(String.format("Error querying table for %s. Possible typo? %nTableName=%s%n",
                        tableName, tableName));
            }
            ArrayList<String> keysList = new ArrayList<>();
            // By querying keyResponse.next() above, the head has been moved to the next index
            // So, to read the 1st element, use a do while loop. next() is only called after the first read
            // https://javarevisited.blogspot.com/2016/10/how-to-check-if-resultset-is-empty-in-Java-JDBC.html#axzz7mueiayIp
            do {
                // SQL indexes from 1
                keysList.add(keyResponse.getString(1));
            } while (keyResponse.next());
            Collections.sort(keysList);
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
                // "ID" is the databases iterator column, and is filled automatically by SQL
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
                    // If null, just upload as null
                    String valStr = val == null ? null : val.toString();
                    builder.append(valStr); // 10.4
                    builder.append(","); // 10.4,
                } // (1667523600, 10.9, 241,
                //Removes final comma
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

        public void setUrl(String url, float lat, float lon) {
            setUrl(String.format(url, lat, lon));
        }

        public void setUrlFromConfig() {
            setUrl(String.format(modelConfig.getString("URL"), lat, lon));
        }

        public void setHeader(String s) {
            header = s;
        }

        public void setRoot(String s) {
            root = s;
        }

        public String toString() {
            return name + " " + url;
        }

        /**
         * Performs a HTTP GET request.
         * <a href="https://techndeck.com/get-request-using-java-11-httpclient-api/">Techndeck.com</a>.
         *
         * @param url URL to be queried
         * @return HTTP response
         */
        HttpRequest get(String url) {
            return HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
        }

        /**
         * Performs a HTTP POST request.
         * <a href="https://techndeck.com/post-request-with-json-using-java-11-httpclient-api/">Techndeck.com</a>.
         *
         * @param url  URL to be queried
         * @param data JSON data to send in POST request
         * @return HTTP response
         */
        HttpRequest post(String url, String data) {
            return HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(data))
                    .build();
        }

        /**
         * GET request that appends additional headers, for use with rapidapi.com
         * Code from rapidapi.com's request builder
         *
         * @param url  URL to be queried
         * @param data Header data
         * @return HTTP response
         */
        HttpRequest getRapid(String url, String data) {
            return HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-RapidAPI-Key", "c4bcddcd70msh61696ab501cad75p134af7jsndf9884823caf")
                    .header("X-RapidAPI-Host", data)
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .build();
        }
    }
}
