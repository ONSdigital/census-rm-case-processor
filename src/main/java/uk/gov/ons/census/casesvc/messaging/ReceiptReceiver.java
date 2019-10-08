package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.ReceiptService;

@MessageEndpoint
public class ReceiptReceiver {
  private final ReceiptService receiptService;

  public ReceiptReceiver(ReceiptService receiptService) {
    this.receiptService = receiptService;
  }

  @Transactional
  @ServiceActivator(inputChannel = "receiptInputChannel", adviceChain = "messageHandlingAdvice")
  public void receiveMessage(ResponseManagementEvent receiptEvent) {
    receiptService.processReceipt(receiptEvent);
  }
}
