package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.service.EventService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

@RunWith(MockitoJUnitRunner.class)
public class SampleReceiverTest {

  @InjectMocks private SampleReceiver underTest;

  @Mock private EventService eventService;

  @Test
  public void testHappyPath() {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();

    Message<CreateCaseSample> message = constructMessageWithValidTimeStamp(createCaseSample);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    // When
    underTest.receiveMessage(message);

    // Then
    verify(eventService).processSampleReceivedMessage(createCaseSample, expectedDate);
  }
}
