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
import uk.gov.ons.census.casesvc.model.entity.CaseState;

@RunWith(MockitoJUnitRunner.class)
public class FulfilmentRequestServiceTest {
  private static final String HOUSEHOLD_RESPONSE_ADDRESS_TYPE = "HH";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_ADDRESS_TYPE = "HI";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_SMS = "UACIT1";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_SMS = "UACIT2";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_SMS = "UACIT2W";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_SMS = "UACIT4";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_PRINT = "P_OR_I1";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_PRINT = "P_OR_I2";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_PRINT = "P_OR_I2W";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_PRINT = "P_OR_I4";

  @Mock private EventLogger eventLogger;

  @Mock private CaseService caseService;

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
    testIndividualResponseCodeSMS(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_SMS);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT2() {
    testIndividualResponseCodeSMS(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_SMS);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT2W() {
    testIndividualResponseCodeSMS(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_SMS);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT4() {
    testIndividualResponseCodeSMS(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_SMS);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForP_OR_I1() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND_PRINT);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForP_OR_I2() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH_PRINT);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForP_OR_I2W() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH_PRINT);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForP_OR_I4() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NI_PRINT);
  }

  private void testIndividualResponseCode(String individualResponseCode) {
    // Given
    Case parentCase = getRandomCase();
    parentCase.setUacQidLinks(new ArrayList<>());
    parentCase.setEvents(new ArrayList<>());
    parentCase.setCreatedDateTime(OffsetDateTime.now().minusDays(1));
    parentCase.setState(null);
    parentCase.setReceiptReceived(true);
    parentCase.setRefusalReceived(true);
    parentCase.setAddressType("HH");

    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();
    expectedFulfilmentRequest.setCaseId(parentCase.getCaseId().toString());
    expectedFulfilmentRequest.setFulfilmentCode(individualResponseCode);
    expectedFulfilmentRequest.setIndividualCaseId(UUID.randomUUID().toString());

    OffsetDateTime messageTimestamp = OffsetDateTime.now();


    when(caseService.getCaseByCaseId(UUID.fromString(expectedFulfilmentRequest.getCaseId())))
        .thenReturn(parentCase);

    // This simulates the DB creating the ID, which it does when the case is persisted
    when(caseService.saveNewCaseAndStampCaseRef(any(Case.class)))
        .then(
            invocation -> {
              Case caze = invocation.getArgument(0);
              caze.setSecretSequenceNumber(123);
              caze.setCaseRef(666);
              return caze;
            });

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

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<FulfilmentRequestDTO> fulfilmentRequestArgumentCaptor =
        ArgumentCaptor.forClass(FulfilmentRequestDTO.class);
    verify(caseService)
        .emitCaseCreatedEvent(
            caseArgumentCaptor.capture(), fulfilmentRequestArgumentCaptor.capture());
    assertThat(fulfilmentRequestArgumentCaptor.getValue().getFulfilmentCode())
        .isEqualTo(individualResponseCode);
    Case actualChildCase = caseArgumentCaptor.getValue();
    checkIndivdualFulfilmentRequestCase(parentCase, actualChildCase, managementEvent);
  }

  private void testIndividualResponseCodeSMS(String individualResponseCode) {
    // Given
    Case parentCase = getRandomCase();
    parentCase.setUacQidLinks(new ArrayList<>());
    parentCase.setEvents(new ArrayList<>());
    parentCase.setCreatedDateTime(OffsetDateTime.now().minusDays(1));
    parentCase.setState(null);
    parentCase.setReceiptReceived(true);
    parentCase.setRefusalReceived(true);
    parentCase.setAddressType("HH");

    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();
    expectedFulfilmentRequest.setCaseId(parentCase.getCaseId().toString());
    expectedFulfilmentRequest.setFulfilmentCode(individualResponseCode);
    expectedFulfilmentRequest.setIndividualCaseId(UUID.randomUUID().toString());
    OffsetDateTime messageTimestamp = OffsetDateTime.now();


    when(caseService.getCaseByCaseId(UUID.fromString(expectedFulfilmentRequest.getCaseId())))
        .thenReturn(parentCase);

    // This simulates the DB creating the ID, which it does when the case is persisted
    when(caseService.saveNewCaseAndStampCaseRef(any(Case.class)))
        .then(
            invocation -> {
              Case caze = invocation.getArgument(0);
              caze.setSecretSequenceNumber(123);
              caze.setCaseRef(666);
              return caze;
            });

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

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).emitCaseCreatedEvent(caseArgumentCaptor.capture());
    Case actualChildCase = caseArgumentCaptor.getValue();
    checkIndivdualFulfilmentRequestCase(parentCase, actualChildCase, managementEvent);
  }

  private void checkIndivdualFulfilmentRequestCase(
      Case parentCase, Case actualChildCase, ResponseManagementEvent managementEvent) {
    assertThat(actualChildCase.getCaseRef()).isNotEqualTo(parentCase.getCaseRef());
    assertThat(UUID.fromString(actualChildCase.getCaseId().toString()))
        .isNotEqualTo(parentCase.getCaseId());
    assertThat(actualChildCase.getCaseId().toString())
        .isEqualTo(managementEvent.getPayload().getFulfilmentRequest().getIndividualCaseId());
    assertThat(actualChildCase.getUacQidLinks()).isNull();
    assertThat(actualChildCase.getEvents()).isNull();
    assertThat(actualChildCase.getCreatedDateTime())
        .isBetween(OffsetDateTime.now().minusSeconds(10), OffsetDateTime.now());
    assertThat(actualChildCase.getCollectionExerciseId())
        .isEqualTo(parentCase.getCollectionExerciseId());
    assertThat(actualChildCase.getActionPlanId()).isEqualTo(parentCase.getActionPlanId());
    assertThat(actualChildCase.getState()).isEqualTo(CaseState.ACTIONABLE);
    assertThat(actualChildCase.isReceiptReceived()).isFalse();
    assertThat(actualChildCase.isRefusalReceived()).isFalse();
    assertThat(actualChildCase.getArid()).isEqualTo(parentCase.getArid());
    assertThat(actualChildCase.getEstabArid()).isEqualTo(parentCase.getEstabArid());
    assertThat(actualChildCase.getUprn()).isEqualTo(parentCase.getUprn());
    assertThat(actualChildCase.getAddressType()).isEqualTo(HOUSEHOLD_RESPONSE_ADDRESS_TYPE);
    assertThat(actualChildCase.getCaseType()).isEqualTo(HOUSEHOLD_INDIVIDUAL_RESPONSE_ADDRESS_TYPE);
    assertThat(actualChildCase.getEstabType()).isEqualTo(parentCase.getEstabType());
    assertThat(actualChildCase.getAddressLevel()).isNull();
    assertThat(actualChildCase.getAbpCode()).isEqualTo(parentCase.getAbpCode());
    assertThat(actualChildCase.getOrganisationName()).isEqualTo(parentCase.getOrganisationName());
    assertThat(actualChildCase.getAddressLine1()).isEqualTo(parentCase.getAddressLine1());
    assertThat(actualChildCase.getAddressLine2()).isEqualTo(parentCase.getAddressLine2());
    assertThat(actualChildCase.getAddressLine3()).isEqualTo(parentCase.getAddressLine3());
    assertThat(actualChildCase.getTownName()).isEqualTo(parentCase.getTownName());
    assertThat(actualChildCase.getPostcode()).isEqualTo(parentCase.getPostcode());
    assertThat(actualChildCase.getLatitude()).isEqualTo(parentCase.getLatitude());
    assertThat(actualChildCase.getLongitude()).isEqualTo(parentCase.getLongitude());
    assertThat(actualChildCase.getOa()).isEqualTo(parentCase.getOa());
    assertThat(actualChildCase.getLsoa()).isEqualTo(parentCase.getLsoa());
    assertThat(actualChildCase.getMsoa()).isEqualTo(parentCase.getMsoa());
    assertThat(actualChildCase.getLad()).isEqualTo(parentCase.getLad());
    assertThat(actualChildCase.getRegion()).isEqualTo(parentCase.getRegion());
    assertThat(actualChildCase.getHtcWillingness()).isNull();
    assertThat(actualChildCase.getHtcDigital()).isNull();
    assertThat(actualChildCase.getFieldCoordinatorId()).isNull();
    assertThat(actualChildCase.getFieldOfficerId()).isNull();
    assertThat(actualChildCase.getTreatmentCode()).isNull();
    assertThat(actualChildCase.getCeExpectedCapacity()).isNull();
  }
}
