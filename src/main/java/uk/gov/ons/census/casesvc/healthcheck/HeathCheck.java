package uk.gov.ons.census.casesvc.healthcheck;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

@Component
public class HeathCheck {
  @Value("${healthcheck.filename}")
  private String fileName;

  @Scheduled(fixedDelayString = "${healthcheck.frequency}")
  public void updateFileWithCurrentTimestamp() {
    Path path = Paths.get(fileName);
    LocalDateTime now = LocalDateTime.now();

    try (BufferedWriter writer = Files.newBufferedWriter(path)) {
      writer.write(now.toString());
    } catch (IOException e) {
      // Ignored
    }
  }
}
