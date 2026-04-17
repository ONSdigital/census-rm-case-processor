package uk.gov.ons.census.caseprocessor.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.ons.census.caseprocessor.model.repository.CaseToProcessRepository;
import uk.gov.ons.census.caseprocessor.service.CaseToProcessProcessor;
import uk.gov.ons.census.common.model.entity.CaseToProcess;

@ExtendWith(MockitoExtension.class)
public class ChunkProcessorTest {
  @Mock private CaseToProcessRepository caseToProcessRepository;
  @Mock private CaseToProcessProcessor caseToProcessProcessor;

  @InjectMocks private ChunkProcessor underTest;

  @Value("${scheduler.chunksize}")
  private int chunkSize;

  @Test
  public void testProcessChunk() {
    // Given
    CaseToProcess caseToProcess = new CaseToProcess();
    List<CaseToProcess> caseToProcessList = new LinkedList<>();
    caseToProcessList.add(caseToProcess);
    when(caseToProcessRepository.findChunkToProcess(anyInt()))
        .thenReturn(caseToProcessList.stream());

    // When
    underTest.processChunk();

    // Then
    verify(caseToProcessRepository).findChunkToProcess(eq(chunkSize));
    verify(caseToProcessProcessor).process(eq(caseToProcess));
    verify(caseToProcessRepository).deleteAllInBatch(eq(List.of(caseToProcess)));
  }

  @Test
  public void testIsThereWorkToDoNoThereIsNot() {
    // Given
    when(caseToProcessRepository.count()).thenReturn(0L);

    // When
    boolean actualResult = underTest.isThereWorkToDo();

    // Then
    verify(caseToProcessRepository).count();
    assertThat(actualResult).isFalse();
  }

  @Test
  public void testIsThereWorkToDoYesThereIs() {
    // Given
    when(caseToProcessRepository.count()).thenReturn(666L);

    // When
    boolean actualResult = underTest.isThereWorkToDo();

    // Then
    verify(caseToProcessRepository).count();
    assertThat(actualResult).isTrue();
  }
}
