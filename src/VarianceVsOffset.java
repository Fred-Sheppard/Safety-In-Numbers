import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class VarianceVsOffset {

    public static void main(String[] args) throws SQLException {
        Statement weatherModelsSQL = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/WeatherModels", "root", "1234").createStatement();
        Statement aliBabaStatement = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/alibaba", "root", "1234").createStatement();
        Statement offsetStatement = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/DirectionVsOffset", "root", "1234").createStatement();
        String[] tables = {"Accu_1h", "Aeris_1h", "ECMWF_1h", "Harmonie_1h", "OpenWeather_1h", "Visual_1h"};
//        String[] tables = {"ECMWF_6h"};
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

        for (String table : tables) {

            // Reset the table
            try {
                offsetStatement.executeUpdate("drop table " + table);
            } catch (SQLException ignored) {
            }
            offsetStatement.executeUpdate(String.format("""
                    create table %s
                    (
                        ID       bigint primary key auto_increment,
                        Offset_ int,
                        Variance float
                    );""", table));


            StringBuilder builder = new StringBuilder();
            builder.append(String.format("insert into %s (Offset_,Variance)%nVALUES%n", table));
            long multiplier = switch (table) {
                case "Visual_1h" -> 1000L;
                default -> 1L;
            };
            int offset = switch (table) {
                case "ECMWF_1h" -> 90;
                case "ECMWF_3h" -> 130;
                case "ECMWF_6h" -> 152;
                default -> 0;
            };
            boolean lastResultSetWasEmpty = false;
            // Loop through offsets
            while (true) {
                double total = 0;
                System.out.print("\r" + offset);
                String query = String.format("""
                        SELECT Epoch, WindDir FROM %s
                        WHERE Epoch > %d
                        AND Epoch < %d
                        AND Offset_ = %d;""", table, 1673827200L * multiplier, 1679767201L * multiplier, offset);
                ResultSet forecastResults;
                // If offset goes out of bounds
                try {
                    forecastResults = weatherModelsSQL.executeQuery(query);
                } catch (SQLException e) {
                    break;
                }
                // Check for offset too big
                if (!forecastResults.next()) {
                    if (!lastResultSetWasEmpty) {
                        lastResultSetWasEmpty = true;
                        offset++;
                        continue;
                    } else {
                        break;
                    }
                }
                lastResultSetWasEmpty = false;
                int count = 0;
                do {
                    count++;
                    long time = forecastResults.getLong("Epoch");
                    double predictedDirection = forecastResults.getDouble("WindDir");
                    Instant instant = switch (table) {
                        case "Visual_1h" -> Instant.ofEpochMilli(time);
                        default -> Instant.ofEpochSecond(time);
                    };

                    ResultSet aliBabaResultSet = aliBabaStatement.executeQuery(String.format(
                            "SELECT Direction FROM boatdata WHERE Time = '%s'", formatter.format(instant)));

                    if (!aliBabaResultSet.next()) continue;
                    double boatDirection = aliBabaResultSet.getDouble(1);
//                    double diff = predictedSpeed - boatSpeed;
//                    double abs = Math.abs(diff);
                    double abs = windDiff(boatDirection, predictedDirection);
                    total += abs;
                } while (forecastResults.next());
                double mean = total / count;
                builder.append(String.format("(%d, %.2f),%n", offset, mean));
                offset++;
            }
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);
            builder.append(";");
            System.out.println(builder);
            offsetStatement.executeUpdate(builder.toString());
        }
    }

    private static double windDiff(double a, double b) {
        double abs = Math.abs(a - b);
        return (abs > 180) ? 360 - abs : abs;
    }
}


