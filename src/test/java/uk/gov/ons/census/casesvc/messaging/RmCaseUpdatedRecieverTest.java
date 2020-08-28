package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.RmCaseUpdatedService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

@RunWith(MockitoJUnitRunner.class)
public class RmCaseUpdatedRecieverTest {

  @Mock private RmCaseUpdatedService rmCaseUpdatedService;

  @InjectMocks private RmCaseUpdatedReciever underTest;

  @Test
  public void testReceiveMessage() {
    // Given
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    // When
    underTest.receiveMessage(message);

    // Then
    verify(rmCaseUpdatedService).processMessage(eq(managementEvent), eq(expectedDate));
  }
}
