package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CollectionCaseCaseId;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.InvalidAddress;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@RunWith(MockitoJUnitRunner.class)
public class InvalidAddressServiceTest {

  private final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @InjectMocks InvalidAddressService underTest;

  @Test
  public void testHappyPath() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(EventTypeDTO.ADDRESS_NOT_VALID);
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setInvalidAddress(new InvalidAddress());
    managementEvent.getPayload().getInvalidAddress().setCollectionCase(new CollectionCaseCaseId());
    managementEvent
        .getPayload()
        .getInvalidAddress()
        .getCollectionCase()
        .setId(UUID.randomUUID().toString());

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setAddressInvalid(false);
    when(caseService.getCaseByCaseId(any(UUID.class))).thenReturn(expectedCase);

    // when
    underTest.processMessage(managementEvent);

    // then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isAddressInvalid()).isTrue();
    verify(eventLogger)
        .logCaseEvent(
            eq(expectedCase),
            any(OffsetDateTime.class),
            eq("Invalid address"),
            eq(EventType.ADDRESS_NOT_VALID),
            eq(managementEvent.getEvent()),
            anyString());
  }

  @Test
  public void testInvalidEventType() {
    // Given
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(EventTypeDTO.ADDRESS_MODIFIED);

    CollectionCaseCaseId collectionCaseCaseId = new CollectionCaseCaseId();
    collectionCaseCaseId.setId(TEST_CASE_ID.toString());

    PayloadDTO payload = new PayloadDTO();
    payload.setInvalidAddress(new InvalidAddress());

    payload.getInvalidAddress().setCollectionCase(collectionCaseCaseId);

    managementEvent.setPayload(payload);

    // when
    underTest.processMessage(managementEvent);

    // then
    verify(eventLogger)
        .logCaseEvent(
            isNull(),
            any(OffsetDateTime.class),
            eq(String.format("Unexpected event type '%s'", EventTypeDTO.ADDRESS_MODIFIED)),
            eq(EventType.ADDRESS_MODIFIED),
            eq(managementEvent.getEvent()),
            anyString());

    verifyNoMoreInteractions(eventLogger);
    verifyZeroInteractions(caseService);
  }
}
