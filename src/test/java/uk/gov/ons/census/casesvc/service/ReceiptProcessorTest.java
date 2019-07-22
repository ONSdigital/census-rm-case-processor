package uk.gov.ons.census.casesvc.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.service.ReceiptProcessor.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import java.util.Optional;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ReceiptDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class ReceiptProcessorTest {
  private static final UUID TEST_CASE_ID = UUID.randomUUID();
  private static final String TEST_QUESTIONNAIRE_ID = "123";
  private static final String TEST_QID = "test_qid";
  private static final String TEST_UAC = "test_uac";

  @Mock private CaseProcessor caseProcessor;

  @Mock private UacQidLinkRepository uacQidLinkRepository;

  @Mock private CaseRepository caseRepository;

  @Mock private UacProcessor uacProcessor;

  @Mock private EventLogger eventLogger;

  @InjectMocks UacProcessor underTest;

  @Test
  public void testGoodReceipt() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ReceiptDTO receipt = managementEvent.getPayload().getReceipt();
    CaseRepository caseRepository = mock(CaseRepository.class);
    UacQidLinkRepository uacQidLinkRepository = mock(UacQidLinkRepository.class);

    // Given
    Case expectedCase = getRandomCase();
    UacQidLink expectedUacQidLink = expectedCase.getUacQidLinks().get(0);
    expectedUacQidLink.setCaze(expectedCase);

    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(Optional.of(expectedUacQidLink));

    UacProcessor uacProcessor = mock(UacProcessor.class);
    CaseProcessor caseProcessor = mock(CaseProcessor.class);
    PayloadDTO payloadDTO = new PayloadDTO();

    when(uacProcessor.emitUacUpdatedEvent(any(UacQidLink.class), any(Case.class), anyBoolean()))
        .thenReturn(payloadDTO);

    // when
    ReceiptProcessor receiptProcessor =
        new ReceiptProcessor(
            caseProcessor, uacQidLinkRepository, caseRepository, uacProcessor, eventLogger);
    receiptProcessor.processReceipt(managementEvent);

    // then
    verify(uacProcessor, times(1)).emitUacUpdatedEvent(expectedUacQidLink, expectedCase, false);
    verify(eventLogger, times(1))
        .logEvent(
            expectedUacQidLink,
            QID_RECEIPTED,
            EventType.UAC_UPDATED,
            receipt,
            managementEvent.getEvent(),
            receipt.getResponseDateTime());
  }

  @Test(expected = RuntimeException.class)
  public void testReceiptedQidNotFound() {
    // Given
    CaseRepository caseRepository = mock(CaseRepository.class);
    CaseProcessor caseProcessor = mock(CaseProcessor.class);
    UacProcessor uacProcessor = mock(UacProcessor.class);
    UacQidLinkRepository uacQidLinkRepository = mock(UacQidLinkRepository.class);

    // Given
    ReceiptDTO receipt = new ReceiptDTO();
    receipt.setQuestionnaireId(TEST_QUESTIONNAIRE_ID);

    ReceiptProcessor receiptProcessor =
        new ReceiptProcessor(
            caseProcessor, uacQidLinkRepository, caseRepository, uacProcessor, eventLogger);
    receiptProcessor.processReceipt(new ResponseManagementEvent());

    // Then
    // Expected Exception is raised

  }

  private Case getRandomCase() {
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);

    return caze;
  }
}
