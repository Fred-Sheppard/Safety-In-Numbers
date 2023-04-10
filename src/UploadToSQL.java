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
            HashMap<String, Integer> directionMap = new HashMap<>();
            BufferedReader csvReader = new BufferedReader(new FileReader("data/Direction_N.csv"));
            String row;
            csvReader.readLine();
            while ((row = csvReader.readLine()) != null) {
                String[] data = row.split(",");
                String time = data[0].replace("Z", ":00");
                int direction = Integer.parseInt(data[1]);
                if (direction < 0) {
                    direction = Math.abs(direction) + 180;
                }
                directionMap.put(time, direction);
            }
            csvReader.close();

            // Update the SQL table with the new directions
            ResultSet rs = stmt.executeQuery("SELECT * FROM boatdata");
            while (rs.next()) {
                String time = rs.getString("Time").replace(" ", "T");
                int newDirection = directionMap.get(time);
                stmt.executeUpdate("UPDATE boatdata SET Direction='" + newDirection + "' WHERE Time='" + time + "'");
//                System.out.println("UPDATE boatdata SET Direction='" + newDirection + "' WHERE Time='" + time + "'");
            }

            System.out.println("Directions updated successfully!");
        } catch (Exception e) {
            System.err.println("Error updating directions: " + e.getMessage());
        }
    }
}
