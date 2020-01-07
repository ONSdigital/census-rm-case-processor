package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementCCSAddressListedEvent;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.CCSPropertyListedService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

import java.time.OffsetDateTime;

public class CCSPropertyListedReceiverTest {

  @Test
  public void testCCSPropertyListed() {

    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime messageTimestamp = MsgDateHelper.getMsgTimeStamp(message);

    String expectedCaseId =
        managementEvent.getPayload().getCcsProperty().getCollectionCase().getId();

    CCSPropertyListedService ccsPropertyListedService = mock(CCSPropertyListedService.class);

    CCSPropertyListedReceiver ccsPropertyListedReceiver =
        new CCSPropertyListedReceiver(ccsPropertyListedService);
    ccsPropertyListedReceiver.receiveMessage(message);

    ArgumentCaptor<ResponseManagementEvent> eventArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(ccsPropertyListedService).processCCSPropertyListed(eventArgumentCaptor.capture(), eq(messageTimestamp));

    String actualCaseId =
        eventArgumentCaptor.getValue().getPayload().getCcsProperty().getCollectionCase().getId();
    assertThat(actualCaseId).isEqualTo(expectedCaseId);
  }
}
