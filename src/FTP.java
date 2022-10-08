//import java.io.File;
//import java.io.IOException;
//import java.io.InputStream;
//import java.net.URL;
//import java.net.URLConnection;
//import java.nio.file.Files;
//
//public class FTP {
//
//    public static void main(String[] args) throws IOException {
//        String myUrl = "ftp://ftpprd.ncep.noaa.gov/pub/data/nccf/com/rap/prod";
//
//        URLConnection urlConnection = new URL(myUrl).openConnection();
//        InputStream inputStream = urlConnection.getInputStream();
//        Files.copy(inputStream, new File("output/ftp.txt").toPath());
//        inputStream.close();
//    }
//
//}
