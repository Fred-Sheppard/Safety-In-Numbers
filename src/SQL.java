import java.sql.*;

class SQL {

    public static void main(String[] args) throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/models", "root", "12345678");

        Statement statement = connection.createStatement();
        int returnCode = statement.executeUpdate("""
                    insert into test2 (Epoch, WindSpeed, WindDir)
                    values
                    (1667509200, 10, 311),
                    (1667512800, 8, 321),
                    (1667516400, 7, 315);""");
        System.out.println(returnCode);

        ResultSet resultSet = statement.executeQuery("SELECT * FROM test2;");

        while (resultSet.next()) {
            System.out.println("WindSpeed: " + resultSet.getFloat("WindSpeed"));
            System.out.println("WindDir: " + resultSet.getInt("WindDir"));
        }
    }
}
