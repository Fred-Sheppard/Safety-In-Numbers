import processing.core.PApplet;
import processing.data.JSONArray;
import processing.data.JSONObject;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.TreeMap;

class SQL {
    public static void main(String[] args) {
        dbFromFile();
    }

    public static void main1(String[] args) throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/models", "root", "12345678");

        Statement statement = connection.createStatement();
        int returnCode = statement.executeUpdate("""
                insert into test2 (Epoch, WindSpeed, WindDir)
                values
                (1667509200, 10, 311),
                (1667512800, 8, 321),
                (1667516400, 7, 315);""");
        System.out.println(returnCode);

        ResultSet resultSet = statement.executeQuery("SELECT * FROM test2;");

        while (resultSet.next()) {
            System.out.println("WindSpeed: " + resultSet.getFloat("WindSpeed"));
            System.out.println("WindDir: " + resultSet.getInt("WindDir"));
        }
    }

    @SuppressWarnings("unused")
    public static void makeDatabases() throws SQLException {
        JSONObject json = PApplet.loadJSONObject(new File("SQL cmd.json"));
        Connection connectionIn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/models", "root", "1234");
        Connection connectionOut = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/weathermodels", "root", "1234");
        DatabaseMetaData metaData = connectionIn.getMetaData();
        Statement statement = connectionOut.createStatement();
        ResultSet tables = metaData.getTables("models", null, "%", null);
        ArrayList<String> queries = new ArrayList<>();
        while (tables.next()) {
            String table = tables.getString(3);
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("create table %s (%n", table));
            ResultSet columns = metaData.getColumns("models", null, table, null);
            int columnIndex = 0;
            while (columns.next()) {
                columnIndex++; // Starts at 1
                if (columnIndex == 1) {
                    builder.append("Epoch bigint");
                    builder.append("\n");
                    continue;
                } else if (columnIndex == 2) {
                    builder.append("id int primary key");
                    builder.append("\n");
                    builder.append("Offset int,");
                    builder.append("\n");
                }
                String metric = columns.getString(4);
                String command = json.getString(metric);
                builder.append(command);
                builder.append("\n");
            }
            queries.add(builder.toString());
        }
        for (String builder : queries) {
            System.out.println(builder);
            statement.executeUpdate(builder);
        }
    }

    public static void dbFromFile() {
        // Todo currently stores list of TreeMaps
        //  Turn these TreeMaps into queries
        JSONObject json = PApplet.loadJSONObject(new File("SQL cmd.json"));
        JSONObject config = PApplet.loadJSONObject(new File("data/config.json"));
        JSONObject models = config.getJSONObject("Models");
        ArrayList<TreeMap<String, String>> list = new ArrayList<>();
        for (Object o : models.keys()) {
            TreeMap<String, String> treeMap = new TreeMap<>();
            String s = (String) o;
            JSONObject model = models.getJSONObject(s);
            JSONObject keys = model.getJSONObject("Keys");
            for (Object o1 : keys.keys()) {
                String key = (String) o1;
                treeMap.put(key, json.getString(key));
            }
            list.add(treeMap);
        }
        list.forEach(System.out::println);
    }
}

