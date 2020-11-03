package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementFieldUpdatedEvent;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
public class FieldCaseUpdatedServiceTest {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @Mock private CaseRepository caseRepository;

  @InjectMocks FieldCaseUpdatedService underTest;

  @Test
  public void testProcessFieldCaseUpdatedEventResultsInFieldCancel() {

    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementFieldUpdatedEvent();

    CollectionCase collectionCase = managementEvent.getPayload().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID);

    Case testCase = getRandomCase();
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setCaseType("CE");
    testCase.setAddressLevel("U");
    testCase.setCeExpectedCapacity(9);
    testCase.setCeActualResponses(8);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseAndLockIt(eq(TEST_CASE_ID))).thenReturn(testCase);

    // When
    underTest.processFieldCaseUpdatedEvent(managementEvent, messageTimestamp);

    // Then

    InOrder inOrder = Mockito.inOrder(caseRepository, eventLogger, caseService);

    inOrder.verify(caseService).getCaseAndLockIt(any(UUID.class));

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);

    inOrder
        .verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());
    Case caze = caseArgumentCaptor.getValue();
    Metadata metadata = metadataArgumentCaptor.getValue();

    assertThat(caze.getCeExpectedCapacity()).isEqualTo(5);

    assertThat(metadata.getFieldDecision()).isEqualTo(ActionInstructionType.CANCEL);
    assertThat(metadata.getCauseEventType()).isEqualTo(EventTypeDTO.FIELD_CASE_UPDATED);
  }

  @Test
  public void testProcessFieldCaseUpdatedEventResultsInFieldUpdate() {

    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementFieldUpdatedEvent();

    CollectionCase collectionCase = managementEvent.getPayload().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID);

    Case testCase = getRandomCase();
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setCaseType("CE");
    testCase.setAddressLevel("U");
    testCase.setCeExpectedCapacity(9);
    testCase.setCeActualResponses(3);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseAndLockIt(eq(TEST_CASE_ID))).thenReturn(testCase);

    // When
    underTest.processFieldCaseUpdatedEvent(managementEvent, messageTimestamp);

    // Then

    InOrder inOrder = Mockito.inOrder(caseRepository, eventLogger, caseService);

    inOrder.verify(caseService).getCaseAndLockIt(any(UUID.class));

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);

    inOrder
        .verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());
    Case caze = caseArgumentCaptor.getValue();
    Metadata metadata = metadataArgumentCaptor.getValue();

    assertThat(caze.getCeExpectedCapacity()).isEqualTo(5);

    assertThat(metadata.getFieldDecision()).isEqualTo(ActionInstructionType.UPDATE);
    assertThat(metadata.getCauseEventType()).isEqualTo(EventTypeDTO.FIELD_CASE_UPDATED);
  }

  @Test
  public void testProcessFieldCaseUpdatedEventOnEstabDoesNotSendToField() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementFieldUpdatedEvent();

    CollectionCase collectionCase = managementEvent.getPayload().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID);

    Case testCase = getRandomCase();
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setCaseType("CE");
    testCase.setAddressLevel("E");
    testCase.setCeExpectedCapacity(9);
    testCase.setCeActualResponses(3);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseAndLockIt(eq(TEST_CASE_ID))).thenReturn(testCase);

    // When
    underTest.processFieldCaseUpdatedEvent(managementEvent, messageTimestamp);

    // Then

    InOrder inOrder = Mockito.inOrder(caseRepository, eventLogger, caseService);

    inOrder.verify(caseService).getCaseAndLockIt(any(UUID.class));

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);

    inOrder
        .verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());
    Case caze = caseArgumentCaptor.getValue();
    Metadata metadata = metadataArgumentCaptor.getValue();

    assertThat(caze.getCeExpectedCapacity()).isEqualTo(5);

    assertThat(metadata.getFieldDecision()).isNull();
    assertThat(metadata.getCauseEventType()).isEqualTo(EventTypeDTO.FIELD_CASE_UPDATED);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testProcessFieldCaseUpdatedEventWithHouseholdCase() {

    ResponseManagementEvent managementEvent = getTestResponseManagementFieldUpdatedEvent();

    CollectionCase collectionCase = managementEvent.getPayload().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID);

    Case testCase = getRandomCase();
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setCaseType("HH");
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseAndLockIt(eq(TEST_CASE_ID))).thenReturn(testCase);

    String expectedErrorMessage =
        String.format(
            "Updating expected response count for %s failed as CaseType not CE", TEST_CASE_ID);

    try {
      underTest.processFieldCaseUpdatedEvent(managementEvent, messageTimestamp);
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).isEqualTo(expectedErrorMessage);
      throw e;
    }
  }
}
