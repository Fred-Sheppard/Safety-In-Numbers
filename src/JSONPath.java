import processing.data.JSONArray;
import processing.data.JSONObject;

//import java.nio.file.InvalidPathException;

public class JSONPath {

    /**
     * Returns the data at a given json path.
     * Use numbers to represent array indexes.
     * e.g. "People/0/Age"
     *
     * @param json JSON object from which to retrieve data
     * @param path String representation of path to data.
     * @return Value at path
     */
    static Object getValue(Object json, String path) {
        // Todo rename tree() to readObject, readArray
        //  Remove preview language features
        String[] pathArray = path.split("/", 0);
        return readJSON(json, pathArray, 0);
    }

    static Object readJSON(Object o, String[] path, int index) {
//      At end of path, return value
        if (index >= path.length) return o;
        String s = path[index];
        // Fixes problems with empty strings due to .split();
        if (s.equals("")) return readJSON(o, path, ++index);
        // Rip preview features
//        Object child = switch (o) {
//            case JSONObject j -> j.get(s);
//            case JSONArray j -> j.get(Integer.parseInt(s));
//            default -> null;
//        };
        Object child;
        if (o instanceof JSONObject) {
            // If JSONObject, read next layer with String key
            child = ((JSONObject) o).get(s);
        } else if (o instanceof JSONArray) {
            // If JSONArray, read next layer with int key
            child = ((JSONArray) o).get(Integer.parseInt(s));
        } else {
            // If anything else (Integer, String), no children
            // If we reach this, we're asking for a child that doesn't exist
            // i.e. the path should end, but goes a layer deeper
            child = null;
        }
        // Used to throw an error, but we don't always want that
        // With at typo in the path, an error is more beneficial
        // However, sometimes Model timeframes simply don't include a metric for some reason
        // e.g. OpenWeather leaves out a value for "rain" occasionally
        // In that case, we should simply inform the user and move on
        if (child == null) {
//            throw new InvalidPathException(s, "JSONReader: Object \"" + s + "\" does not exist");
            return null;
        }

        // Read next layer
        return readJSON(child, path, ++index);
    }

}