import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

// Todo make all vs average
// Todo migrate away from Processing.json
public class VarianceVsGroundTruth {

    public static void main(String[] args) throws SQLException, IOException {
        Statement weatherModelsSQL = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/WeatherModels", "root", "1234").createStatement();
        Statement variance = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/variance", "root", "1234").createStatement();
        String[] tables = {"Accu_1h", "Aeris_1h", "ECMWF_1h", "Harmonie_1h", "OpenWeather_1h", "Visual_1h" };

        ArrayList<String> list = new ArrayList<>(Files.readAllLines(Paths.get("data/means.csv")));
        HashMap<Instant, Double> map = new HashMap<>();
        // Skip headers
        for (int i = 1; i < list.size(); i++) {
            String row = list.get(i);
            String[] entries = row.split(",");
            map.put(Instant.parse(entries[0]), Double.valueOf(entries[1]));
        }

        for (String table : tables) {
            // Reset the table
            variance.executeUpdate("drop table " + table);
            variance.executeUpdate(String.format("""
                    create table %s
                    (
                        ID       bigint primary key auto_increment,
                        Epoch    datetime,
                        Variance float null
                    );""", table));
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("insert into %s (Epoch, Variance)\nValues\n", table));
            long multiplier = switch (table) {
                case "Visual_1h" -> 1000L;
                default -> 1L;
            };
            int offset = 0;
            if (table.equals("ECMWF_1h")) offset = 98;
            // Loop through offsets
            while (true) {
                String query = String.format("""
                        SELECT * FROM %s
                        WHERE Epoch > %d
                        AND Epoch < %d
                        AND `Offset` = %d;""", table, 1673827200L * multiplier, 1676678400L * multiplier, offset);
                ResultSet resultSet;
                // If offset goes out of bounds
                try {
                    resultSet = weatherModelsSQL.executeQuery(query);
                } catch (SQLException e) {
                    break;
                }
                while (resultSet.next()) {
                    long time = resultSet.getLong("Epoch");
                    double speed = resultSet.getDouble("WindSpeed");
                    Instant instant = switch (table) {
                        case "Visual_1h" -> Instant.ofEpochMilli(time);
                        default -> Instant.ofEpochSecond(time);
                    };

                    double diff = map.get(instant) - speed;
                    String tuple = String.format("'%s', %.2f,\n", instant, diff);
                    builder.append(tuple.replace("T", " ").replace("Z", ""));
                }
                offset++;
            }
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);
            builder.append(";");
            variance.executeUpdate(builder.toString());
        }
    }
}


