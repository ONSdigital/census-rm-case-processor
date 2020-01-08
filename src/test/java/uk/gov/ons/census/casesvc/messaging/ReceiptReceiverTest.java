package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import org.junit.Test;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.ReceiptService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

import java.time.OffsetDateTime;

public class ReceiptReceiverTest {

  @Test
  public void testReceipting() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ReceiptService receiptService = mock(ReceiptService.class);
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    ReceiptReceiver receiptReceiver = new ReceiptReceiver(receiptService);
    receiptReceiver.receiveMessage(message);

    verify(receiptService, times(1)).processReceipt(managementEvent, expectedDate);
  }
}
