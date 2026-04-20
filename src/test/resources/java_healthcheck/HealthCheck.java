import java.net.*;

public class HealthCheck {
  // A simple Java class for testing a GET endpoint responds with status code 200 inside Java containers
  // Usage: java <PATH_TO_THIS_CLASS>/HealthCheck.java <HEALTH_URL>

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Expected exactly one argument, the URL to GET");
    }

    // Prepare the HTTP GET request
    URL url = new URL(args[0]);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");

    // Make the request
    int status = con.getResponseCode();

    // Exit with a failure code if the GET is not a success
    if (status != 200) {
      System.exit(1);
    }
  }
}
