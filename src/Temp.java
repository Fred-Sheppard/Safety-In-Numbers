import processing.core.*;
import processing.data.JSONObject;

class Temp extends PApplet {


    public void settings() {
        size(50, 50);
    }

    public void setup() {
        JSONObject json = new JSONObject();
        json.setString("lat", "52.930166");
        json.setString("long", "-8.282975");
        saveJSONObject(json, "Vars.json");
        System.exit(0);
    }

    public void draw() {

    }

    public void mousePressed() {

    }

    public void keyPressed() {

    }

    public static void main(String[] args) {
        String[] processingArgs = {"Temp"};
        PApplet.runSketch(processingArgs, new Temp());
    }
}