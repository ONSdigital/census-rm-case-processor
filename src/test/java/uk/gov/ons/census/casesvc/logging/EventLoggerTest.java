package uk.gov.ons.census.casesvc.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.utility.EventHelper;

@RunWith(MockitoJUnitRunner.class)
public class EventLoggerTest {
  @Mock EventRepository eventRepository;

  @InjectMocks EventLogger underTest;

  @Test
  public void testLogCaseEvent() {
    Case caze = new Case();
    OffsetDateTime eventTime = OffsetDateTime.now();
    EventDTO eventDTO = EventHelper.createEventDTO(EventTypeDTO.CASE_CREATED);
    eventDTO.setSource("Test source");
    eventDTO.setChannel("Test channel");

    underTest.logCaseEvent(
        caze,
        eventTime,
        "Test description",
        EventType.UAC_UPDATED,
        eventDTO,
        "{\"test\":\"json\"}");

    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    Event actualEvent = eventArgumentCaptor.getValue();
    assertThat(caze).isEqualTo(actualEvent.getCaze());
    assertThat(actualEvent.getUacQidLink()).isNull();
    assertThat(eventTime).isEqualTo(actualEvent.getEventDate());
    assertThat("Test source").isEqualTo(actualEvent.getEventSource());
    assertThat("Test channel").isEqualTo(actualEvent.getEventChannel());
    assertThat(EventType.UAC_UPDATED).isEqualTo(actualEvent.getEventType());
    assertThat("Test description").isEqualTo(actualEvent.getEventDescription());
    assertThat("{\"test\":\"json\"}").isEqualTo(actualEvent.getEventPayload());
    assertThat(eventDTO.getTransactionId()).isEqualTo(actualEvent.getEventTransactionId());
  }

  @Test
  public void testLogUacQidEvent() {
    UacQidLink uacQidLink = new UacQidLink();
    OffsetDateTime eventTime = OffsetDateTime.now();
    EventDTO eventDTO = EventHelper.createEventDTO(EventTypeDTO.CASE_CREATED);
    eventDTO.setSource("Test source");
    eventDTO.setChannel("Test channel");

    underTest.logUacQidEvent(
        uacQidLink,
        eventTime,
        "Test description",
        EventType.UAC_UPDATED,
        eventDTO,
        "{\"test\":\"json\"}");

    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    Event actualEvent = eventArgumentCaptor.getValue();
    assertThat(uacQidLink).isEqualTo(actualEvent.getUacQidLink());
    assertThat(actualEvent.getCaze()).isNull();
    assertThat(eventTime).isEqualTo(actualEvent.getEventDate());
    assertThat("Test source").isEqualTo(actualEvent.getEventSource());
    assertThat("Test channel").isEqualTo(actualEvent.getEventChannel());
    assertThat(EventType.UAC_UPDATED).isEqualTo(actualEvent.getEventType());
    assertThat("Test description").isEqualTo(actualEvent.getEventDescription());
    assertThat("{\"test\":\"json\"}").isEqualTo(actualEvent.getEventPayload());
    assertThat(eventDTO.getTransactionId()).isEqualTo(actualEvent.getEventTransactionId());
  }
}
