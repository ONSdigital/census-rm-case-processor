package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementCCSAddressListedEvent;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.CCSPropertyListedService;

public class CCSPropertyListedReceiverTest {

  @Test
  public void testCCSPropertyListed() {

    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    String expectedCaseId =
        managementEvent.getPayload().getCcsProperty().getCollectionCase().getId();

    CCSPropertyListedService ccsPropertyListedService = mock(CCSPropertyListedService.class);

    CCSPropertyListedReceiver ccsPropertyListedReceiver =
        new CCSPropertyListedReceiver(ccsPropertyListedService);
    ccsPropertyListedReceiver.receiveMessage(managementEvent);

    ArgumentCaptor<ResponseManagementEvent> eventArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(ccsPropertyListedService).processCCSPropertyListed(eventArgumentCaptor.capture());

    String actualCaseId =
        eventArgumentCaptor.getValue().getPayload().getCcsProperty().getCollectionCase().getId();
    assertThat(actualCaseId).isEqualTo(expectedCaseId);
  }
}
