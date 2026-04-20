package uk.gov.ons.census.caseprocessor.schedule;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MessageToSendPoller {
  private final MessageToSendProcessor messageToSendProcessor;

  public MessageToSendPoller(MessageToSendProcessor messageToSendProcessor) {
    this.messageToSendProcessor = messageToSendProcessor;
  }

  @Scheduled(fixedDelayString = "${scheduler.frequency}")
  public void processQueuedMessages() {
    do {
      messageToSendProcessor.processChunk();
    } while (messageToSendProcessor.isThereWorkToDo()); // No sleep while there's work to do!
  }
}
