package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.NonComplianceTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.service.CaseService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

@RunWith(MockitoJUnitRunner.class)
public class RmNonComplianceReceiverTest {

  public static final String FIELD_CORDINATOR_ID = "M";
  public static final String FIELD_OFFICER_ID = "007";
  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @InjectMocks private RmNonComplianceReceiver underTest;

  @Test
  public void testReceiveMessageWithNoFieldOfficerOrCordUpdatesNCF() {
    // Given
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(UUID.randomUUID());
    collectionCase.setNonComplianceStatus(NonComplianceTypeDTO.NCF);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setCollectionCase(collectionCase);
    managementEvent.setPayload(payloadDTO);

    EventDTO eventDTO = new EventDTO();
    eventDTO.setDateTime(OffsetDateTime.now());
    managementEvent.setEvent(eventDTO);

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);

    Case caze = new Case();
    caze.setCaseId(collectionCase.getId());
    when(caseService.getCaseByCaseId(any())).thenReturn(caze);

    // When
    underTest.receiveMessage(message);

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), isNull());
    Case savedCase = caseArgumentCaptor.getValue();

    assertThat(savedCase.getCaseId()).isEqualTo(collectionCase.getId());
    assertThat(savedCase.getMetadata().getNonCompliance()).isEqualTo(NonComplianceTypeDTO.NCF);

    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            any(),
            eq("None Compliance"),
            eq(EventType.SELECTED_FOR_NON_COMPLIANCE),
            any(),
            any(),
            eq(expectedDateTime));
  }

  @Test
  public void testReceiveMessageWithNoFieldOfficerOrCordUpdatesNCL() {
    // Given
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(UUID.randomUUID());
    collectionCase.setNonComplianceStatus(NonComplianceTypeDTO.NCL);
    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setCollectionCase(collectionCase);
    managementEvent.setPayload(payloadDTO);

    EventDTO eventDTO = new EventDTO();
    eventDTO.setDateTime(OffsetDateTime.now());
    managementEvent.setEvent(eventDTO);

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);

    Case caze = new Case();
    caze.setCaseId(collectionCase.getId());
    when(caseService.getCaseByCaseId(any())).thenReturn(caze);

    // When
    underTest.receiveMessage(message);

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), isNull());
    Case savedCase = caseArgumentCaptor.getValue();

    assertThat(savedCase.getCaseId()).isEqualTo(collectionCase.getId());
    assertThat(savedCase.getMetadata().getNonCompliance()).isEqualTo(NonComplianceTypeDTO.NCL);

    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            any(),
            eq("None Compliance"),
            eq(EventType.SELECTED_FOR_NON_COMPLIANCE),
            any(),
            any(),
            eq(expectedDateTime));
  }

  @Test
  public void testFieldCordAndOfficerIdsUpdated() {
    // Given
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setId(UUID.randomUUID());
    collectionCase.setNonComplianceStatus(NonComplianceTypeDTO.NCL);
    collectionCase.setFieldOfficerId(FIELD_OFFICER_ID);
    collectionCase.setFieldCoordinatorId(FIELD_CORDINATOR_ID);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setCollectionCase(collectionCase);
    managementEvent.setPayload(payloadDTO);

    EventDTO eventDTO = new EventDTO();
    eventDTO.setDateTime(OffsetDateTime.now());
    managementEvent.setEvent(eventDTO);

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDateTime = MsgDateHelper.getMsgTimeStamp(message);

    Case caze = new Case();
    caze.setCaseId(collectionCase.getId());
    when(caseService.getCaseByCaseId(any())).thenReturn(caze);

    // When
    underTest.receiveMessage(message);

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), isNull());
    Case savedCase = caseArgumentCaptor.getValue();

    assertThat(savedCase.getCaseId()).isEqualTo(collectionCase.getId());
    assertThat(savedCase.getMetadata().getNonCompliance()).isEqualTo(NonComplianceTypeDTO.NCL);
    assertThat(savedCase.getFieldOfficerId()).isEqualTo(FIELD_OFFICER_ID);
    assertThat(savedCase.getFieldCoordinatorId()).isEqualTo(FIELD_CORDINATOR_ID);

    verify(eventLogger)
        .logCaseEvent(
            eq(caze),
            any(),
            eq("None Compliance"),
            eq(EventType.SELECTED_FOR_NON_COMPLIANCE),
            any(),
            any(),
            eq(expectedDateTime));
  }
}
