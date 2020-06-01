package uk.gov.ons.census.casesvc.service;

import org.hibernate.type.descriptor.java.DataHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.messaging.FieldCaseUpdatedReceiver;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.utility.MetadataHelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementFieldUpdatedEvent;

@RunWith(MockitoJUnitRunner.class)
public class FieldCaseUpdatedServiceTest {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock
  private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @Mock private CaseRepository caseRepository;

  @Mock private MetadataHelper metadataHelper;

  @InjectMocks FieldCaseUpdatedService underTest;

  @Test
  public void processFieldCaseUpdatedEventHappyPath(){

    ResponseManagementEvent managementEvent = getTestResponseManagementFieldUpdatedEvent();

    CollectionCase collectionCase = managementEvent.getPayload().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID.toString());

    Case testCase = getRandomCase();
    testCase.setCaseId(TEST_CASE_ID);
    testCase.setCaseType("CE");
    testCase.setCeExpectedCapacity(9);
    testCase.setCeActualResponses(8);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseRepository.getCaseAndLockByCaseId(UUID.fromString(collectionCase.getId()))).thenReturn(java.util.Optional.of(testCase));

    underTest.processFieldCaseUpdatedEvent(managementEvent, messageTimestamp);

    // Then

    InOrder inOrder = Mockito.inOrder(caseRepository, eventLogger, caseService);

    inOrder.verify(caseRepository).getCaseAndLockByCaseId(any(UUID.class));

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);

    inOrder.verify(caseService).saveCaseAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());
    Case caze = caseArgumentCaptor.getValue();
    Metadata metadata = metadataArgumentCaptor.getValue();

    assertThat(caze.getCeExpectedCapacity()).isEqualTo(5);

    assertThat(metadata.getFieldDecision()).isEqualTo(ActionInstructionType.CANCEL);
    assertThat(metadata.getCauseEventType()).isEqualTo(EventTypeDTO.FIELD_CASE_UPDATED);

  }
}
