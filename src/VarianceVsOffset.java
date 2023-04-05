import java.sql.*;
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
                "jdbc:mariadb://localhost:3307/speedvspressure", "root", "1234").createStatement();
        String[] tables = {"Aeris_1h", "ECMWF_1h", "Harmonie_1h", "OpenWeather_1h", "Visual_1h"};
        // NO ACCUWEATHER
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
                        Pressure int,
                        Variance float
                    );""", table));


            StringBuilder builder = new StringBuilder();
            builder.append(String.format("insert into %s (Pressure,Variance)%nVALUES%n", table));
            long multiplier = switch (table) {
                case "Visual_1h" -> 1000L;
                default -> 1L;
            };
            int offset = switch (table) {
                case "ECMWF_1h" -> 98;
                case "ECMWF_3h" -> 130;
                case "ECMWF_6h" -> 152;
                default -> 0;
            };
            int pressure = 970;
            boolean lastResultSetWasEmpty = false;
            // Loop through offsets
            while (true) {
                double total = 0;
                double x = 0;
                double y = 0;
                System.out.print("\r" + table + ": " + pressure);
                String query = String.format("""
                                SELECT Epoch, WindSpeed FROM %s
                                WHERE Epoch > %d
                                AND Epoch < %d
                                AND Pressure between %d and %d
                                AND Offset_ = %d;""", table, 1673827200L * multiplier, 1679767201L * multiplier,
                        pressure, pressure + 5, offset);
                ResultSet forecastResults;
                // If offset goes out of bounds
                try {
                    forecastResults = weatherModelsSQL.executeQuery(query);
                } catch (SQLSyntaxErrorException se) {
                    System.out.println(query);
                    throw se;
                } catch (SQLException e) {
                    break;
                }
                // Check for offset too big
                if (!forecastResults.next()) {
                    if (!lastResultSetWasEmpty) {
                        lastResultSetWasEmpty = true;
                        pressure += 10;
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
                    total += abs;
                    count++;

//                    double abs = windDiff(boatDirection, predictedDirection);
//                    x += Math.cos(Math.toRadians(abs));
//                    y += Math.sin(Math.toRadians(abs));
                } while (forecastResults.next());
//                double mean = Math.toDegrees(Math.atan2(y, x));
                double mean = total / count;
                builder.append(String.format("(%d, %.2f),%n", pressure, mean));
                pressure += 5;
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


