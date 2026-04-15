package uk.gov.ons.census.caseprocessor.schedule;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.model.repository.MessageToSendRepository;
import uk.gov.ons.ssdc.common.model.entity.MessageToSend;

@Component
public class MessageToSendProcessor {
  private static final Logger log = LoggerFactory.getLogger(MessageToSendProcessor.class);
  private final MessageToSendRepository messageToSendRepository;
  private final MessageToSendSender messageToSendSender;

  @Value("${scheduler.chunksize}")
  private int chunkSize;

  public MessageToSendProcessor(
      MessageToSendRepository messageToSendRepository, MessageToSendSender messageToSendSender) {
    this.messageToSendRepository = messageToSendRepository;
    this.messageToSendSender = messageToSendSender;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW) // Start a new transaction for every chunk
  public void processChunk() {
    try (Stream<MessageToSend> messagesToSend =
        messageToSendRepository.findMessagesToSend(chunkSize)) {
      List<MessageToSend> messagesToSendSent = new LinkedList<>();

      messagesToSend.forEach(
          messageToSend -> {
            try {
              messageToSendSender.sendMessage(messageToSend);
              messagesToSendSent.add(messageToSend);
            } catch (Exception exception) {
              log.atInfo()
                  .setMessage("Could not send message. Will retry indefinitely")
                  .addArgument(messageToSend)
                  .log();
            }
          });

      messageToSendRepository.deleteAllInBatch(messagesToSendSent);
    }
  }

  @Transactional
  public boolean isThereWorkToDo() {
    return messageToSendRepository.count() > 0;
  }
}
