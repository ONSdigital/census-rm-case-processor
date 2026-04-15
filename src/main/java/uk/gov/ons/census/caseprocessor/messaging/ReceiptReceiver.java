package uk.gov.ons.census.caseprocessor.messaging;

import static uk.gov.ons.census.caseprocessor.utils.JsonHelper.convertJsonBytesToEvent;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.service.UacService;
import uk.gov.ons.ssdc.common.model.entity.EventType;
import uk.gov.ons.ssdc.common.model.entity.UacQidLink;

@MessageEndpoint
public class ReceiptReceiver {
  private final UacService uacService;
  private final EventLogger eventLogger;

  public ReceiptReceiver(UacService uacService, EventLogger eventLogger) {
    this.uacService = uacService;
    this.eventLogger = eventLogger;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  @ServiceActivator(inputChannel = "receiptInputChannel", adviceChain = "retryAdvice")
  public void receiveMessage(Message<byte[]> message) {
    EventDTO event = convertJsonBytesToEvent(message.getPayload());

    UacQidLink uacQidLink = uacService.findByQid(event.getPayload().getReceipt().getQid());

    if (!uacQidLink.isReceiptReceived()) {
      uacQidLink.setActive(false);
      uacQidLink.setReceiptReceived(true);

      uacQidLink =
          uacService.saveAndEmitUacUpdateEvent(
              uacQidLink,
              event.getHeader().getCorrelationId(),
              event.getHeader().getOriginatingUser());
    }

    eventLogger.logUacQidEvent(uacQidLink, "Receipt received", EventType.RECEIPT, event, message);
  }
}
