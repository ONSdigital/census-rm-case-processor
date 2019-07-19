package uk.gov.ons.census.casesvc.messaging;

import java.util.Map;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ReceiptDTO;
import uk.gov.ons.census.casesvc.service.ReceiptProcessor;

@MessageEndpoint
public class ReceiptReceiver {
  private final ReceiptProcessor receiptProcessor;

  public ReceiptReceiver(ReceiptProcessor receiptProcessor) {
    this.receiptProcessor = receiptProcessor;
  }

  @Transactional
  @ServiceActivator(inputChannel = "receiptInputChannel")
  public void receiveMessage(ReceiptDTO receipt, @Headers Map<String, String> headers) {
    receiptProcessor.processReceipt(receipt, headers);
  }
}
