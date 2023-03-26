import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class SQLFromCSV {

    public static void main(String[] args) throws IOException, SQLException {
        BufferedReader speedReader = new BufferedReader(new FileReader("C:/Personal/Code/WeatherAPI/OtherFiles/Speed.txt"));
        speedReader.readLine();
        BufferedReader directionReader = new BufferedReader(new FileReader("C:/Personal/Code/WeatherAPI/OtherFiles/Direction.txt"));
        directionReader.readLine();
        Statement statement = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/alibaba", "root", "1234").createStatement();
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        StringBuilder builder = new StringBuilder();
        builder.append("""
                INSERT INTO BoatData (Time,Speed,Direction)
                VALUES
                """);
        String speedRow, directionRow;
        while ((speedRow = speedReader.readLine()) != null && (directionRow = directionReader.readLine()) != null) {
            String[] speedRowSplit = speedRow.split(",");
            String[] dirRowSplit = directionRow.split(",");
            OffsetDateTime time1 = OffsetDateTime.parse(speedRowSplit[0]);
            OffsetDateTime time2 = OffsetDateTime.parse(dirRowSplit[0]);
            if (!time1.equals(time2)) continue;
            String formattedTime = formatter.format(time1);
            double speed = Double.parseDouble(speedRowSplit[1]);
            double direction = Double.parseDouble(dirRowSplit[1]);
            builder.append(String.format("('%s', %s, %s),%n", formattedTime, speed, direction));
        }
        int len = builder.length();
        builder.delete(len - 3, len);
        builder.append(";");
//        System.out.println(builder);
        statement.executeUpdate(builder.toString());
    }
}
