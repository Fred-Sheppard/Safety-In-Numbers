import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Class to compare the forecasted data for each model against the ground truth from the boat.
 * Boat data comes from mysql/alibaba/data
 * Forecast data comes from mysql/weathermodels/[models]
 * Data is outputted to mysql/error/[models]
 */
public class VarianceVsGroundTruth {

    public static void main(String[] args) throws SQLException {
        Statement weatherModelsSQL = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/WeatherModels", "root", "1234").createStatement();
        Statement aliBabaStatement = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/alibaba", "root", "1234").createStatement();
        Statement errorStatement = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/error", "root", "1234").createStatement();
        String[] tables = {"Accu_1h", "Aeris_1h", "ECMWF_1h", "Harmonie_1h", "OpenWeather_1h", "Visual_1h"};
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        // Loop through tables
        for (String table : tables) {

//             Reset table
            errorStatement.executeUpdate("DROP TABLE " + table);
            errorStatement.executeUpdate(String.format("""
                    create table %s
                    (
                        ID       bigint primary key auto_increment,
                        Epoch    datetime,
                        Error    float null,
                        Absolute float null
                    );""", table));

            StringBuilder builder = new StringBuilder();
            builder.append(String.format("insert into %s (Epoch, Error, Absolute)\nValues\n", table));
            long multiplier = switch (table) {
                case "Visual_1h" -> 1000L;
                default -> 1L;
            };
            int offset = switch (table) {
                case "ECMWF_1h" -> 98;
                default -> 0;
            };
            String query = String.format("""
                    SELECT * FROM %s
                    WHERE Epoch > %d
                    AND Epoch < %d
                    AND Offset_ = %d;""", table, 1673827200L * multiplier, 1679767200L * multiplier, offset);
            ResultSet forecastResults = weatherModelsSQL.executeQuery(query);
            while (forecastResults.next()) {
                long time = forecastResults.getLong("Epoch");
                double predictedSpeed = forecastResults.getDouble("WindSpeed");
                Instant instant = switch (table) {
                    case "Visual_1h" -> Instant.ofEpochMilli(time);
                    default -> Instant.ofEpochSecond(time);
                };

                ResultSet aliBabaResultSet = aliBabaStatement.executeQuery(String.format(
                        "SELECT Speed FROM boatdata WHERE Time = '%s'", formatter.format(instant)));

                if (!aliBabaResultSet.next()) continue;
                double boatSpeed = aliBabaResultSet.getDouble(1);
                double diff = predictedSpeed - boatSpeed;
                double abs = Math.abs(diff);
                String tuple = String.format("('%s', %.2f, %.2f),\n", instant, diff, abs);
                builder.append(tuple.replace("T", " ").replace("Z", ""));
            }
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);
            builder.append(";");
//            System.out.println(builder);
            errorStatement.executeUpdate(builder.toString());
        }
    }
}


