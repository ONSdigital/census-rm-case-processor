package uk.gov.ons.census.casesvc.healthcheck;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@ActiveProfiles("test")
@SpringBootTest
@RunWith(SpringJUnit4ClassRunner.class)
public class HeathCheckIT {
  @Value("${healthcheck.filename}")
  private String fileName;

  @Test
  public void testHappyPath() throws IOException {
    Path path = Paths.get(fileName);

    try (BufferedReader bufferedReader = Files.newBufferedReader(path)) {
      String fileLine = bufferedReader.readLine();
      String now = LocalDateTime.now().toString();

      assertEquals(now.substring(0, 16), fileLine.substring(0, 16));
    }
  }
}
