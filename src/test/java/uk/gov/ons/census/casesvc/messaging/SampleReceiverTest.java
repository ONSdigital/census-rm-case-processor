package uk.gov.ons.census.casesvc.messaging;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.service.EventProcessor;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class SampleReceiverTest {

  @InjectMocks private SampleReceiver underTest;

  @Mock private EventProcessor eventProcessor;

  @Test
  public void testHappyPath() {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();

    // When
    underTest.receiveMessage(createCaseSample);

    // Then
    verify(eventProcessor).processSampleReceivedMessage(createCaseSample);
  }
}
