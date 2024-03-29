import processing.core.PApplet;
import processing.data.JSONObject;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;

class SQL {
    public static void main(String[] args) throws SQLException {
        dbFromFile();
    }

    public static void dbFromFile() throws SQLException {
        JSONObject json = PApplet.loadJSONObject(new File("OtherFiles/SQL cmd.json"));
        Connection connectionIn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/models", "root", "1234");
        Connection connectionOut = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/weathermodels", "root", "1234");
        DatabaseMetaData metaData = connectionIn.getMetaData();
        ResultSet tables = metaData.getTables("models", null, "%", null);
        Statement statement = connectionOut.createStatement();

        while (tables.next()) {
            String table = tables.getString(3);
            ArrayList<String> columnList = new ArrayList<>();
            ResultSet columns = metaData.getColumns("models", null, table, null);
            while (columns.next()) {
                columnList.add(columns.getString(4));
            }
            columnList.add("ID");
            columnList.add("Offset");
            columnList.add("RequestTime");
            Collections.sort(columnList);
            String temp = columnList.get(1);
            columnList.set(1, columnList.get(0));
            columnList.set(0, temp);
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("create table %s  (%n", table));
            for (String s : columnList) {
                String cmd = json.getString(s);
                builder.append(cmd);
                builder.append("\n");
            }
            System.out.println(builder);
            statement.executeUpdate(builder.toString());
        }
    }
}

