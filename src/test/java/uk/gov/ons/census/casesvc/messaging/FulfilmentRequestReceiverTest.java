package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.FulfilmentRequestProcessor;

public class FulfilmentRequestReceiverTest {

  @Mock private FulfilmentRequestProcessor fulfilmentRequestProcessor;

  @InjectMocks FulfilmentRequestReceiver underTest;

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
