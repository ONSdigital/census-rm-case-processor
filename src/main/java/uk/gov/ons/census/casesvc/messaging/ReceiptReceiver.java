package uk.gov.ons.census.casesvc.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.Receipt;
import uk.gov.ons.census.casesvc.service.ReceiptProcessor;

@MessageEndpoint
public class ReceiptReceiver {
  private final ReceiptProcessor receiptProcessor;

  public ReceiptReceiver(ReceiptProcessor receiptProcessor) {
    this.receiptProcessor = receiptProcessor;
  }

  @Transactional
  @ServiceActivator(inputChannel = "receiptInputChannel")
  public void receiveMessage(Receipt receipt) throws JsonProcessingException {
    receiptProcessor.processReceipt(receipt);
  }
}
