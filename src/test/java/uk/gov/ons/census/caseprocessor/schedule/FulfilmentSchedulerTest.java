package uk.gov.ons.census.caseprocessor.schedule;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentNextTriggerRepository;
import uk.gov.ons.census.common.model.entity.FulfilmentNextTrigger;

@ExtendWith(MockitoExtension.class)
public class FulfilmentSchedulerTest {
  @Mock private ClusterLeaderManager clusterLeaderManager;
  @Mock private FulfilmentNextTriggerRepository fulfilmentNextTriggerRepository;
  @Mock private FulfilmentProcessor fulfilmentProcessor;

  @InjectMocks private FulfilmentScheduler underTest;

  @Test
  public void testScheduleFulfilments() {
    // Given
    FulfilmentNextTrigger fulfilmentNextTrigger = new FulfilmentNextTrigger();
    OffsetDateTime dateTimeNow = OffsetDateTime.now();
    fulfilmentNextTrigger.setTriggerDateTime(dateTimeNow);

    when(clusterLeaderManager.isThisHostClusterLeader()).thenReturn(true);
    when(fulfilmentNextTriggerRepository.findByTriggerDateTimeBefore(any(OffsetDateTime.class)))
        .thenReturn(Optional.of(fulfilmentNextTrigger));

    // When
    underTest.scheduleFulfilments();

    // Then
    verify(fulfilmentProcessor).addFulfilmentBatchIdAndQuantity();
    verify(fulfilmentNextTriggerRepository).saveAndFlush(fulfilmentNextTrigger);

    assertThat(fulfilmentNextTrigger.getTriggerDateTime()).isEqualTo(dateTimeNow.plusDays(1));
  }

  @Test
  public void testScheduleFulfilmentsNothingToDo() {
    // Given
    FulfilmentNextTrigger fulfilmentNextTrigger = new FulfilmentNextTrigger();
    OffsetDateTime dateTimeNow = OffsetDateTime.now();
    fulfilmentNextTrigger.setTriggerDateTime(dateTimeNow);

    when(clusterLeaderManager.isThisHostClusterLeader()).thenReturn(true);
    when(fulfilmentNextTriggerRepository.findByTriggerDateTimeBefore(any(OffsetDateTime.class)))
        .thenReturn(Optional.empty());

    // When
    underTest.scheduleFulfilments();

    // Then
    verifyNoInteractions(fulfilmentProcessor);
    verify(fulfilmentNextTriggerRepository).findByTriggerDateTimeBefore(any(OffsetDateTime.class));
    verifyNoMoreInteractions(fulfilmentNextTriggerRepository);
  }
}
