package uk.gov.ons.census.casesvc.messaging;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.DeactivateUacDto;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.service.UacService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

@RunWith(MockitoJUnitRunner.class)
public class DeactivateUacReceiverTest {
  private static final String TEST_QID = "test_qid";

  @Mock
  private UacService uacService;
  @Mock private EventLogger eventLogger;

  @InjectMocks
  DeactivateUacReceiver underTest;

  @Test
  public void testDeactivateUac() {
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    EventDTO eventDTO = new EventDTO();
    eventDTO.setType(EventTypeDTO.DEACTIVATE_UAC);
    responseManagementEvent.setEvent(eventDTO);

    PayloadDTO payloadDTO = new PayloadDTO();
    DeactivateUacDto deactivateUacDto = new DeactivateUacDto();
    deactivateUacDto.setQid(TEST_QID);
    payloadDTO.setDeactivateUacDto(deactivateUacDto);
    responseManagementEvent.setPayload(payloadDTO);

    Message<ResponseManagementEvent> message =
        constructMessageWithValidTimeStamp(responseManagementEvent);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(TEST_QID);
    when(uacService.findByQid(TEST_QID)).thenReturn(uacQidLink);

    underTest.receiveMessage(message);

    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);

    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkArgumentCaptor.capture());

    assertThat(uacQidLinkArgumentCaptor.getValue().getQid()).isEqualTo(TEST_QID);
    assertFalse(uacQidLinkArgumentCaptor.getValue().isActive());

    verify(eventLogger)
        .logUacQidEvent(
            eq(uacQidLink),
            any(),
            eq("DEACTIVATED UAC"),
            eq(EventType.DEACTIVATE_UAC),
            any(),
            any(),
            any());
  }
}
