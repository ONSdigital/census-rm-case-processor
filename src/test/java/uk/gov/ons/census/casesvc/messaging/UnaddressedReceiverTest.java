package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CreateUacQid;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.UacService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

@RunWith(MockitoJUnitRunner.class)
public class UnaddressedReceiverTest {

  @Mock UacService uacService;
  @Mock EventLogger eventLogger;

  @InjectMocks UnaddressedReceiver underTest;

  @Test
  public void testReceiveCreateUacQid() {
    // Given
    CreateUacQid createUacQid = new CreateUacQid();
    createUacQid.setQuestionnaireType("21");
    createUacQid.setBatchId(UUID.randomUUID());
    UacQidLink uacQidLink = new UacQidLink();
    Message<CreateUacQid> message = constructMessageWithValidTimeStamp(createUacQid);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);
    when(uacService.buildUacQidLink(
            eq(null), eq(21), eq(createUacQid.getBatchId()), any(EventDTO.class)))
        .thenReturn(uacQidLink);
    when(uacService.saveAndEmitUacUpdatedEvent(any(UacQidLink.class))).thenReturn(new PayloadDTO());

    // When
    underTest.receiveMessage(message);

    // Then
    verify(uacService).saveAndEmitUacUpdatedEvent(eq(uacQidLink));
    verify(eventLogger)
        .logUacQidEvent(
            eq(uacQidLink),
            any(OffsetDateTime.class),
            eq("Unaddressed UAC/QID pair created"),
            any(EventType.class),
            any(EventDTO.class),
            any(),
            eq(expectedDate));
  }
}
