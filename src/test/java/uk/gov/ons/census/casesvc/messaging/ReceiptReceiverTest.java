package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.ReceiptProcessor;

public class ReceiptReceiverTest {

  @Test
  public void testReceipting() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ReceiptProcessor receiptProcessor = mock(ReceiptProcessor.class);

    ReceiptReceiver receiptReceiver = new ReceiptReceiver(receiptProcessor);
    receiptReceiver.receiveMessage(managementEvent);

    verify(receiptProcessor, times(1)).processReceipt(managementEvent);
  }
}
