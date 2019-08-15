package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.FulfilmentRequestProcessor;

public class FulfilmentRequestReceiverTest {

  @Test
  public void testFulfilmentRequestReceiver() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestProcessor fulfilmentRequestProcessor = mock(FulfilmentRequestProcessor.class);

    FulfilmentRequestReceiver fulfilmentRequestReceiver =
        new FulfilmentRequestReceiver(fulfilmentRequestProcessor);
    fulfilmentRequestReceiver.receiveMessage(managementEvent);

    verify(fulfilmentRequestProcessor, times(1)).processFulfilmentRequest(managementEvent);
  }
}
