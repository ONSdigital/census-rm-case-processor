package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;

@RunWith(MockitoJUnitRunner.class)
public class FulfilmentRequestServiceTest {
  private static final String RM_HOUSEHOLD_INDIVIDUAL_TELEPHONE_CAPTURE = "RM_TC_HI";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_SMS = "UACIT1";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_SMS = "UACIT2";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_SMS = "UACIT2W";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_SMS = "UACIT4";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_PRINT = "P_OR_I1";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_PRINT = "P_OR_I2";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_PRINT = "P_OR_I2W";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_PRINT = "P_OR_I4";
  private static final String INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_ENGLAND_LETTER = "P_LP_ILP1";
  private static final String INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_WALES_ENGLISH_LETTER =
      "P_LP_ILP2";
  private static final String INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_WALES_WELSH_LETTER =
      "P_LP_ILP2W";
  private static final String INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_NI_LETTER = "P_LP_IL4";

  @Mock private EventLogger eventLogger;

  @Mock private CaseService caseService;

  @Mock private UacService uacService;

  @InjectMocks FulfilmentRequestService underTest;

  @Test
  public void testGoodFulfilmentRequest() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    Case expectedCase = getRandomCase();
    expectedFulfilmentRequest.setCaseId(expectedCase.getCaseId().toString());

    when(caseService.getCaseByCaseId(UUID.fromString(expectedFulfilmentRequest.getCaseId())))
        .thenReturn(expectedCase);

    // when
    underTest.processFulfilmentRequest(managementEvent, messageTimestamp);

    // then
    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(expectedCase),
            eq(managementEvent.getEvent().getDateTime()),
            eq("Fulfilment Request Received"),
            eq(FULFILMENT_REQUESTED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT1() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_SMS);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT2() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_SMS);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT2W() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_SMS);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT4() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_SMS);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForP_OR_I1() {
    testIndividualResponseCodePrinter(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_PRINT);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForP_OR_I2() {
    testIndividualResponseCodePrinter(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_PRINT);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForP_OR_I2W() {
    testIndividualResponseCodePrinter(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_PRINT);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForP_OR_I4() {
    testIndividualResponseCodePrinter(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_PRINT);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForRM_TC_HI() {
    testIndividualResponseCode(RM_HOUSEHOLD_INDIVIDUAL_TELEPHONE_CAPTURE);
  }

  @Test
  public void testGoodIndividualLargePrintQuestionnaireFulfilmentRequestForP_LP_ILP1() {
    testIndividualResponseCodePrinter(INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_ENGLAND_LETTER);
  }

  @Test
  public void testGoodIndividualLargePrintQuestionnaireFulfilmentRequestForP_LP_ILP2() {
    testIndividualResponseCodePrinter(INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_WALES_ENGLISH_LETTER);
  }

  @Test
  public void testGoodIndividualLargePrintQuestionnaireFulfilmentRequestForP_LP_ILP2W() {
    testIndividualResponseCodePrinter(INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_WALES_WELSH_LETTER);
  }

  @Test
  public void testGoodIndividualLargePrintQuestionnaireFulfilmentRequestForP_LP_IL4() {
    testIndividualResponseCodePrinter(INDIVIDUAL_QUESTIONNAIRE_LARGE_PRINT_NI_LETTER);
  }

  @Test
  public void testIndividalResponseFulfilmentRequestForNonHHIsJustLogged() {
    // Given
    Case parentCase = getRandomCase();
    parentCase.setUacQidLinks(new ArrayList<>());
    parentCase.setEvents(new ArrayList<>());
    parentCase.setCaseType("SPG");

    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setCaseId(parentCase.getCaseId().toString());
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setFulfilmentCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_PRINT);
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setIndividualCaseId(UUID.randomUUID().toString());

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(eq(parentCase.getCaseId()))).thenReturn(parentCase);

    // when
    underTest.processFulfilmentRequest(managementEvent, messageTimestamp);

    verify(caseService).getCaseByCaseId(parentCase.getCaseId());
    verifyNoMoreInteractions(caseService);

    // then
    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(parentCase),
            eq(managementEvent.getEvent().getDateTime()),
            eq("Fulfilment Request Received"),
            eq(FULFILMENT_REQUESTED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
  }

  private void testIndividualResponseCodePrinter(String individualResponseCode) {
    // Given
    Case parentCase = getRandomCase();
    parentCase.setUacQidLinks(new ArrayList<>());
    parentCase.setEvents(new ArrayList<>());
    parentCase.setCaseType("HH");

    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setCaseId(parentCase.getCaseId().toString());
    managementEvent.getPayload().getFulfilmentRequest().setFulfilmentCode(individualResponseCode);
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setIndividualCaseId(UUID.randomUUID().toString());

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(eq(parentCase.getCaseId()))).thenReturn(parentCase);

    UUID childCaseId =
        UUID.fromString(managementEvent.getPayload().getFulfilmentRequest().getIndividualCaseId());

    Case childCase = new Case();
    when(caseService.prepareIndividualResponseCaseFromParentCase(parentCase, childCaseId))
        .thenReturn(childCase);
    when(caseService.saveNewCaseAndStampCaseRef(childCase)).thenReturn(childCase);

    // when
    underTest.processFulfilmentRequest(managementEvent, messageTimestamp);

    // then
    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(parentCase),
            eq(managementEvent.getEvent().getDateTime()),
            eq("Fulfilment Request Received"),
            eq(FULFILMENT_REQUESTED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));

    ArgumentCaptor<FulfilmentRequestDTO> fulfilmentRequestArgumentCaptor =
        ArgumentCaptor.forClass(FulfilmentRequestDTO.class);
    verify(caseService)
        .emitCaseCreatedEvent(eq(childCase), fulfilmentRequestArgumentCaptor.capture());
    assertThat(fulfilmentRequestArgumentCaptor.getValue().getFulfilmentCode())
        .isEqualTo(individualResponseCode);
  }

  private void testIndividualResponseCode(String individualResponseCode) {
    // Given
    Case parentCase = getRandomCase();
    parentCase.setUacQidLinks(new ArrayList<>());
    parentCase.setEvents(new ArrayList<>());
    parentCase.setCaseType("HH");

    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();
    expectedFulfilmentRequest.setCaseId(parentCase.getCaseId().toString());
    expectedFulfilmentRequest.setFulfilmentCode(individualResponseCode);
    expectedFulfilmentRequest.setIndividualCaseId(UUID.randomUUID().toString());
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(UUID.fromString(expectedFulfilmentRequest.getCaseId())))
        .thenReturn(parentCase);

    UUID childCaseId =
        UUID.fromString(managementEvent.getPayload().getFulfilmentRequest().getIndividualCaseId());

    Case childCase = new Case();
    when(caseService.prepareIndividualResponseCaseFromParentCase(parentCase, childCaseId))
        .thenReturn(childCase);
    when(caseService.saveNewCaseAndStampCaseRef(childCase)).thenReturn(childCase);

    // when
    underTest.processFulfilmentRequest(managementEvent, messageTimestamp);

    // then
    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(parentCase),
            eq(managementEvent.getEvent().getDateTime()),
            eq("Fulfilment Request Received"),
            eq(FULFILMENT_REQUESTED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));

    verify(caseService).emitCaseCreatedEvent(childCase);
  }
}
