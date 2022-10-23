import processing.core.PApplet;
import processing.data.JSONArray;
import processing.data.JSONObject;
import processing.data.StringList;

import java.io.File;
import java.nio.file.InvalidPathException;

public class JSONPath {

    public static StringList path = new StringList();

    public static void main(String[] args) {
//        JSONObject json = PApplet.loadJSONObject(new File("data/Object.json"));
        JSONArray json = PApplet.loadJSONArray(new File("data/Array.json"));
        tree(json);
        String path = "1/Age/Unit";
        System.out.println(getValue(json, path));
    }

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
        String[] pathArray = path.split("/", 0);
        return readJSON(json, pathArray, 0);
    }

    static Object readJSON(Object o, String[] path, int index) {
//      At end of path, return value
        if (index >= path.length) return o;
        String s = path[index];
        // Fixes problems with empty strings due to .split();
        if (s.equals("")) return readJSON(o, path, ++index);
        Object child = switch (o) {
            // If JSONObject, read next layer with String key
            case JSONObject j -> j.get(s);
            // If JSONArray, read next layer with int key
            case JSONArray j -> j.get(Integer.parseInt(s));
            // If anything else (Integer, String), no children
            default -> null;
        };
        if (child == null) {
            throw new InvalidPathException(s, "JSONReader: Object \"" + s + "\" does not exist");
        }
        // Read next layer
        return readJSON(child, path, ++index);
    }

    public static void tree(JSONObject json) {
        //For every Object in JSON
        for (Object o : json.keys()) {
            String key = (String) o;
            Object val = json.get(key);
            //Add current key to path
            path.append("/" + key);
            switch (val) {
                // Read next layer
                case JSONObject j -> tree(j);
                case JSONArray j -> tree(j);
                default -> {
                    // Reached final value, finish and go back a layer
                    printStringList(path);
                    path.pop();
                }
            }
        }
        try {
            path.pop();
        } catch (RuntimeException e) {
            //If we can't pop any more, we're done the final object
            System.out.println("Finished Parsing JSON");
        }
    }

    public static void tree(JSONArray arr) {
        // For every Object in arr
        for (int i = 0; i < arr.size(); i++) {
            //Array Objects don't have keys, so use index
            path.append("/" + i);
            Object val = arr.get(i);
            switch (val) {
                // Read next layer
                case JSONObject j -> tree(j);
                case JSONArray j -> tree(j);
                default -> {
                    // Reached final value, finish and go back a layer
                    printStringList(path);
                    path.pop();
                }
            }
        }
        try {
            path.pop();
        } catch (RuntimeException e) {
            // The first array has no key
            // A failed pop may just mean you're finished one layer
        }
    }

    static void printStringList(StringList list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s);
        }
        System.out.println(sb);
    }
}
