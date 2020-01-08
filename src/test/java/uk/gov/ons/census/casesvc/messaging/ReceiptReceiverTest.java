package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.ReceiptService;

public class ReceiptReceiverTest {

  @Test
  public void testReceipting() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ReceiptService receiptService = mock(ReceiptService.class);

    ReceiptReceiver receiptReceiver = new ReceiptReceiver(receiptService);
    receiptReceiver.receiveMessage(managementEvent);

    verify(receiptService, times(1)).processReceipt(managementEvent);
  }
}
