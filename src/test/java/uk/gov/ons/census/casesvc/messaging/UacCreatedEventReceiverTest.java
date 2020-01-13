package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.generateUacCreatedEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.service.UacService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

@RunWith(MockitoJUnitRunner.class)
public class UacCreatedEventReceiverTest {
  @Mock UacService uacService;

  @InjectMocks UacCreatedEventReceiver underTest;

  @Test
  public void testReceiveMessage() {
    // Given
    Case linkedCase = getRandomCase();
    ResponseManagementEvent uacCreatedEvent = generateUacCreatedEvent(linkedCase);

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(uacCreatedEvent);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    // When
    underTest.receiveMessage(message);

    // Then
    verify(uacService).ingestUacCreatedEvent(eq(uacCreatedEvent), eq(expectedDate));
  }
}
