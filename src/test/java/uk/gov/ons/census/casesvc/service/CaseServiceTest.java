package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.service.CaseService.CASE_UPDATE_ROUTING_KEY;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import ma.glasnost.orika.MapperFacade;
import ma.glasnost.orika.impl.DefaultMapperFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
public class CaseServiceTest {
  private static final String FIELD_CORD_ID = "FIELD_CORD_ID";
  private static final String FIELD_OFFICER_ID = "FIELD_OFFICER_ID";
  private static final String CE_CAPACITY = "CE_CAPACITY";
  private static final String TEST_TREATMENT_CODE = "TEST_TREATMENT_CODE";
  private static final String TEST_POSTCODE = "TEST_POSTCODE";
  private static final String TEST_EXCHANGE = "TEST_EXCHANGE";
  private static final UUID TEST_UUID = UUID.randomUUID();
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND = "UACIT1";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH = "UACIT2";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH = "UACIT2W";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NORTHERN_IRELAND = "UACIT4";
  private static final String HOUSEHOLD_RESPONSE_ADDRESS_TYPE = "HH";
  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_ADDRESS_TYPE = "HI";

  @Mock CaseRepository caseRepository;

  @Spy
  private MapperFacade mapperFacade = new DefaultMapperFactory.Builder().build().getMapperFacade();

  @Mock RabbitTemplate rabbitTemplate;

  @InjectMocks CaseService underTest;

  @Test
  public void testSaveCase() {
    CreateCaseSample createCaseSample = new CreateCaseSample();
    createCaseSample.setTreatmentCode(TEST_TREATMENT_CODE);
    createCaseSample.setFieldCoordinatorId(FIELD_CORD_ID);
    createCaseSample.setFieldOfficerId(FIELD_OFFICER_ID);
    createCaseSample.setCeExpectedCapacity(CE_CAPACITY);
    // Given
    when(caseRepository.saveAndFlush(any(Case.class))).then(obj -> obj.getArgument(0));

    // When
    underTest.saveCase(createCaseSample);

    // Then
    verify(mapperFacade).map(createCaseSample, Case.class);
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository).saveAndFlush(caseArgumentCaptor.capture());

    Case savedCase = caseArgumentCaptor.getValue();
    assertThat(savedCase.getTreatmentCode()).isEqualTo(TEST_TREATMENT_CODE);
    assertThat(savedCase.getFieldCoordinatorId()).isEqualTo(FIELD_CORD_ID);
    assertThat(savedCase.getFieldOfficerId()).isEqualTo(FIELD_OFFICER_ID);
    assertThat(savedCase.getCeExpectedCapacity()).isEqualTo(CE_CAPACITY);
  }

  @Test
  public void testEmitCaseCreatedEvent() {
    // Given
    Case caze = new Case();
    caze.setRegion("E");
    caze.setCaseId(UUID.randomUUID());
    caze.setState(CaseState.ACTIONABLE);
    caze.setPostcode(TEST_POSTCODE);
    caze.setFieldCoordinatorId(FIELD_CORD_ID);
    caze.setFieldOfficerId(FIELD_OFFICER_ID);
    caze.setCeExpectedCapacity(CE_CAPACITY);
    ReflectionTestUtils.setField(underTest, "outboundExchange", TEST_EXCHANGE);

    // When
    underTest.saveAndEmitCaseCreatedEvent(caze);

    // Then
    verify(caseRepository).saveAndFlush(eq(caze));

    ArgumentCaptor<ResponseManagementEvent> rmeArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(TEST_EXCHANGE), eq(CASE_UPDATE_ROUTING_KEY), rmeArgumentCaptor.capture());

    CollectionCase collectionCase = rmeArgumentCaptor.getValue().getPayload().getCollectionCase();

    assertEquals(TEST_POSTCODE, collectionCase.getAddress().getPostcode());
    assertThat(collectionCase.getFieldCoordinatorId()).isEqualTo(FIELD_CORD_ID);
    assertThat(collectionCase.getFieldOfficerId()).isEqualTo(FIELD_OFFICER_ID);
    assertThat(collectionCase.getCeExpectedCapacity()).isEqualTo(CE_CAPACITY);
  }

  @Test(expected = RuntimeException.class)
  public void testUniqueCaseRefCreationThrowsRuntimeException() {
    // Given
    when(caseRepository.existsById(anyInt())).thenReturn(true);

    // When
    underTest.getUniqueCaseRef();
  }

  @Test(expected = RuntimeException.class)
  public void testCaseIdNotFound() {
    when(caseRepository.findByCaseId(TEST_UUID)).thenReturn(Optional.empty());

    String expectedErrorMessage = String.format("Case ID '%s' not present", TEST_UUID);

    try {
      // WHEN
      underTest.getCaseByCaseId(TEST_UUID);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT1() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_ENGLAND);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT2() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_ENGLISH);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT2W() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_WALES_WELSH);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT4() {
    testIndividualResponseCode(HOUSEHOLD_INDIVIDUAL_RESPONSE_REQUEST_NORTHERN_IRELAND);
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

    // when
    Case actualChildCase = underTest.prepareIndividualResponseCaseFromParentCase(parentCase);

    // then
    checkIndivdualFulfilmentRequestCase(parentCase, actualChildCase);
  }

  private void checkIndivdualFulfilmentRequestCase(Case parentCase, Case actualChildCase) {
    assertThat(actualChildCase.getCaseRef()).isNotEqualTo(parentCase.getCaseRef());
    assertThat(UUID.fromString(actualChildCase.getCaseId().toString()))
        .isNotEqualTo(parentCase.getCaseId());
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
