package uk.gov.ons.census.caseprocessor.schedule;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChunkPoller {
  private final ChunkProcessor chunkProcessor;
  private final ActionRuleProcessor actionRuleProcessor;

  public ChunkPoller(ChunkProcessor chunkProcessor, ActionRuleProcessor actionRuleProcessor) {
    this.chunkProcessor = chunkProcessor;
    this.actionRuleProcessor = actionRuleProcessor;
  }

  @Scheduled(fixedDelayString = "${scheduler.frequency}")
  public void processQueuedCases() {
    do {
      chunkProcessor.processChunk();
      actionRuleProcessor.updateCompletedProcessingActionRules();
    } while (chunkProcessor.isThereWorkToDo()); // Don't go to sleep while there's work to do!
  }

  @Scheduled(fixedDelayString = "${scheduler.frequency}")
  public void processQueuedFulfilments() {
    do {
      chunkProcessor.processFulfilmentChunk();
    } while (chunkProcessor
        .isThereFulfilmentWorkToDo()); // Don't go to sleep while there's work to do!
  }
}
