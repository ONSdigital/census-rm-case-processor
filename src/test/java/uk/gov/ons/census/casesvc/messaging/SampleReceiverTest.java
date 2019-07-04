package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.service.EventProcessor;

@RunWith(MockitoJUnitRunner.class)
public class SampleReceiverTest {

  @InjectMocks private SampleReceiver underTest;

  @Mock private EventProcessor eventProcessor;

  @Test
  public void testHappyPath() throws Exception {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();

    // When
    underTest.receiveMessage(createCaseSample);

    // Then
    verify(eventProcessor).processSampleReceivedMessage(createCaseSample);
  }
}
