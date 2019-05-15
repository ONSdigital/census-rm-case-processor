package uk.gov.ons.census.casesvc.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.utility.IacDispenser;
import uk.gov.ons.census.casesvc.utility.QidCreator;

@RunWith(MockitoJUnitRunner.class)
public class UacProcessorTest {

  @Mock UacQidLinkRepository uacQidLinkRepository;

  @Mock EventRepository eventRepository;

  @Mock RabbitTemplate rabbitTemplate;

  @Mock IacDispenser iacDispenser;

  @Spy QidCreator qidCreator;

  @InjectMocks UacProcessor underTest;

  @Test
  public void testSaveUacQidLinkEnglandHousehold() {
    // Given
    Case caze = new Case();
    ReflectionTestUtils.setField(qidCreator, "modulus", 33);
    ReflectionTestUtils.setField(qidCreator, "factor", 802);
    ReflectionTestUtils.setField(qidCreator, "trancheIdentifier", 2);

    when(iacDispenser.getIacCode()).thenReturn("TEST_IAC");
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.fromString("7dc53df5-703e-49b3-8670-b1c468f47f1f"));
    uacQidLink.setUniqueNumber(12345L);
    when(uacQidLinkRepository.saveAndFlush(any(UacQidLink.class))).thenReturn(uacQidLink);

    // When
    UacQidLink result;
    // ArgumentCaptor<UacQidLink> caseArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    result = underTest.saveUacQidLink(caze, 1);

    // Then
    assertEquals("01", result.getQid().substring(0, 2));
    verify(iacDispenser).getIacCode();
  }

  @Test
  public void testLogEvent() {
    // Given
    UacQidLink uacQuidLink = new UacQidLink();
    uacQuidLink.setUniqueNumber(12345L);

    // When
    underTest.logEvent(uacQuidLink, "TEST_LOGGED_EVENT");

    // Then
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    assertEquals("TEST_LOGGED_EVENT", eventArgumentCaptor.getValue().getEventDescription());
  }

  @Test
  public void testEmitUacUpdatedEvent() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setUac("12345");
    Case caze = new Case();
    UUID caseUuid = UUID.randomUUID();
    caze.setCaseId(caseUuid);
    ReflectionTestUtils.setField(underTest, "outboundExchange", "TEST_EXCHANGE");

    // When
    underTest.emitUacUpdatedEvent(uacQidLink, caze);

    // Then
    ArgumentCaptor<ResponseManagementEvent> responseManagementEventArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("TEST_EXCHANGE"),
            eq("event.uac.update"),
            responseManagementEventArgumentCaptor.capture());
    assertEquals(
        "12345", responseManagementEventArgumentCaptor.getValue().getPayload().getUac().getUac());
  }
}
