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
        Statement offsetStatement = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/offsets", "root", "1234").createStatement();
        String[] tables = {"Accu_1h", "Aeris_1h", "ECMWF_1h", "Harmonie_1h", "OpenWeather_1h", "Visual_1h"};

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
            offsetStatement.executeUpdate("drop table " + table);
            offsetStatement.executeUpdate(String.format("""
                    create table %s
                    (
                        ID       bigint primary key auto_increment,
                        `Offset` int,
                        Epoch    datetime,
                        Variance float null,
                        Absolute float null
                    );""", table));

            double total = 0;

            StringBuilder builder = new StringBuilder();
            builder.append(String.format("insert into %s (Epoch, `Offset`, Variance, Absolute)\nValues\n", table));
            long multiplier = switch (table) {
                case "Visual_1h" -> 1000L;
                default -> 1L;
            };
            int offset = switch (table) {
                case "ECMWF_1h" -> 98;
                default -> 0;
            };
            // Loop through offsets
            while (true) {
                System.out.print("\r" + offset);
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
                // Check for offset too big
                if (!resultSet.next()) break;
                do {
                    long time = resultSet.getLong("Epoch");
                    double speed = resultSet.getDouble("WindSpeed");
                    Instant instant = switch (table) {
                        case "Visual_1h" -> Instant.ofEpochMilli(time);
                        default -> Instant.ofEpochSecond(time);
                    };

                    double diff = map.get(instant) - speed;
                    double abs = Math.abs(diff);
                    String tuple = String.format("('%s', %d, %.2f, %.2f),%n", instant, offset, diff, abs);
                    builder.append(tuple.replace("T", " ").replace("Z", ""));
                }
                while (resultSet.next());
                offset++;
            }
            builder.deleteCharAt(builder.length()-1);
            builder.deleteCharAt(builder.length()-1);
            builder.deleteCharAt(builder.length()-1);
            builder.append(";");
            offsetStatement.executeUpdate(builder.toString());
//            System.out.println(builder);
        }
    }
}


