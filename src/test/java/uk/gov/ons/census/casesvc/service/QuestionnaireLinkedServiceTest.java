package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCaseWithUacQidLinks;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementQuestionnaireLinkedEvent;

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
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class QuestionnaireLinkedServiceTest {

  private final UUID TEST_CASE_ID_1 = UUID.randomUUID();
  private final UUID TEST_CASE_ID_2 = UUID.randomUUID();
  private final String TEST_HH_QID = "0112345678901234";
  private final String TEST_HI_QID = "2112345678901234";
  private final String TEST_NON_CCS_QID_ID = "1234567890123456";
  private final String TEST_CCS_QID_ID = "7134567890123456";

  @Mock UacService uacService;

  @Mock CaseService caseService;

  @Mock EventLogger eventLogger;

  @InjectMocks QuestionnaireLinkedService underTest;

  @Test
  public void testQuestionnaireLinkedForUnreceiptedNonCCSCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HH_QID);

    Case testCase = getRandomCaseWithUacQidLinks(1);
    testCase.setCaseId(TEST_CASE_ID_1);
    testCase.setCcsCase(false);

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setActive(true);
    testUacQidLink.setQid(TEST_NON_CCS_QID_ID);
    testUacQidLink.setCaze(null);

    when(uacService.findByQid(TEST_HH_QID)).thenReturn(testUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testCase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent);

    // THEN
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);

    inOrder.verify(uacService).findByQid(anyString());

    inOrder.verify(caseService).getCaseByCaseId(any(UUID.class));
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(testUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(testUacQidLink.getUac());
    verifyNoMoreInteractions(uacService);

    verify(eventLogger)
        .logUacQidEvent(
            eq(testUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString());
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testQuestionnaireLinkedForUnreceiptedCCSCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HH_QID);

    Case testCase = getRandomCaseWithUacQidLinks(1);
    testCase.setCaseId(TEST_CASE_ID_1);
    testCase.setCcsCase(true);

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setActive(true);
    testUacQidLink.setQid(TEST_CCS_QID_ID);
    testUacQidLink.setCaze(null);

    when(uacService.findByQid(TEST_HH_QID)).thenReturn(testUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testCase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent);

    // THEN
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);

    inOrder.verify(uacService).findByQid(anyString());

    inOrder.verify(caseService).getCaseByCaseId(any(UUID.class));
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveUacQidLink(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(testUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(testUacQidLink.getUac());
    verifyNoMoreInteractions(uacService);

    inOrder
        .verify(eventLogger)
        .logUacQidEvent(
            eq(testUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString());
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testQuestionnaireLinkedBeforeNonCCSCaseReceipted() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HH_QID);

    Case testCase = getRandomCaseWithUacQidLinks(1);
    testCase.setCaseId(TEST_CASE_ID_1);
    testCase.setReceiptReceived(false);
    testCase.setCcsCase(false);

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setQid(TEST_HI_QID);
    testUacQidLink.setActive(false);
    testUacQidLink.setCaze(null);

    when(uacService.findByQid(TEST_HH_QID)).thenReturn(testUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testCase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent);

    // THEN
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);
    inOrder.verify(uacService).findByQid(TEST_HH_QID);
    inOrder.verify(caseService).getCaseByCaseId(TEST_CASE_ID_1);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    inOrder.verify(caseService).saveAndEmitCaseUpdatedEvent(caseCaptor.capture());
    Case actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(TEST_CASE_ID_1);
    assertThat(actualCase.isReceiptReceived()).isTrue();
    assertThat(actualCase.isCcsCase()).isFalse();
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getCaze()).isEqualTo(testCase);
    assertThat(actualUacQidLink.isActive()).isFalse();
    verifyNoMoreInteractions(uacService);

    verify(eventLogger)
        .logUacQidEvent(
            eq(testUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString());
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testQuestionnaireLinkedBeforeCCSCaseReceipted() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HH_QID);

    Case testCase = getRandomCaseWithUacQidLinks(1);
    testCase.setCaseId(TEST_CASE_ID_1);
    testCase.setReceiptReceived(false);
    testCase.setCcsCase(true);

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setQid(TEST_CCS_QID_ID);
    testUacQidLink.setActive(false);
    testUacQidLink.setCaze(null);

    when(uacService.findByQid(TEST_HH_QID)).thenReturn(testUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testCase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent);

    // THEN
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);
    inOrder.verify(uacService).findByQid(TEST_HH_QID);
    inOrder.verify(caseService).getCaseByCaseId(TEST_CASE_ID_1);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    inOrder.verify(caseService).saveCase(caseCaptor.capture());
    Case actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(TEST_CASE_ID_1);
    assertThat(actualCase.isReceiptReceived()).isTrue();
    assertThat(actualCase.isCcsCase()).isTrue();
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveUacQidLink(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getCaze()).isEqualTo(testCase);
    assertThat(actualUacQidLink.isActive()).isFalse();
    verifyNoMoreInteractions(uacService);

    verify(eventLogger)
        .logUacQidEvent(
            eq(testUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString());
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testIndividualQuestionnaireLinkedForNonCCSCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HI_QID);

    Case testHHCase = getRandomCase();
    testHHCase.setCaseId(TEST_CASE_ID_1);
    testHHCase.setReceiptReceived(false);
    testHHCase.setCcsCase(false);

    Case testHICase = getRandomCaseWithUacQidLinks(1);
    testHICase.setCaseId(TEST_CASE_ID_2);
    testHHCase.setReceiptReceived(false);
    testHICase.setCcsCase(false);

    UacQidLink testHIUacQidLink = testHICase.getUacQidLinks().get(0);
    testHIUacQidLink.setQid(TEST_HI_QID);
    testHIUacQidLink.setActive(true);
    testHIUacQidLink.setCaze(null);

    when(uacService.findByQid(TEST_HI_QID)).thenReturn(testHIUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testHHCase);
    when(caseService.prepareIndividualResponseCaseFromParentCase(testHHCase))
        .thenReturn(testHICase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent);

    // THEN
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);
    inOrder.verify(uacService).findByQid(TEST_HI_QID);

    inOrder.verify(caseService).getCaseByCaseId(TEST_CASE_ID_1);
    inOrder.verify(caseService).prepareIndividualResponseCaseFromParentCase(testHHCase);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    inOrder.verify(caseService).emitCaseCreatedEvent(caseCaptor.capture());
    Case actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(TEST_CASE_ID_2);
    assertThat(actualCase.isCcsCase()).isFalse();
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getCaze()).isEqualTo(testHICase);
    verifyNoMoreInteractions(uacService);

    verify(eventLogger)
        .logUacQidEvent(
            eq(testHIUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString());

    verifyNoMoreInteractions(eventLogger);
  }

  @Test(expected = RuntimeException.class)
  public void testAlreadyLinkedToDifferentCaseUacQidThrowsException() {
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HI_QID);

    Case caze = getRandomCase();
    caze.setCaseId(UUID.randomUUID());

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(TEST_HI_QID);
    uacQidLink.setCaze(caze);

    when(uacService.findByQid(TEST_HI_QID)).thenReturn(uacQidLink);

    try {
      underTest.processQuestionnaireLinked(managementEvent);
    } catch (RuntimeException rte) {
      assertThat(rte.getMessage())
          .isEqualTo("UacQidLink already linked to case id: " + caze.getCaseId());
      throw rte;
    }
  }

  @Test
  public void testGoodIndividualQuestionnaireLinkedWhereSameCaseAndQidAlreadyLinked() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HI_QID);

    Case testHHCase = getRandomCase();
    testHHCase.setCaseId(TEST_CASE_ID_1);
    testHHCase.setReceiptReceived(false);

    Case testHICase = getRandomCaseWithUacQidLinks(1);
    testHICase.setCaseId(TEST_CASE_ID_2);
    testHHCase.setReceiptReceived(false);

    UacQidLink testHIUacQidLink = testHICase.getUacQidLinks().get(0);
    testHIUacQidLink.setQid(TEST_HI_QID);
    testHIUacQidLink.setActive(true);
    testHIUacQidLink.setCaze(testHHCase);

    when(uacService.findByQid(TEST_HI_QID)).thenReturn(testHIUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testHHCase);
    when(caseService.prepareIndividualResponseCaseFromParentCase(testHHCase))
        .thenReturn(testHICase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent);

    // THEN
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);
    inOrder.verify(uacService).findByQid(TEST_HI_QID);
    inOrder.verify(caseService).getCaseByCaseId(TEST_CASE_ID_1);
    inOrder.verify(caseService).prepareIndividualResponseCaseFromParentCase(testHHCase);
    inOrder.verify(caseService).emitCaseCreatedEvent(testHICase);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());

    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getCaze()).isEqualTo(testHICase);
    assertThat(actualUacQidLink.isActive()).isTrue();

    verify(eventLogger)
        .logUacQidEvent(
            eq(testHIUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString());

    verifyNoMoreInteractions(caseService);
    verifyNoMoreInteractions(uacService);
    verifyNoMoreInteractions(eventLogger);
  }
}
