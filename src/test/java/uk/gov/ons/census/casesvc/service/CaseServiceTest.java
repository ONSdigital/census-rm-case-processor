package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.service.CaseService.CASE_UPDATE_ROUTING_KEY;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;

import java.util.*;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.SampleUnitDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
@ActiveProfiles("test")
public class CaseServiceTest {

  private static final String FIELD_CORD_ID = "FIELD_CORD_ID";
  private static final String FIELD_OFFICER_ID = "FIELD_OFFICER_ID";
  private static final Integer CE_CAPACITY = 37;
  private static final String TEST_TREATMENT_CODE = "TEST_TREATMENT_CODE";
  private static final String TEST_POSTCODE = "TEST_POSTCODE";
  private static final String TEST_EXCHANGE = "TEST_EXCHANGE";
  private static final String TEST_ADDRESS_TYPE = "testy_address_type";
  private static final UUID TEST_UUID = UUID.randomUUID();
  private static final UUID TEST_ACTION_PLAN_ID = UUID.randomUUID();
  private static final UUID TEST_COLLECTION_EXERCISE_ID = UUID.randomUUID();
  private static final byte[] caserefgeneratorkey =
      new byte[] {0x10, 0x20, 0x10, 0x20, 0x10, 0x20, 0x10, 0x20};
  private static final Integer CE_ACTUAL_CAPACITY = 0;
  private List<String> directDeliveryTreatmentCodes =
      new ArrayList<>(Arrays.asList("CE_LDIEE", "test"));

  @Mock CaseRepository caseRepository;

  @Spy
  private MapperFacade mapperFacade = new DefaultMapperFactory.Builder().build().getMapperFacade();

  @Mock RabbitTemplate rabbitTemplate;

  @InjectMocks CaseService underTest;

