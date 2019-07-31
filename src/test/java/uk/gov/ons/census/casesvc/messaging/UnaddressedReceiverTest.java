package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CreateUacQid;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.UacProcessor;

@RunWith(MockitoJUnitRunner.class)
public class UnaddressedReceiverTest {
  @Mock UacProcessor uacProcessor;
  @Mock EventLogger eventLogger;

  @InjectMocks UnaddressedReceiver underTest;

  @Test
  public void testReceiveCreateUacQid() {
    // Given
    CreateUacQid createUacQid = new CreateUacQid();
    createUacQid.setQuestionnaireType("21");
    createUacQid.setBatchId(UUID.randomUUID());
    UacQidLink uacQidLink = new UacQidLink();
    when(uacProcessor.saveUacQidLink(null, 21, createUacQid.getBatchId())).thenReturn(uacQidLink);
    when(uacProcessor.emitUacUpdatedEvent(any(UacQidLink.class), any()))
        .thenReturn(new PayloadDTO());

    // When
    underTest.receiveMessage(createUacQid);

    // Then
    verify(uacProcessor).emitUacUpdatedEvent(eq(uacQidLink), eq(null));
    verify(eventLogger)
        .logEvent(
            eq(uacQidLink),
            eq("Unaddressed UAC/QID pair created"),
            any(EventType.class),
            any(PayloadDTO.class));
  }
}
