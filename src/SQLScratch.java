import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

public class SQLScratch {

    public static void main(String[] args) throws SQLException {
        Statement weatherModelsSQL = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/weathermodels", "root", "1234").createStatement();
        Statement aliBabaSQL = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/alibaba", "root", "1234").createStatement();
        String[] tables = {
                "accu_1h",
                "aeris_1h",
                "ecmwf_1h",
                "harmonie_1h",
                "openweather_1h",
                "visual_1h"
        };
        HashMap<Long, Double> aliBabaWind = new HashMap<>();
        ResultSet boatResults = aliBabaSQL.executeQuery("SELECT * FROM data WHERE Epoch > 1673827200;");
        while (boatResults.next()) {
            aliBabaWind.put(
                    boatResults.getLong("Epoch"),
                    boatResults.getDouble("WindSpeed"));
        }

        for (String table : tables) {
            double diff = 0;
            int counter = 0;
            long timeConverter = (table.equals("visual_1h")) ? 1000 : 1;
            String query = String.format("""
                    SELECT * FROM %s
                    WHERE Epoch > %d
                    AND Epoch < %d;""", table, 1673827200 * timeConverter, 1676678400 * timeConverter);
            ResultSet resultSet = weatherModelsSQL.executeQuery(query);
            while (resultSet.next()) {
                long epoch = resultSet.getLong("Epoch");
                double speed = resultSet.getDouble("WindSpeed");
                // Get the difference between the forecast and the ground truth at the given instant
                double aliBabaSpeed = aliBabaWind.getOrDefault(epoch / timeConverter, 0.0);
                if (aliBabaSpeed == 0.0) {
                    System.out.println(epoch);
                    continue;
                }
                diff += Math.abs(-speed);
                counter++;
            }
            System.out.println(table + ": " + diff / counter);
        }
    }
}
