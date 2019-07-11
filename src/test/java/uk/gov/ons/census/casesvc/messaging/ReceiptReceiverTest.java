package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.Receipt;
import uk.gov.ons.census.casesvc.service.ReceiptProcessor;

public class ReceiptReceiverTest {

  @Test
  public void testReceipting() throws Exception {
    ReceiptProcessor receiptProcessor = mock(ReceiptProcessor.class);

    Map<String, String> headers = new HashMap<>();
    headers.put("channel", "any receipt channel");
    headers.put("source", "any receipt source");

    ReceiptReceiver receiptReceiver = new ReceiptReceiver(receiptProcessor);
    Receipt receipt = new Receipt();
    receiptReceiver.receiveMessage(receipt, headers);

    verify(receiptProcessor, times(1)).processReceipt(receipt, headers);
  }
}
