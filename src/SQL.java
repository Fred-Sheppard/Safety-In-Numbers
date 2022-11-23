import processing.core.PApplet;
import processing.data.JSONObject;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;

class SQL {

    public static void main(String[] args) throws SQLException {
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
        ArrayList<String> builders = new ArrayList<>();
        while (tables.next()) {
            String table = tables.getString(3);
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("create table %s (%n", table));
            ResultSet columns = metaData.getColumns("models", null, table, null);
            int columnIndex = 0;
            while (columns.next()) {
                columnIndex++; // Starts at 1
                if (columnIndex == 2) {
                    builder.append("Offset int,");
                    builder.append("\n");
                }
                String metric = columns.getString(4);
                String command = json.getString(metric);
                builder.append(command);
                builder.append("\n");
            }
            builders.add(builder.toString());
        }
        for (String builder : builders) {
            System.out.println(builder);
            statement.executeUpdate(builder);
        }
    }
}

