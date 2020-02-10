package uk.gov.ons.census.casesvc.messaging;

import static uk.gov.ons.census.casesvc.utility.MsgDateHelper.getMsgTimeStamp;

import java.time.OffsetDateTime;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.QidReceiptService;

@MessageEndpoint
public class ReceiptReceiver {
  private final QidReceiptService qidReceiptService;

  public ReceiptReceiver(QidReceiptService qidReceiptService) {
    this.qidReceiptService = qidReceiptService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "receiptInputChannel")
  public void receiveMessage(Message<ResponseManagementEvent> message) {
    OffsetDateTime messageTimestamp = getMsgTimeStamp(message);
    qidReceiptService.processReceipt(message.getPayload(), messageTimestamp);
  }
}
