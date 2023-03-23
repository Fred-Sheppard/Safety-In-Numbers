import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

// Todo collapse boat data into 3h and 6h data
public class HourlyDataFromBoat {

    public static void main(String[] args) throws SQLException {
        Statement aliBabaStatement = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/alibaba", "root", "1234").createStatement();
        ResultSet boatResults = aliBabaStatement.executeQuery("SELECT * FROM data");
        while (boatResults.next()) {
            long time = boatResults.getLong("Epoch");
            Instant instant = Instant.ofEpochSecond(time);
            double windSpeed = boatResults.getDouble("WindSpeed");
            int windDir = boatResults.getInt("WindDir");

        }
    }
}
