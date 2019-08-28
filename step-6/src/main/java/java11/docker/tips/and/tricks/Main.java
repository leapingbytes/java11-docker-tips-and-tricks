/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package java11.docker.tips.and.tricks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

public class Main {
    public static void main(String[] args) throws IOException {
        String urlString = "https://github.com/";

        // create the url
        URL url = new URL(urlString);

        BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

        // write the output to stdout
        String line;
        while ((line = reader.readLine()) != null)
        {
            if (line.indexOf("title") > 0) {
                System.out.println( line );
                break;
            }
        }

        // close our reader
        reader.close();

        System.out.println("Hello Java 11 - step 6");
    }
}
