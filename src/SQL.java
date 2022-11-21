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

    static void makeDatabases() throws SQLException {
        // Todo Create list of all models and how far in the future databases should index to
        //  e.g. accu+0h, accu+1h, ... , accu+72h
        //  How far should each go?
        JSONObject json = PApplet.loadJSONObject(new File("SQL cmd.json"));
        Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/models", "root", "1234");
        DatabaseMetaData md = connection.getMetaData();
        ResultSet rs = md.getTables("models", null, "%", null);
        ArrayList<StringBuilder> builders = new ArrayList<>();
        while (rs.next()) {
            String table = rs.getString(3);
            String[] split = table.split("_");
            for (int i = 0; i < 12; i++) {
                StringBuilder builder = new StringBuilder();
                String tableName = split[0] + "+" + i + split[1].charAt(1);
                builder.append(String.format("create table %s (%n", tableName));
                ResultSet columns = md.getColumns(null, null, table, null);
                while (columns.next()) {
                    String metric = columns.getString(4);
                    String command = json.getString(metric);
                    builder.append(command);
                    builder.append("\n");
                }
                builders.add(builder);
            }
            break;
        }
        builders.forEach(System.out::println);
    }
}

