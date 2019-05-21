package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.CreateUacQid;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.UacProcessor;

@RunWith(MockitoJUnitRunner.class)
public class UnaddressedReceiverTest {
  @Mock UacProcessor uacProcessor;

  @InjectMocks UnaddressedReceiver underTest;

  @Test
  public void testReceiveCreateUacQid() {
    // Given
    CreateUacQid createUacQid = new CreateUacQid();
    createUacQid.setQuestionnaireType("21");
    UacQidLink uacQidLink = new UacQidLink();
    when(uacProcessor.saveUacQidLink(null, 21)).thenReturn(uacQidLink);

    // When
    underTest.receiveMessage(createUacQid);

    // Then
    verify(uacProcessor).emitUacUpdatedEvent(eq(uacQidLink), eq(null));
    verify(uacProcessor)
        .logEvent(eq(uacQidLink), eq("Unaddressed UAC/QID pair created"), any(EventType.class));
  }
}
