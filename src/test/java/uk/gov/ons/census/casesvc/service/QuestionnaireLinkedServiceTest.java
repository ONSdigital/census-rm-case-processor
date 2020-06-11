package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
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
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class QuestionnaireLinkedServiceTest {

  private final UUID TEST_CASE_ID_1 = UUID.randomUUID();
  private final UUID TEST_CASE_ID_2 = UUID.randomUUID();
  private final UUID TEST_INDIVIDUAL_CASE_ID = UUID.randomUUID();
  private final String TEST_HH_QID = "0112345678901234";
  private final String TEST_HI_QID = "2112345678901234";
  private final String TEST_NON_CCS_QID_ID = "1234567890123456";

  @Mock UacService uacService;

  @Mock CaseService caseService;

  @Mock EventLogger eventLogger;

  @Mock CaseReceiptService caseReceiptService;

  @Mock BlankQuestionnaireService blankQuestionnaireService;

  @InjectMocks QuestionnaireLinkedService underTest;

  @Test
  public void testQuestionnaireLinkedForUnreceiptedCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HH_QID);

    Case testCase = getRandomCaseWithUacQidLinks(1);
    testCase.setCaseId(TEST_CASE_ID_1);
    testCase.setSurvey("CENSUS");

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setActive(true);
    testUacQidLink.setQid(TEST_NON_CCS_QID_ID);
    testUacQidLink.setCaze(null);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();
    testUacQidLink.setCcsCase(false);

    when(uacService.findByQid(TEST_HH_QID)).thenReturn(testUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testCase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent, messageTimestamp);

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
    assertThat(actualUacQidLink.isCcsCase()).isFalse();
    assertThat(actualUacQidLink.getCaze().getSurvey()).isEqualTo("CENSUS");
    verifyNoMoreInteractions(uacService);

    verify(eventLogger)
        .logUacQidEvent(
            eq(testUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testQuestionnaireLinkedBeforeCaseReceipted() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HH_QID);

    Case testCase = getRandomCaseWithUacQidLinks(1);
    testCase.setCaseId(TEST_CASE_ID_1);
    testCase.setReceiptReceived(false);
    testCase.setSurvey("CENSUS");

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setQid(TEST_HI_QID);
    testUacQidLink.setActive(false);
    testUacQidLink.setCaze(null);
    testUacQidLink.setCcsCase(false);
    testUacQidLink.setBlankQuestionnaire(false);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(uacService.findByQid(TEST_HH_QID)).thenReturn(testUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testCase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent, messageTimestamp);

    // THEN
    InOrder inOrder = inOrder(uacService, caseService, caseReceiptService, eventLogger);
    inOrder.verify(uacService).findByQid(TEST_HH_QID);
    inOrder.verify(caseService).getCaseByCaseId(TEST_CASE_ID_1);
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder
        .verify(caseReceiptService)
        .receiptCase(uacQidLinkArgumentCaptor.capture(), eq(managementEvent.getEvent()));
    assertThat(uacQidLinkArgumentCaptor.getValue().getCaze().getCaseId()).isEqualTo(TEST_CASE_ID_1);
    assertThat(uacQidLinkArgumentCaptor.getValue().getQid()).isEqualTo(TEST_HI_QID);
    verifyNoMoreInteractions(caseReceiptService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getCaze()).isEqualTo(testCase);
    assertThat(actualUacQidLink.isActive()).isFalse();
    assertThat(actualUacQidLink.isCcsCase()).isFalse();
    verifyNoMoreInteractions(uacService);

    verify(eventLogger)
        .logUacQidEvent(
            eq(testUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testIndividualQuestionnaireLinkedForCase() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HI_QID);
    uac.setIndividualCaseId(TEST_INDIVIDUAL_CASE_ID.toString());

    Case testHHCase = getRandomCase();
    testHHCase.setCaseId(TEST_CASE_ID_1);
    testHHCase.setReceiptReceived(false);
    testHHCase.setSurvey("CENSUS");
    testHHCase.setCaseType("HH");

    Case testHICase = getRandomCaseWithUacQidLinks(1);
    testHICase.setCaseId(TEST_INDIVIDUAL_CASE_ID);
    testHHCase.setReceiptReceived(false);
    testHICase.setSurvey("CENSUS");

    UacQidLink testHIUacQidLink = testHICase.getUacQidLinks().get(0);
    testHIUacQidLink.setQid(TEST_HI_QID);
    testHIUacQidLink.setActive(true);
    testHIUacQidLink.setCaze(null);
    testHIUacQidLink.setCcsCase(false);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(uacService.findByQid(TEST_HI_QID)).thenReturn(testHIUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testHHCase);
    when(caseService.prepareIndividualResponseCaseFromParentCase(any(), any()))
        .thenReturn(testHICase);
    when(caseService.saveNewCaseAndStampCaseRef(testHICase)).thenReturn(testHICase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent, messageTimestamp);

    // THEN
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);
    inOrder.verify(uacService).findByQid(TEST_HI_QID);

    inOrder.verify(caseService).getCaseByCaseId(TEST_CASE_ID_1);
    inOrder
        .verify(caseService)
        .prepareIndividualResponseCaseFromParentCase(eq(testHHCase), any(UUID.class));
    inOrder.verify(caseService).saveNewCaseAndStampCaseRef(testHICase);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    inOrder.verify(caseService).emitCaseCreatedEvent(caseCaptor.capture());
    Case actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(TEST_INDIVIDUAL_CASE_ID);
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getCaze()).isEqualTo(testHICase);
    assertThat(actualUacQidLink.isCcsCase()).isFalse();
    assertThat(actualUacQidLink.getCaze().getSurvey()).isEqualTo("CENSUS");
    verifyNoMoreInteractions(uacService);

    verify(eventLogger)
        .logUacQidEvent(
            eq(testHIUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));

    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testQidAlreadyLinkedToDifferentCaseCanBeReLinked() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HH_QID);

    Case testCase = getRandomCase();
    testCase.setCaseId(UUID.randomUUID());

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(TEST_HH_QID);
    uacQidLink.setCaze(testCase);

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(uacService.findByQid(TEST_HH_QID)).thenReturn(uacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testCase);

    // When
    underTest.processQuestionnaireLinked(managementEvent, messageTimestamp);

    // Then
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);

    inOrder.verify(uacService).findByQid(anyString());

    inOrder.verify(caseService).getCaseByCaseId(any(UUID.class));
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(uacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(uacQidLink.getUac());
    verifyNoMoreInteractions(uacService);

    verify(eventLogger)
        .logUacQidEvent(
            eq(uacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));

    verify(eventLogger)
        .logCaseEvent(
            eq(testCase),
            any(OffsetDateTime.class),
            eq("Questionnaire unlinked from case with QID " + TEST_HH_QID),
            eq(EventType.QUESTIONNAIRE_UNLINKED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));

    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testGoodIndividualQuestionnaireLinkedWhereSameCaseAndQidAlreadyLinked() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HI_QID);
    uac.setIndividualCaseId(TEST_INDIVIDUAL_CASE_ID.toString());

    Case testHHCase = getRandomCase();
    testHHCase.setCaseId(TEST_CASE_ID_1);
    testHHCase.setReceiptReceived(false);
    testHHCase.setCaseType("HH");

    Case testHICase = getRandomCaseWithUacQidLinks(1);
    testHICase.setCaseId(TEST_CASE_ID_2);
    testHHCase.setReceiptReceived(false);

    UacQidLink testHIUacQidLink = testHICase.getUacQidLinks().get(0);
    testHIUacQidLink.setQid(TEST_HI_QID);
    testHIUacQidLink.setActive(true);
    testHIUacQidLink.setCaze(testHHCase);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(uacService.findByQid(TEST_HI_QID)).thenReturn(testHIUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testHHCase);
    when(caseService.prepareIndividualResponseCaseFromParentCase(any(), any()))
        .thenReturn(testHICase);
    when(caseService.saveNewCaseAndStampCaseRef(any())).thenReturn(testHICase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent, messageTimestamp);

    // THEN
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);
    inOrder.verify(uacService).findByQid(TEST_HI_QID);
    inOrder.verify(caseService).getCaseByCaseId(TEST_CASE_ID_1);
    inOrder
        .verify(caseService)
        .prepareIndividualResponseCaseFromParentCase(eq(testHHCase), any(UUID.class));
    inOrder.verify(caseService).saveNewCaseAndStampCaseRef(testHICase);
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
            anyString(),
            eq(messageTimestamp));

    verifyNoMoreInteractions(caseService);
    verifyNoMoreInteractions(uacService);
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testQuestionnaireLinkedForUnreceiptedCaseReceiptedUacQid() {
    // GIVEN
    Case testCase = getRandomCaseWithUacQidLinks(1);
    testCase.setCaseId(TEST_CASE_ID_1);
    testCase.setSurvey("CENSUS");
    testCase.setReceiptReceived(false);

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setActive(false);
    testUacQidLink.setQid(TEST_NON_CCS_QID_ID);
    testUacQidLink.setCaze(null);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();
    testUacQidLink.setCcsCase(false);
    testUacQidLink.setBlankQuestionnaire(false);

    ResponseManagementEvent linkingEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    UacDTO uac = linkingEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HH_QID);

    when(uacService.findByQid(TEST_HH_QID)).thenReturn(testUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testCase);

    // WHEN
    underTest.processQuestionnaireLinked(linkingEvent, messageTimestamp);

    // THEN
    InOrder inOrder = inOrder(uacService, caseService, caseReceiptService, eventLogger);

    inOrder.verify(uacService).findByQid(anyString());

    inOrder.verify(caseService).getCaseByCaseId(any(UUID.class));
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(caseReceiptService)
        .receiptCase(uacQidLinkArgumentCaptor.capture(), eq(linkingEvent.getEvent()));
    assertThat(uacQidLinkArgumentCaptor.getValue().getCaze().getCaseId())
        .as("CaseReceipter receiptHandler Case Id")
        .isEqualTo(TEST_CASE_ID_1);
    assertThat(uacQidLinkArgumentCaptor.getValue().getId())
        .as("CaseReceipter uacQidLink Qid Id")
        .isEqualTo(testUacQidLink.getId());

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(testUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(testUacQidLink.getUac());
    assertThat(actualUacQidLink.isCcsCase()).isFalse();
    assertThat(actualUacQidLink.getCaze().getSurvey()).isEqualTo("CENSUS");
    verifyNoMoreInteractions(uacService);

    verify(eventLogger)
        .logUacQidEvent(
            eq(testUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(linkingEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testLinkingAnActiveQidToAnUnreceiptedCaseDoesntReceipt() {
    // GIVEN
    Case testCase = getRandomCaseWithUacQidLinks(1);
    testCase.setCaseId(TEST_CASE_ID_1);
    testCase.setSurvey("CENSUS");
    testCase.setReceiptReceived(false);

    UacQidLink testUacQidLink = testCase.getUacQidLinks().get(0);
    testUacQidLink.setActive(true);
    testUacQidLink.setQid(TEST_NON_CCS_QID_ID);
    testUacQidLink.setCaze(null);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();
    testUacQidLink.setCcsCase(false);

    ResponseManagementEvent linkingEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    UacDTO uac = linkingEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HH_QID);

    when(uacService.findByQid(TEST_HH_QID)).thenReturn(testUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testCase);

    // WHEN
    underTest.processQuestionnaireLinked(linkingEvent, messageTimestamp);

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
    assertThat(actualUacQidLink.isCcsCase()).isFalse();
    assertThat(actualUacQidLink.getCaze().getSurvey()).isEqualTo("CENSUS");
    verifyNoMoreInteractions(uacService);

    verifyZeroInteractions(caseReceiptService);
  }

  @Test
  public void testLinkingToQidMarkedBlank() {
    // GIVEN
    Case testCase = getRandomCase();
    testCase.setCaseId(TEST_CASE_ID_1);
    testCase.setSurvey("CENSUS");
    testCase.setReceiptReceived(false);
    testCase.setUacQidLinks(null);

    UacQidLink testUacQidLink = new UacQidLink();
    testUacQidLink.setBlankQuestionnaire(true);
    testUacQidLink.setActive(false);
    testUacQidLink.setQid(TEST_NON_CCS_QID_ID);
    testUacQidLink.setCaze(null);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();
    testUacQidLink.setCcsCase(false);

    ResponseManagementEvent linkingEvent = getTestResponseManagementQuestionnaireLinkedEvent();
    UacDTO uac = linkingEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HH_QID);

    when(uacService.findByQid(TEST_HH_QID)).thenReturn(testUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testCase);

    // WHEN
    underTest.processQuestionnaireLinked(linkingEvent, messageTimestamp);

    // THEN

    verify(uacService).findByQid(anyString());

    verify(caseService).getCaseByCaseId(eq(testCase.getCaseId()));
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(testUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(testUacQidLink.getUac());
    assertThat(actualUacQidLink.isCcsCase()).isFalse();
    assertThat(actualUacQidLink.getCaze().getSurvey()).isEqualTo("CENSUS");
    verifyNoMoreInteractions(uacService);

    verify(blankQuestionnaireService)
        .handleBlankQuestionnaire(
            eq(testCase), eq(testUacQidLink), eq(EventTypeDTO.QUESTIONNAIRE_LINKED));
  }

  @Test
  public void testIndCaseIdIsIgnoredWhenLinkingToCeParentCase() {
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HI_QID);
    uac.setIndividualCaseId(TEST_INDIVIDUAL_CASE_ID.toString());

    Case testCase = getRandomCase();
    testCase.setCaseId(TEST_CASE_ID_1);
    testCase.setReceiptReceived(false);
    testCase.setSurvey("CENSUS");
    testCase.setCaseType("CE");

    UacQidLink testUacQidLink = new UacQidLink();
    testUacQidLink.setActive(true);
    testUacQidLink.setQid(TEST_HI_QID);
    testUacQidLink.setCaze(null);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();
    testUacQidLink.setCcsCase(false);

    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testCase);
    when(uacService.findByQid(TEST_HI_QID)).thenReturn(testUacQidLink);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent, messageTimestamp);

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
    assertThat(actualUacQidLink.isCcsCase()).isFalse();
    assertThat(actualUacQidLink.getCaze().getSurvey()).isEqualTo("CENSUS");
    verifyNoMoreInteractions(uacService);

    verify(eventLogger)
        .logUacQidEvent(
            eq(testUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testIndQIDAndCaseTypeHHButNoIndCaseIdProvided() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementQuestionnaireLinkedEvent();

    UacDTO uac = managementEvent.getPayload().getUac();
    uac.setCaseId(TEST_CASE_ID_1.toString());
    uac.setQuestionnaireId(TEST_HI_QID);
    uac.setIndividualCaseId(null);

    Case testHHCase = getRandomCase();
    testHHCase.setCaseId(TEST_CASE_ID_1);
    testHHCase.setReceiptReceived(false);
    testHHCase.setSurvey("CENSUS");
    testHHCase.setCaseType("HH");

    Case testHICase = getRandomCaseWithUacQidLinks(1);
    testHICase.setCaseId(TEST_INDIVIDUAL_CASE_ID);
    testHHCase.setReceiptReceived(false);
    testHICase.setSurvey("CENSUS");

    UacQidLink testHIUacQidLink = testHICase.getUacQidLinks().get(0);
    testHIUacQidLink.setQid(TEST_HI_QID);
    testHIUacQidLink.setActive(true);
    testHIUacQidLink.setCaze(null);
    testHIUacQidLink.setCcsCase(false);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(uacService.findByQid(TEST_HI_QID)).thenReturn(testHIUacQidLink);
    when(caseService.getCaseByCaseId(TEST_CASE_ID_1)).thenReturn(testHHCase);
    when(caseService.prepareIndividualResponseCaseFromParentCase(any(), any()))
        .thenReturn(testHICase);
    when(caseService.saveNewCaseAndStampCaseRef(testHICase)).thenReturn(testHICase);

    // WHEN
    underTest.processQuestionnaireLinked(managementEvent, messageTimestamp);

    // THEN
    InOrder inOrder = inOrder(uacService, caseService, eventLogger);
    inOrder.verify(uacService).findByQid(TEST_HI_QID);

    inOrder.verify(caseService).getCaseByCaseId(TEST_CASE_ID_1);
    inOrder
        .verify(caseService)
        .prepareIndividualResponseCaseFromParentCase(eq(testHHCase), any(UUID.class));
    inOrder.verify(caseService).saveNewCaseAndStampCaseRef(testHICase);

    ArgumentCaptor<Case> caseCaptor = ArgumentCaptor.forClass(Case.class);
    inOrder.verify(caseService).emitCaseCreatedEvent(caseCaptor.capture());
    Case actualCase = caseCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(TEST_INDIVIDUAL_CASE_ID);
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    inOrder.verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getCaze()).isEqualTo(testHICase);
    assertThat(actualUacQidLink.isCcsCase()).isFalse();
    assertThat(actualUacQidLink.getCaze().getSurvey()).isEqualTo("CENSUS");
    verifyNoMoreInteractions(uacService);

    verify(eventLogger)
        .logUacQidEvent(
            eq(testHIUacQidLink),
            any(OffsetDateTime.class),
            eq("Questionnaire Linked"),
            eq(EventType.QUESTIONNAIRE_LINKED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));

    verifyNoMoreInteractions(eventLogger);
  }
}
