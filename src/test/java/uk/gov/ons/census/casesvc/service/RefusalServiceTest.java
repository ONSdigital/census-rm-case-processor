package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementRefusalEvent;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;

@RunWith(MockitoJUnitRunner.class)
public class RefusalServiceTest {

  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final String ESTAB_ADDRESS_LEVEL = "E";
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @InjectMocks RefusalService underTest;

  @Test
  public void testRefusalForCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementRefusalEvent();
    CollectionCase collectionCase = managementEvent.getPayload().getRefusal().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID.toString());
    collectionCase.setRefusalReceived(false);
    Case testCase = getRandomCase();
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);

    // WHEN
    underTest.processRefusal(managementEvent, messageTimestamp);

    // THEN

    InOrder inOrder = inOrder(caseService, eventLogger);

    inOrder.verify(caseService).getCaseByCaseId(any(UUID.class));

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    inOrder
        .verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    Metadata metadata = metadataArgumentCaptor.getValue();
    verifyNoMoreInteractions(caseService);

    assertThat(actualCase.isRefusalReceived()).isTrue();
    assertThat(metadata.getCauseEventType()).isEqualTo(EventTypeDTO.REFUSAL_RECEIVED);
    assertThat(metadata.getFieldDecision()).isEqualTo(ActionInstructionType.CLOSE);
    inOrder
        .verify(eventLogger, times(1))
        .logCaseEvent(
            eq(testCase),
            any(OffsetDateTime.class),
            eq(REFUSAL_RECEIVED),
            eq(EventType.REFUSAL_RECEIVED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testRefusalNotFromFieldForEstabAddressLevelCaseIsLoggedWithoutRefusingTheCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementRefusalEvent();
    managementEvent.getEvent().setChannel("NOT FROM FIELD");
    managementEvent.getPayload().getRefusal().getCollectionCase().setId(TEST_CASE_ID.toString());

    Case testCase = getRandomCase();
    testCase.setAddressLevel(ESTAB_ADDRESS_LEVEL);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);

    // WHEN
    underTest.processRefusal(managementEvent, messageTimestamp);

    // THEN
    // verify the event is logged
    verify(eventLogger)
        .logCaseEvent(
            eq(testCase),
            any(OffsetDateTime.class),
            eq("Refusal received for individual on Estab"),
            eq(EventType.REFUSAL_RECEIVED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);

    // verify we do not try to update the case in any way
    verify(caseService, times(1)).getCaseByCaseId(any(UUID.class));
    verifyNoMoreInteractions(caseService);
  }
}
