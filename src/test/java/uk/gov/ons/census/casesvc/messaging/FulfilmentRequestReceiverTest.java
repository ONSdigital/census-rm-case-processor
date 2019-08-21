package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.FulfilmentRequestService;

public class FulfilmentRequestReceiverTest {

  @Test
  public void testFulfilmentRequestReceiver() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestService fulfilmentRequestService = mock(FulfilmentRequestService.class);

    FulfilmentRequestReceiver fulfilmentRequestReceiver =
        new FulfilmentRequestReceiver(fulfilmentRequestService);
    fulfilmentRequestReceiver.receiveMessage(managementEvent);

    verify(fulfilmentRequestService, times(1)).processFulfilmentRequest(managementEvent);
  }
}
