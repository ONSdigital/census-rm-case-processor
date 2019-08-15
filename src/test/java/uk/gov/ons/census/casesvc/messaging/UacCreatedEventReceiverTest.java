package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.generateUacCreatedEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.service.UacProcessor;

@RunWith(MockitoJUnitRunner.class)
public class UacCreatedEventReceiverTest {
  @Mock UacProcessor uacProcessor;

  @InjectMocks UacCreatedEventReceiver underTest;

  @Test
  public void testReceiveMessage() {
    // Given
    Case linkedCase = getRandomCase();
    ResponseManagementEvent uacCreatedEvent = generateUacCreatedEvent(linkedCase);

    // When
    underTest.receiveMessage(uacCreatedEvent);

    // Then
    verify(uacProcessor).ingestUacCreatedEvent(eq(uacCreatedEvent));
  }
}
