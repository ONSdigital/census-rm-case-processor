package uk.gov.ons.census.caseprocessor.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ActiveProfiles("test")
@SpringBootTest
@ExtendWith(SpringExtension.class)
public class HealthCheckIT {
  @Value("${healthcheck.filename}")
  private String fileName;

  @Test
  public void testHappyPath() throws IOException, InterruptedException {
    // Hack because test is flakey in Travis
    Thread.sleep(5000);

    Path path = Paths.get(fileName);

    try (BufferedReader bufferedReader = Files.newBufferedReader(path)) {
      String fileLine = bufferedReader.readLine();
      OffsetDateTime healthCheckTimeStamp = OffsetDateTime.parse(fileLine);

      assertThat(OffsetDateTime.now().toEpochSecond() - healthCheckTimeStamp.toEpochSecond())
          .isLessThan(30);
    }
  }
}
