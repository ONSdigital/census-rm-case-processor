package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.FulfilmentConfirmedService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

public class FulfilmentConfirmedReceiverTest {

  @Test
  public void testFulfilmentRequestReceiver() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    FulfilmentConfirmedService fulfilmentConfirmedService = mock(FulfilmentConfirmedService.class);

    FulfilmentConfirmedReceiver fulfilmentConfirmedReceiver =
        new FulfilmentConfirmedReceiver(fulfilmentConfirmedService);
    fulfilmentConfirmedReceiver.receiveMessage(message);

    verify(fulfilmentConfirmedService, times(1))
        .processFulfilmentConfirmed(managementEvent, expectedDate);
  }
}
