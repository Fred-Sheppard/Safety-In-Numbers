import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;

public class UploadToSQL {
    public static void main(String[] args) {

        try (Statement stmt = DriverManager.getConnection(
                "jdbc:mariadb://localhost:3307/alibaba", "root", "1234").createStatement()) {

            // Read the CSV file and store its contents in a HashMap
            HashMap<String, String> directionMap = new HashMap<>();
            BufferedReader csvReader = new BufferedReader(new FileReader("data/Direction_N.csv"));
            String row;
            csvReader.readLine();
            while ((row = csvReader.readLine()) != null) {
                String[] data = row.split(",");
                directionMap.put(data[0].replace("Z", ":00"), data[1]);
            }
            csvReader.close();

            // Update the SQL table with the new directions
            ResultSet rs = stmt.executeQuery("SELECT * FROM boatdata");
            while (rs.next()) {
                String time = rs.getString("Time").replace(" ", "T");
                String newDirection = directionMap.get(time);
                if (newDirection != null) {
                    stmt.executeUpdate("UPDATE boatdata SET Direction='" + newDirection + "' WHERE Time='" + time + "'");
                }
            }

            System.out.println("Directions updated successfully!");
        } catch (Exception e) {
            System.err.println("Error updating directions: " + e.getMessage());
        }
    }
}
