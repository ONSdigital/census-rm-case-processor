package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.*;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.Receipt;
import uk.gov.ons.census.casesvc.service.ReceiptProcessor;

public class ReceiptReceiverTest {

  @Test
  public void testReceipting() {
    ReceiptProcessor receiptProcessor = mock(ReceiptProcessor.class);

    ReceiptReceiver receiptReceiver = new ReceiptReceiver(receiptProcessor);
    Receipt receipt = new Receipt();
    receiptReceiver.receiveMessage(receipt);

    verify(receiptProcessor, times(1)).processReceipt(receipt);
  }
}
