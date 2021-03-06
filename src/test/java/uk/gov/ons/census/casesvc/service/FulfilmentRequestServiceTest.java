package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
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
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_VIA_EQ_ENGLAND_SMS =
      "UACITA1";
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_VIA_EQ_WALES_ENGLISH_SMS =
      "UACITA2B";
  private static final String
      HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_VIA_EQ_NORTHERN_IRELAND_SMS = "UACITA4";

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
    expectedFulfilmentRequest.setCaseId(expectedCase.getCaseId());

    when(caseService.getCaseByCaseId(expectedFulfilmentRequest.getCaseId()))
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
            any(),
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
  public void testGoodIndividualResponseFulfilmentRequestForUACITA1() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_VIA_EQ_ENGLAND_SMS);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACITA2B() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_VIA_EQ_WALES_ENGLISH_SMS);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACITA4() {
    testIndividualResponseCode(
        HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_VIA_EQ_NORTHERN_IRELAND_SMS);
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
  public void tesIndividualResponseFulfilmentDuplicateCaseIdBlowsUp() {
    testIndividualResponseCodeDuplicateIndivdualCaseId(
        HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_PRINT);
  }

  @Test
  public void testIndividalResponseFulfilmentRequestForNonHHIsJustLogged() {
    // Given
    Case parentCase = getRandomCase();
    parentCase.setUacQidLinks(new ArrayList<>());
    parentCase.setEvents(new ArrayList<>());
    parentCase.setCaseType("SPG");

    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    managementEvent.getPayload().getFulfilmentRequest().setCaseId(parentCase.getCaseId());
    managementEvent
        .getPayload()
        .getFulfilmentRequest()
        .setFulfilmentCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_PRINT);
    managementEvent.getPayload().getFulfilmentRequest().setIndividualCaseId(UUID.randomUUID());

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
            any(),
            eq(messageTimestamp));
  }

  private void testIndividualResponseCodePrinter(String individualResponseCode) {
    // Given
    Case parentCase = getRandomCase();
    parentCase.setUacQidLinks(new ArrayList<>());
    parentCase.setEvents(new ArrayList<>());
    parentCase.setCaseType("HH");

    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    managementEvent.getPayload().getFulfilmentRequest().setCaseId(parentCase.getCaseId());
    managementEvent.getPayload().getFulfilmentRequest().setFulfilmentCode(individualResponseCode);
    managementEvent.getPayload().getFulfilmentRequest().setIndividualCaseId(UUID.randomUUID());

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(eq(parentCase.getCaseId()))).thenReturn(parentCase);

    UUID childCaseId = managementEvent.getPayload().getFulfilmentRequest().getIndividualCaseId();

    Case childCase = new Case();
    when(caseService.prepareIndividualResponseCaseFromParentCase(
            parentCase, childCaseId, managementEvent.getEvent().getChannel()))
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
            any(),
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
    expectedFulfilmentRequest.setCaseId(parentCase.getCaseId());
    expectedFulfilmentRequest.setFulfilmentCode(individualResponseCode);
    expectedFulfilmentRequest.setIndividualCaseId(UUID.randomUUID());
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(expectedFulfilmentRequest.getCaseId())).thenReturn(parentCase);

    UUID childCaseId = managementEvent.getPayload().getFulfilmentRequest().getIndividualCaseId();

    Case childCase = new Case();
    when(caseService.prepareIndividualResponseCaseFromParentCase(
            parentCase, childCaseId, managementEvent.getEvent().getChannel()))
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
            any(),
            eq(messageTimestamp));

    verify(caseService).emitCaseCreatedEvent(childCase);
  }

  private void testIndividualResponseCodeDuplicateIndivdualCaseId(String individualResponseCode) {
    // Given
    Case parentCase = getRandomCase();
    parentCase.setUacQidLinks(new ArrayList<>());
    parentCase.setEvents(new ArrayList<>());
    parentCase.setCaseType("HH");

    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();
    expectedFulfilmentRequest.setCaseId(parentCase.getCaseId());
    expectedFulfilmentRequest.setFulfilmentCode(individualResponseCode);
    expectedFulfilmentRequest.setIndividualCaseId(UUID.randomUUID());
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(expectedFulfilmentRequest.getCaseId())).thenReturn(parentCase);
    when(caseService.checkIfCaseIdExists(any())).thenReturn(true);

    UUID childCaseId = managementEvent.getPayload().getFulfilmentRequest().getIndividualCaseId();

    // when
    try {
      underTest.processFulfilmentRequest(managementEvent, messageTimestamp);
      fail("Expected exception not thrown");
    } catch (RuntimeException runtimeException) {
      if (!runtimeException
          .getMessage()
          .equals("Individual case ID " + childCaseId + " already present in database")) {
        fail("Unexpected exception thrown");
      }
    }

    // then
    verify(eventLogger, never())
        .logCaseEvent(
            eq(parentCase),
            eq(managementEvent.getEvent().getDateTime()),
            eq("Fulfilment Request Received"),
            eq(FULFILMENT_REQUESTED),
            eq(managementEvent.getEvent()),
            any(),
            eq(messageTimestamp));

    verify(caseService, never()).emitCaseCreatedEvent(any());
  }
}
