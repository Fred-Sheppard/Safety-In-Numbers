import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class MeanOfAllModels {

    public static void main(String[] args) throws SQLException, IOException {
        Statement weatherModelsSQL = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/weathermodels", "root", "1234").createStatement();
        String[] tables = {
                "accu_1h",
                "aeris_1h",
                "ecmwf_1h",
                "harmonie_1h",
                "openweather_1h",
                "visual_1h"
        };

        LinkedHashMap<Long, ArrayList<Double>> directionsAtEachTime = new LinkedHashMap<>();
        // Get the mean of all the tables at each point
        for (String table : tables) {
            boolean isVisual = table.equals("visual_1h");
            // Visual uses nanosecond time
            long timeConverter = (isVisual) ? 1000 : 1;
            String query = String.format("""
                    SELECT * FROM %s
                    WHERE Epoch > %d
                    AND Epoch < %d;""", table, 1673827200 * timeConverter, 1676678400 * timeConverter);
            ResultSet resultSet = weatherModelsSQL.executeQuery(query);

            while (resultSet.next()) { // For every Value in Table
                long epoch = resultSet.getLong("Epoch");
                double speed = resultSet.getDouble("WindSpeed");
                // Convert millisecond time to seconds
                epoch = (epoch > 1000000000000L) ? epoch / 1000 : epoch;
                // Ignore newly calculated fields in Visual_1h - They came from the average itself!
                if (isVisual && epoch > 1675620000L) {
                    break;
                }

                // If the time or list is null, create a new list
                ArrayList<Double> list = directionsAtEachTime.getOrDefault(epoch, new ArrayList<>());
                // Append this wind speed
                list.add(speed);
                directionsAtEachTime.put(epoch, list);
            }
        }
        PrintWriter output = new PrintWriter(new BufferedWriter(new FileWriter("means.csv")));
        output.println("Time,Speed");
        directionsAtEachTime.forEach((time, list) -> {
            double mean = list.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0);
            output.printf("%s,%.2f\n", Instant.ofEpochSecond(time), mean);
        });
        output.flush();
        output.close();
    }
}
