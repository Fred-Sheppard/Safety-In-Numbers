import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;

// Todo fix average to use offset = 0
// Todo make all vs average
public class HarmonieVsMean {

    public static void main(String[] args) throws SQLException, IOException {
        Statement weatherModelsSQL = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3306/WeatherModels", "root", "1234").createStatement();

        String query = String.format("""
                SELECT * FROM Harmonie_1h
                WHERE Epoch > %d
                AND Epoch < %d;""", 1673827200, 1676678400);

        ArrayList<String> list = new ArrayList<>(Files.readAllLines(Paths.get("means.csv")));
        HashMap<Instant, Double> map = new HashMap<>();
        // Skip headers
        for (int i = 1; i < list.size(); i++) {
            String row = list.get(i);
            String[] entries = row.split(",");
            map.put(Instant.parse(entries[0]), Double.valueOf(entries[1]));
        }

        PrintWriter output = new PrintWriter(new BufferedWriter(new FileWriter("diff.csv")));

        ResultSet resultSet = weatherModelsSQL.executeQuery(query);
        while (resultSet.next()) {
            long time = resultSet.getLong("Epoch");
            double speed = resultSet.getDouble("WindSpeed");
            Instant instant = Instant.ofEpochSecond(time);

            double diff = map.get(instant) - speed;
            output.printf("%s,%.2f\n", instant, diff);
        }

        output.flush();
        output.close();
    }
}


