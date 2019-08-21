package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CreateUacQid;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
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
    when(uacProcessor.generateAndSaveUacQidLink(null, 21, createUacQid.getBatchId()))
        .thenReturn(uacQidLink);
    when(uacProcessor.emitUacUpdatedEvent(any(UacQidLink.class), any()))
        .thenReturn(new PayloadDTO());

    // When
    underTest.receiveMessage(createUacQid);

    // Then
    verify(uacProcessor).emitUacUpdatedEvent(eq(uacQidLink), eq(null));
    verify(eventLogger)
        .logUacQidEvent(
            eq(uacQidLink),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class),
            eq("Unaddressed UAC/QID pair created"),
            any(EventType.class),
            any(EventDTO.class),
            anyString());
  }
}
