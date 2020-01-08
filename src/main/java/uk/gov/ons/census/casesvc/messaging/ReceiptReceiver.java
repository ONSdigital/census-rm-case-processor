package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.ReceiptService;

import java.time.OffsetDateTime;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

@MessageEndpoint
public class ReceiptReceiver {
  private final ReceiptService receiptService;

  public ReceiptReceiver(ReceiptService receiptService) {
    this.receiptService = receiptService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "receiptInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    ResponseManagementEvent receiptEvent = message.getPayload();
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    receiptService.processReceipt(receiptEvent, messageTimestamp);
  }
}
