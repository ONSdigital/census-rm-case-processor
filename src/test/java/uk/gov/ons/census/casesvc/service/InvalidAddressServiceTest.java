package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.model.dto.EventTypeDTO.ADDRESS_NOT_VALID;
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
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.CollectionCaseCaseId;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.InvalidAddress;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@RunWith(MockitoJUnitRunner.class)
public class InvalidAddressServiceTest {
  @Mock private CaseService caseService;
  @Mock private EventLogger eventLogger;

  @InjectMocks InvalidAddressService underTest;

  @Test
  public void testInvalidAddress() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    managementEvent.setEvent(new EventDTO());
    managementEvent.getEvent().setDateTime(OffsetDateTime.now());
    managementEvent.getEvent().setType(ADDRESS_NOT_VALID);
    managementEvent.getEvent().setChannel("CC");
    managementEvent.setPayload(new PayloadDTO());
    managementEvent.getPayload().setInvalidAddress(new InvalidAddress());
    managementEvent.getPayload().getInvalidAddress().setCollectionCase(new CollectionCaseCaseId());
    managementEvent
        .getPayload()
        .getInvalidAddress()
        .getCollectionCase()
        .setId(UUID.randomUUID().toString());

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setAddressInvalid(false);
    expectedCase.setSurvey("CENSUS");
    when(caseService.getCaseByCaseId(any(UUID.class))).thenReturn(expectedCase);

    // when
    underTest.processMessage(managementEvent, messageTimestamp);

    // then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);

    verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isAddressInvalid()).isTrue();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    Metadata metadata = metadataArgumentCaptor.getValue();
    assertThat(metadata.getCauseEventType()).isEqualTo(ADDRESS_NOT_VALID);
    assertThat(metadata.getFieldDecision()).isEqualTo(ActionInstructionType.CANCEL);

    verify(eventLogger)
        .logCaseEvent(
            eq(expectedCase),
            any(OffsetDateTime.class),
            eq("Invalid address"),
            eq(EventType.ADDRESS_NOT_VALID),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }
}