  @Test
  public void testSaveCase() {
    Case expectedCase = getRandomCase();

    // Given
    when(caseRepository.saveAndFlush(any(Case.class))).then(obj -> obj.getArgument(0));

    // When
    underTest.saveCase(expectedCase);

    // Then
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository).saveAndFlush(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase).isEqualTo(expectedCase);
  }

  @Test
  public void testSaveCaseSample() {
    CreateCaseSample createCaseSample = new CreateCaseSample();
    createCaseSample.setTreatmentCode(TEST_TREATMENT_CODE);
    createCaseSample.setFieldCoordinatorId(FIELD_CORD_ID);
    createCaseSample.setFieldOfficerId(FIELD_OFFICER_ID);
    createCaseSample.setCeExpectedCapacity(CE_CAPACITY);
    createCaseSample.setAddressType(TEST_ADDRESS_TYPE);

    ReflectionTestUtils.setField(underTest, "caserefgeneratorkey", caserefgeneratorkey);
    ReflectionTestUtils.setField(
        underTest, "directDeliveryTreatmentCodes", directDeliveryTreatmentCodes);

    // Given
    when(caseRepository.saveAndFlush(any(Case.class))).then(obj -> obj.getArgument(0));

    // When
    underTest.saveCaseSample(createCaseSample);

    // Then
    verify(mapperFacade).map(createCaseSample, Case.class);
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository, times(2)).saveAndFlush(caseArgumentCaptor.capture());

    Case savedCase = caseArgumentCaptor.getAllValues().get(1);
    assertThat(savedCase.getTreatmentCode()).isEqualTo(TEST_TREATMENT_CODE);
    assertThat(savedCase.getFieldCoordinatorId()).isEqualTo(FIELD_CORD_ID);
    assertThat(savedCase.getFieldOfficerId()).isEqualTo(FIELD_OFFICER_ID);
    assertThat(savedCase.getCeExpectedCapacity()).isEqualTo(CE_CAPACITY);
    assertThat(savedCase.getCeActualResponses()).isEqualTo(0);
    assertThat(savedCase.getCaseType()).isEqualTo(TEST_ADDRESS_TYPE);
    assertThat(savedCase.isHandDelivery()).isFalse();
  }

  @Test
  public void testCreateCCSCaseWithRefusalNotReceived() {
    // Given
    String caseId = TEST_UUID.toString();
    SampleUnitDTO sampleUnit = new SampleUnitDTO();

    ReflectionTestUtils.setField(underTest, "actionPlanId", TEST_ACTION_PLAN_ID.toString());
    ReflectionTestUtils.setField(
        underTest, "collectionExerciseId", TEST_COLLECTION_EXERCISE_ID.toString());
    ReflectionTestUtils.setField(underTest, "caserefgeneratorkey", caserefgeneratorkey);

    // This simulates the DB creating the ID, which it does when the case is persisted
    when(caseRepository.saveAndFlush(any(Case.class)))
        .then(
            invocation -> {
              Case caze = invocation.getArgument(0);
              caze.setSecretSequenceNumber(123);
              return caze;
            });

    // When
    Case actualCase = underTest.createCCSCase(caseId, sampleUnit, false, false);

    // Then
    verify(mapperFacade).map(sampleUnit, Case.class);
    assertThat(actualCase.getSurvey()).isEqualTo("CCS");
    assertThat(actualCase.getCaseId()).isEqualTo(UUID.fromString(caseId));
    assertThat(actualCase.isRefusalReceived()).isFalse();
    assertThat(actualCase.getActionPlanId()).isEqualTo(TEST_ACTION_PLAN_ID.toString());
    assertThat(actualCase.getCollectionExerciseId())
        .isEqualTo(TEST_COLLECTION_EXERCISE_ID.toString());
  }

  @Test
  public void testCreateCCSCaseWithRefusalReceived() {
    // Given
    String caseId = TEST_UUID.toString();
    SampleUnitDTO sampleUnit = new SampleUnitDTO();

    ReflectionTestUtils.setField(underTest, "actionPlanId", TEST_ACTION_PLAN_ID.toString());
    ReflectionTestUtils.setField(
        underTest, "collectionExerciseId", TEST_COLLECTION_EXERCISE_ID.toString());
    ReflectionTestUtils.setField(underTest, "caserefgeneratorkey", caserefgeneratorkey);

    // This simulates the DB creating the ID, which it does when the case is persisted
    when(caseRepository.saveAndFlush(any(Case.class)))
        .then(
            invocation -> {
              Case caze = invocation.getArgument(0);
              caze.setSecretSequenceNumber(123);
              return caze;
            });

    // When
    Case actualCase = underTest.createCCSCase(caseId, sampleUnit, true, false);

    // Then
    verify(mapperFacade).map(sampleUnit, Case.class);
    assertThat(actualCase.getSurvey()).isEqualTo("CCS");
    assertThat(actualCase.getCaseId()).isEqualTo(UUID.fromString(caseId));
    assertThat(actualCase.isRefusalReceived()).isTrue();
    assertThat(actualCase.getActionPlanId()).isEqualTo(TEST_ACTION_PLAN_ID.toString());
    assertThat(actualCase.getCollectionExerciseId())
        .isEqualTo(TEST_COLLECTION_EXERCISE_ID.toString());
  }

  @Test
  public void testEmitCaseCreatedEvent() {
    // Given
    Case caze = new Case();
    caze.setRegion("E");
    caze.setCaseRef(123);
    caze.setCaseId(UUID.randomUUID());
    caze.setPostcode(TEST_POSTCODE);
    caze.setFieldCoordinatorId(FIELD_CORD_ID);
    caze.setFieldOfficerId(FIELD_OFFICER_ID);
    caze.setCeExpectedCapacity(CE_CAPACITY);
    caze.setCeActualResponses(CE_ACTUAL_CAPACITY);
    ReflectionTestUtils.setField(underTest, "outboundExchange", TEST_EXCHANGE);

    // When
    underTest.saveCaseAndEmitCaseCreatedEvent(caze);

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
    assertThat(collectionCase.getCeActualResponses()).isEqualTo(CE_ACTUAL_CAPACITY);
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
  public void testisTreatmentCodeDirectDeliveredIsTrue() {
    // Given
    ReflectionTestUtils.setField(
        underTest, "directDeliveryTreatmentCodes", directDeliveryTreatmentCodes);

    // When
    boolean treatmentCodeResult = underTest.isTreatmentCodeDirectDelivered("CE_LDIEE");

    // Then
    assertThat(treatmentCodeResult).isTrue();
  }

  @Test
  public void testisTreatmentCodeDirectDeliveredIsFalse() {
    // Given
    ReflectionTestUtils.setField(
        underTest, "directDeliveryTreatmentCodes", directDeliveryTreatmentCodes);

    // When
    boolean treatmentCodeResult = underTest.isTreatmentCodeDirectDelivered("CE_LQIEE");

    // Then
    assertThat(treatmentCodeResult).isFalse();
  }
}
