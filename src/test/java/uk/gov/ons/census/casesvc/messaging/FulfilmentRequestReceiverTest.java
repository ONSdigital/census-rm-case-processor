package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import org.junit.Test;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.FulfilmentRequestService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

import java.time.OffsetDateTime;

public class FulfilmentRequestReceiverTest {

  @Test
  public void testFulfilmentRequestReceiver() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);


    FulfilmentRequestService fulfilmentRequestService = mock(FulfilmentRequestService.class);

    FulfilmentRequestReceiver fulfilmentRequestReceiver =
        new FulfilmentRequestReceiver(fulfilmentRequestService);
    fulfilmentRequestReceiver.receiveMessage(message);

    verify(fulfilmentRequestService, times(1)).processFulfilmentRequest(managementEvent, expectedDate);
  }
}
