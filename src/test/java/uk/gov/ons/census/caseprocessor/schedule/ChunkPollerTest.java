package uk.gov.ons.census.caseprocessor.schedule;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class ChunkPollerTest {

  @Test
  void testProcessQueuedCases() {
    // Given
    ChunkProcessor chunkProcessor = mock(ChunkProcessor.class);
    ActionRuleProcessor actionRuleProcessor = mock(ActionRuleProcessor.class);
    ChunkPoller underTest = new ChunkPoller(chunkProcessor, actionRuleProcessor);

    // When
    underTest.processQueuedCases();

    // Then
    verify(chunkProcessor).processChunk();
    verify(chunkProcessor).isThereWorkToDo();
  }
}
