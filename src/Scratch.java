import processing.core.PApplet;
import processing.data.JSONArray;
import processing.data.JSONObject;
import processing.data.StringList;

import java.io.File;
import java.nio.file.InvalidPathException;

public class Scratch {

    public static StringList path = new StringList();

    public static void main(String[] args) {
        JSONObject json = PApplet.loadJSONObject(new File("data/Input.json"));
//        JSONArray json = PApplet.loadJSONArray(new File("data/Input2.json"));
//        tree(arr);
        String path = "/One/Age/Value/";
        System.out.println(jsonPathToObject(json, path));
    }

    /**
     *
     * @param json
     * JSON object in which to retrieve data
     * @param path
     * String
     * @return
     */
    static Object jsonPathToObject(Object json, String path) {
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
            throw new InvalidPathException(s, "Object " + s + " does not exist");
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
            if (val instanceof JSONObject) {
                //If it's another object, go inside that one
                tree((JSONObject) val);
            } else if (val instanceof JSONArray) {
                //If it's another arr, go inside that one
                tree((JSONArray) val);
            } else {
                //We're done with this layer
                //Go back a layer and go again
                printStringList(path);
                path.pop();
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
        //For starting array
        if (path.size() == 0) path.append("/[]");
        // For every Object in arr
        for (int i = 0; i < arr.size(); i++) {
            //Array Objects don't have keys, so use index
            String key = "[" + i + "]";
            Object val = arr.get(i);
            if (val instanceof JSONObject) {
                // If it's another object, go inside that one
                path.append("/" + key);
                tree((JSONObject) val);
            } else if (val instanceof JSONArray) {
                // If it's another arr, go inside that one
                path.append("/" + key);
                tree((JSONArray) val);
            } else {
                // We're done with this layer
                // Go back a layer and go again
                path.append("/" + key);
                printStringList(path);
                path.pop();
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
