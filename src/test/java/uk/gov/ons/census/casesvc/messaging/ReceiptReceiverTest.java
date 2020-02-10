package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.QidReceiptService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

public class ReceiptReceiverTest {

  @Test
  public void testReceipting() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    QidReceiptService qidReceiptService = mock(QidReceiptService.class);
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    ReceiptReceiver receiptReceiver = new ReceiptReceiver(qidReceiptService);
    receiptReceiver.receiveMessage(message);

    verify(qidReceiptService, times(1)).processReceipt(managementEvent, expectedDate);
  }
}
