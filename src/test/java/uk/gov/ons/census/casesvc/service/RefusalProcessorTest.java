package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.*;

import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class RefusalProcessorTest {

  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private UacQidLinkRepository uacQidLinkRepository;

  @Mock private CaseRepository caseRepository;

  @Mock private CaseProcessor caseProcessor;

  @Mock private EventLogger eventLogger;

  @InjectMocks RefusalProcessor underTest;

  @Test
  public void shouldProcessARefusalReceivedMessageSuccessfully() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementRefusalEvent();
    CollectionCase collectionCase = managementEvent.getPayload().getRefusal().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID.toString());
    collectionCase.setRefusalReceived(false);
    Case testCase = getRandomCase();
    RefusalDTO expectedRefusal = managementEvent.getPayload().getRefusal();

    when(caseProcessor.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);

    // WHEN
    underTest.processRefusal(managementEvent);

    // THEN
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository).saveAndFlush(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();

    assertThat(actualCase.isRefusalReceived()).isTrue();

    verify(caseProcessor, times(1)).emitCaseUpdatedEvent(testCase);
    verify(eventLogger, times(1))
        .logRefusalEvent(
            testCase,
            REFUSAL_RECEIVED,
            EventType.REFUSAL_RECEIVED,
            expectedRefusal,
            managementEvent.getEvent());
  }
}
