package uk.gov.ons.census.caseprocessor.healthcheck;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.caseprocessor.schedule.ClusterLeaderManager;

@Component
public class HealthCheck {
  private final ClusterLeaderManager clusterLeaderManager;

  @Value("${healthcheck.filename}")
  private String fileName;

  public HealthCheck(ClusterLeaderManager clusterLeaderManager) {
    this.clusterLeaderManager = clusterLeaderManager;
  }

  @Scheduled(fixedDelayString = "${healthcheck.frequency}")
  public void updateFileWithCurrentTimestamp() {
    clusterLeaderManager.leaderKeepAlive();

    Path path = Paths.get(fileName);
    OffsetDateTime now = OffsetDateTime.now();

    try (BufferedWriter writer = Files.newBufferedWriter(path)) {
      writer.write(now.toString());
    } catch (IOException e) {
      // Ignored
    }
  }
}
