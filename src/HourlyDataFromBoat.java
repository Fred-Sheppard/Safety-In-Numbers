import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

// Todo collapse boat data into 3h and 6h data
public class HourlyDataFromBoat {

    public static void main(String[] args) throws SQLException {
        Statement aliBabaStatement = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/alibaba", "root", "1234").createStatement();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime startTime = LocalDateTime.parse("2023-01-15T12:00:00");
        aliBabaStatement.executeUpdate("drop table `3hourly`;");
        aliBabaStatement.executeUpdate("""
                create table 3hourly
                (
                    ID int auto_increment primary key,
                    `Time` DATETIME,
                    Speed float,
                    Direction float
                );""");
        while (true) {
            LocalDateTime endTime = startTime.plus(3, ChronoUnit.HOURS);
            ResultSet boatResults = aliBabaStatement.executeQuery(String.format("""
                    SELECT * FROM boatdata
                    WHERE Time >= '%s' AND Time < '%s'""", formatter.format(startTime), formatter.format(endTime)));
            if (!boatResults.next()) break;
            double speedTotal = 0;
            double dirTotal = 0;
            int counter = 0;
            do {
                double windSpeed = boatResults.getDouble("Speed");
                speedTotal += windSpeed;
                int windDir = boatResults.getInt("Direction");
                dirTotal += windDir;
                counter++;
            } while (boatResults.next());
            double avgSpeed = speedTotal / counter;
            double avgDir = dirTotal / counter;
            aliBabaStatement.executeUpdate(String.format("""
                    INSERT INTO `3hourly` (`Time`, Speed, Direction)
                    VALUE
                    ('%s', %f, %f)""", formatter.format(startTime), avgSpeed, avgDir));
            startTime = endTime;
        }
    }
}
