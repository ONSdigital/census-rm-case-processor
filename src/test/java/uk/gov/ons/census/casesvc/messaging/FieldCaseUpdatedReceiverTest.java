package uk.gov.ons.census.casesvc.messaging;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.FieldCaseUpdatedService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementFieldUpdatedEvent;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

@RunWith(MockitoJUnitRunner.class)
public class FieldCaseUpdatedReceiverTest {

  @Mock
  FieldCaseUpdatedService fieldCaseUpdatedService;

  @InjectMocks
  FieldCaseUpdatedReceiver underTest;

  @Test
  public void testFieldCaseUpdatedReceiver() {

    ResponseManagementEvent managementEvent = getTestResponseManagementFieldUpdatedEvent();
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    underTest.receiveMessage(message);

//    // THEN
    ArgumentCaptor<ResponseManagementEvent> managementEventArgumentCaptor =
            ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(fieldCaseUpdatedService)
            .processFieldCaseUpdatedEvent(managementEventArgumentCaptor.capture(), eq(expectedDate));

    Integer expectedCapacity = managementEventArgumentCaptor.getValue().getPayload().getCollectionCase().getCeExpectedCapacity();
    assertThat(expectedCapacity).isEqualTo(5);
  }
}
