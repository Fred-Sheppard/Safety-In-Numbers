import processing.core.PApplet;
import processing.data.JSONArray;
import processing.data.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;

public class Refactor {

//    public static void main(String[] args) {
//        accu();
//        aeris();
//    }

    static void accu() {
        JSONArray times = PApplet.loadJSONArray(new File("output/GET/Accu.json"));
        ArrayList<JSONObject> list = new ArrayList<>();

        for (int i = 0; i < times.size(); i++) {
            JSONObject thisFrame = new JSONObject();
            JSONObject json = (JSONObject) times.get(i);

            JSONObject wind = json.getJSONObject("Wind");
            double windSpeed = wind
                    .getJSONObject("Speed")
                    .getDouble("Value");
            int windDir = wind
                    .getJSONObject("Direction")
                    .getInt("Degrees");
            int epoch = json.getInt("EpochDateTime");
            thisFrame.put("DateTime", json.get("DateTime"));
            thisFrame.put("Epoch", epoch);
            thisFrame.put("WindSpeed", windSpeed);
            thisFrame.put("WindDir", windDir);

            list.add(thisFrame);
        }
//        list.sort(new JSONEpochComparator());
        JSONArray output = new JSONArray();
        for (JSONObject j : list) {
            output.append(j);
        }
        System.out.println(output);
        output.save(new File("output/Refactor/Accu.json"), null);
    }

    static void aeris() {
        JSONObject parent = PApplet.loadJSONObject(new File("output/GET/Aeris.json"));
        JSONArray times = parent.getJSONArray("response")
                .getJSONObject(0)
                .getJSONArray("periods");
        ArrayList<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            JSONObject json = times.getJSONObject(i);
            JSONObject thisFrame = new JSONObject();
            thisFrame.put("Epoch", json.get("timestamp"));
            thisFrame.put("DateTime", json.get("dateTimeISO"));
            thisFrame.put("WindSpeed", json.get("windSpeedKTS"));
            thisFrame.put("WindDir", json.get("windDirDEG"));
            list.add(thisFrame);
        }
        list.sort(new JSONEpochComparator());
        JSONArray output = new JSONArray();
        for (JSONObject j : list) {
            output.append(j);
        }
        System.out.println(output);
        output.save(new File("output/Refactor/Aeris.json"), null);
    }

    static class JSONEpochComparator implements Comparator<JSONObject> {

        @Override
        public int compare(JSONObject a, JSONObject b) {
            return a.getInt("Epoch") - b.getInt("Epoch");
        }
    }
}
