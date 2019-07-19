package uk.gov.ons.census.casesvc.service;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.service.ReceiptProcessor.QID_RECEIPTED;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
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
import uk.gov.ons.census.casesvc.model.dto.Receipt;
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
    CaseRepository caseRepository = mock(CaseRepository.class);
    UacQidLinkRepository uacQidLinkRepository = mock(UacQidLinkRepository.class);

    // Given
    UacQidLink expectedUacQidLink = getUacQidLink();
    Case expectedCase = getRandomCase();
    expectedUacQidLink.setCaze(expectedCase);

    when(uacQidLinkRepository.findByQid(TEST_QID)).thenReturn(Optional.of(expectedUacQidLink));

    UacProcessor uacProcessor = mock(UacProcessor.class);
    CaseProcessor caseProcessor = mock(CaseProcessor.class);
    PayloadDTO payloadDTO = new PayloadDTO();

    when(uacProcessor.emitUacUpdatedEvent(any(UacQidLink.class), any(Case.class), anyBoolean()))
        .thenReturn(payloadDTO);

    Map<String, String> headers = createTestHeaders();

    // when
    Receipt receipt = new Receipt();
    receipt.setQuestionnaire_Id(TEST_QID);

    String dateTime = "2016-03-04T11:30Z";
    OffsetDateTime expectedReceiptDateTime = OffsetDateTime.parse(dateTime);
    receipt.setResponseDateTime(expectedReceiptDateTime);

    ReceiptProcessor receiptProcessor =
        new ReceiptProcessor(
            caseProcessor, uacQidLinkRepository, caseRepository, uacProcessor, eventLogger);
    receiptProcessor.processReceipt(receipt, headers);

    // then
    verify(uacProcessor, times(1)).emitUacUpdatedEvent(expectedUacQidLink, expectedCase, false);
    verify(eventLogger, times(1))
        .logReceiptEvent(
            expectedUacQidLink,
            QID_RECEIPTED,
            EventType.UAC_UPDATED,
            receipt,
            headers,
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
    Receipt receipt = new Receipt();
    receipt.setQuestionnaire_Id(TEST_QUESTIONNAIRE_ID);

    ReceiptProcessor receiptProcessor =
        new ReceiptProcessor(
            caseProcessor, uacQidLinkRepository, caseRepository, uacProcessor, eventLogger);
    receiptProcessor.processReceipt(receipt, createTestHeaders());

    // Then
    // Expected Exception is raised

  }

  private UacQidLink getUacQidLink() {
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setCaze(null);
    uacQidLink.setUac(TEST_UAC);
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setQid(TEST_QID);
    return uacQidLink;
  }

  private Case getRandomCase() {
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);

    return caze;
  }

  private Map<String, String> createTestHeaders() {
    Map<String, String> headers = new HashMap<>();

    headers.put("channel", "any receipt channel");
    headers.put("source", "any receipt source");

    return headers;
  }
}
