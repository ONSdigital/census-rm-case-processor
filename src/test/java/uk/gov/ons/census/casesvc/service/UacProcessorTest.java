package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.client.UacQidServiceClient;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class UacProcessorTest {

  @Mock UacQidLinkRepository uacQidLinkRepository;

  @Mock EventRepository eventRepository;

  @Mock RabbitTemplate rabbitTemplate;

  @Mock UacQidServiceClient uacQidServiceClient;

  @Mock ObjectMapper objectMapper;

  @InjectMocks UacProcessor underTest;

  @Test
  public void testSaveUacQidLinkEnglandHousehold() {
    // Given
    Case caze = new Case();

    UacQidDTO uacQidDTO = new UacQidDTO();
    uacQidDTO.setUac("testuac");
    uacQidDTO.setQid("01testqid");
    when(uacQidServiceClient.generateUacQid(anyInt())).thenReturn(uacQidDTO);

    // When
    UacQidLink result;
    result = underTest.saveUacQidLink(caze, 1);

    // Then
    assertEquals("01", result.getQid().substring(0, 2));
    verify(uacQidServiceClient).generateUacQid(eq(1));
  }

  @Test
  public void testLogEventWithoutEventMetaDataDateTime() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    Case caze = new Case();
    UUID caseUuid = UUID.randomUUID();
    caze.setCaseId(caseUuid);
    uacQidLink.setCaze(caze);

    // When
    underTest.logEvent(uacQidLink, "TEST_LOGGED_EVENT", EventType.UAC_UPDATED, new PayloadDTO());

    // Then
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    assertEquals("TEST_LOGGED_EVENT", eventArgumentCaptor.getValue().getEventDescription());
    assertEquals(EventType.UAC_UPDATED, eventArgumentCaptor.getValue().getEventType());
  }

  @Test
  public void testLogEventWithEventMetaDataDateTime() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    OffsetDateTime now = OffsetDateTime.now();
    Case caze = new Case();
    UUID caseUuid = UUID.randomUUID();
    caze.setCaseId(caseUuid);
    uacQidLink.setCaze(caze);
    Map<String, String> headers = createTestDefaultHeaders();

    // When
    underTest.logEvent(
        uacQidLink,
        "TEST_LOGGED_EVENT",
        EventType.UAC_UPDATED,
        new PayloadDTO(),
        headers,
        any(OffsetDateTime.class));

    // Then
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    assertEquals("TEST_LOGGED_EVENT", eventArgumentCaptor.getValue().getEventDescription());
    assertEquals(EventType.UAC_UPDATED, eventArgumentCaptor.getValue().getEventType());
    assertEquals(
        now.toString().substring(0, 17),
        eventArgumentCaptor.getValue().getEventDate().toString().substring(0, 17));
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
        "12345",
        responseManagementEventArgumentCaptor.getValue().getPayloadDTO().getUac().getUac());
  }

  @Test
  public void testLogEventWithDefaultHeaders() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    Map<String, String> headers = createTestDefaultHeaders();

    // When
    underTest.logEvent(
        uacQidLink,
        "TEST_LOGGED_EVENT",
        EventType.UAC_UPDATED,
        new PayloadDTO(),
        headers,
        any(OffsetDateTime.class));

    // Then
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    assertThat(eventArgumentCaptor.getValue().getEventSource()).isEqualTo("any default source");
    assertThat(eventArgumentCaptor.getValue().getEventChannel()).isEqualTo("any default channel");
  }

  @Test
  public void testLogEventWithNonDefaultHeaders() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    Map<String, String> headers = createTestNonDefaultHeaders();

    // When
    underTest.logEvent(
        uacQidLink,
        "TEST_LOGGED_EVENT",
        EventType.UAC_UPDATED,
        new PayloadDTO(),
        headers,
        any(OffsetDateTime.class));

    // Then
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    assertThat(eventArgumentCaptor.getValue().getEventSource()).isEqualTo("any non-default source");
    assertThat(eventArgumentCaptor.getValue().getEventChannel())
        .isEqualTo("any non-default channel");
  }

  @Test(expected = RuntimeException.class)
  public void testLogEventWithInvalidHeaders() {

    // When
    underTest.logEvent(
        null, "TEST_LOGGED_EVENT", EventType.UAC_UPDATED, null, createTestInvalidHeaders(), null);
  }

  private Map<String, String> createTestDefaultHeaders() {
    Map<String, String> headers = new HashMap<>();

    headers.put("channel", "any default channel");
    headers.put("source", "any default source");

    return headers;
  }

  private Map<String, String> createTestNonDefaultHeaders() {
    Map<String, String> headers = new HashMap<>();

    headers.put("channel", "any non-default channel");
    headers.put("source", "any non-default source");

    return headers;
  }

  private Map<String, String> createTestInvalidHeaders() {
    Map<String, String> headers = new HashMap<>();

    headers.put("not expected key", "anything");

    return headers;
  }
}
