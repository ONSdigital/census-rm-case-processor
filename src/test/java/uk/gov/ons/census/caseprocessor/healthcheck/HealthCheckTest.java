package uk.gov.ons.census.caseprocessor.healthcheck;

import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.caseprocessor.schedule.ClusterLeaderManager;

@ExtendWith(MockitoExtension.class)
public class HealthCheckTest {
  @Mock private ClusterLeaderManager clusterLeaderManager;

  @InjectMocks HealthCheck underTest;

  @Test
  public void testHappyPath() {
    // Given
    ReflectionTestUtils.setField(underTest, "fileName", "/tmp/" + UUID.randomUUID());

    // When
    underTest.updateFileWithCurrentTimestamp();

    // Then
    verify(clusterLeaderManager).leaderKeepAlive();
  }
}
