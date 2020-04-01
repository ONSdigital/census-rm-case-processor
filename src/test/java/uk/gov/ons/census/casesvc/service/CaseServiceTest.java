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

import java.time.OffsetDateTime;
import java.util.*;
import ma.glasnost.orika.MapperFacade;
import ma.glasnost.orika.impl.DefaultMapperFactory;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.Metadata;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.SampleUnitDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseMetadata;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
public class CaseServiceTest {

  private static final String HOUSEHOLD_INDIVIDUAL_RESPONSE_ADDRESS_TYPE = "HI";
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
  private static final long TEST_CASE_REF = 1234567890L;
  private static final byte[] caserefgeneratorkey =
      new byte[] {0x10, 0x20, 0x10, 0x20, 0x10, 0x20, 0x10, 0x20};
  private static final Integer CE_ACTUAL_CAPACITY = 0;
  private static final String TEST_ADDRESS_TYPE_CE = "CE";
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
    createCaseSample.setSecureEstablishment(0);

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
    assertThat(savedCase.getMetadata().getSecureEstablishment()).isFalse();
  }

  @Test
  public void testSaveCaseSampleCESecureTrue() {
    CreateCaseSample createCaseSample = new CreateCaseSample();
    createCaseSample.setTreatmentCode(TEST_TREATMENT_CODE);
    createCaseSample.setFieldCoordinatorId(FIELD_CORD_ID);
    createCaseSample.setFieldOfficerId(FIELD_OFFICER_ID);
    createCaseSample.setCeExpectedCapacity(CE_CAPACITY);
    createCaseSample.setAddressType(TEST_ADDRESS_TYPE_CE);
    createCaseSample.setSecureEstablishment(1);

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
    assertThat(savedCase.getCaseType()).isEqualTo(TEST_ADDRESS_TYPE_CE);
    assertThat(savedCase.isHandDelivery()).isFalse();
    assertThat(savedCase.getMetadata().getSecureEstablishment()).isTrue();
  }

  @Test
  public void testSaveCaseSampleCESecureFalse() {
    CreateCaseSample createCaseSample = new CreateCaseSample();
    createCaseSample.setTreatmentCode(TEST_TREATMENT_CODE);
    createCaseSample.setFieldCoordinatorId(FIELD_CORD_ID);
    createCaseSample.setFieldOfficerId(FIELD_OFFICER_ID);
    createCaseSample.setCeExpectedCapacity(CE_CAPACITY);
    createCaseSample.setAddressType(TEST_ADDRESS_TYPE_CE);
    createCaseSample.setSecureEstablishment(0);

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
    assertThat(savedCase.getCaseType()).isEqualTo(TEST_ADDRESS_TYPE_CE);
    assertThat(savedCase.isHandDelivery()).isFalse();
    assertThat(savedCase.getMetadata().getSecureEstablishment()).isFalse();
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
    caze.setCaseRef(TEST_CASE_REF);
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
    assertThat(collectionCase.getMetadata()).isNull();
  }

  @Test
  public void testEmitCaseCreatedEventCESecure() {
    // Given
    Case caze = new Case();
    caze.setRegion("E");
    caze.setCaseRef(123L);
    caze.setCaseId(UUID.randomUUID());
    caze.setPostcode(TEST_POSTCODE);
    caze.setFieldCoordinatorId(FIELD_CORD_ID);
    caze.setFieldOfficerId(FIELD_OFFICER_ID);
    caze.setCeExpectedCapacity(CE_CAPACITY);
    caze.setCeActualResponses(CE_ACTUAL_CAPACITY);
    CaseMetadata metadata = new CaseMetadata();
    metadata.setSecureEstablishment(true);
    caze.setMetadata(metadata);
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
    assertThat(collectionCase.getMetadata().getSecureEstablishment()).isTrue();
  }

  @Test(expected = RuntimeException.class)
  public void testCaseIdNotFound() {
    when(caseRepository.findById(TEST_UUID)).thenReturn(Optional.empty());

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

  @Test
  public void testSaveAndEmitCaseEventWithNullRegion() {
    // Given
    Case caze = getRandomCase();
    caze.setRegion(null);

    // When
    PayloadDTO payload = underTest.saveCaseAndEmitCaseCreatedEvent(caze);

    // Then
    assertThat(payload.getCollectionCase().getAddress().getRegion()).isNull();
  }

  @Test
  public void checkIndividualCaseCreatedCorrectly() {
    // Given
    EasyRandom easyRandom = new EasyRandom();
    Case parentCase = easyRandom.nextObject(Case.class);
    UUID childCaseId = UUID.randomUUID();

    // When
    Case actualChildCase =
        underTest.prepareIndividualResponseCaseFromParentCase(parentCase, childCaseId);

    // Then
    assertThat(actualChildCase.getCaseRef()).isNotEqualTo(parentCase.getCaseRef());
    assertThat(UUID.fromString(actualChildCase.getCaseId().toString()))
        .isNotEqualTo(parentCase.getCaseId());
    assertThat(actualChildCase.getCaseId()).isEqualTo(childCaseId);
    assertThat(actualChildCase.getUacQidLinks()).isNull();
    assertThat(actualChildCase.getEvents()).isNull();
    assertThat(actualChildCase.getCreatedDateTime())
        .isBetween(OffsetDateTime.now().minusSeconds(10), OffsetDateTime.now());
    assertThat(actualChildCase.getCollectionExerciseId())
        .isEqualTo(parentCase.getCollectionExerciseId());
    assertThat(actualChildCase.getActionPlanId()).isEqualTo(parentCase.getActionPlanId());
    assertThat(actualChildCase.isReceiptReceived()).isFalse();
    assertThat(actualChildCase.isRefusalReceived()).isFalse();
    assertThat(actualChildCase.getArid()).isEqualTo(parentCase.getArid());
    assertThat(actualChildCase.getEstabArid()).isEqualTo(parentCase.getEstabArid());
    assertThat(actualChildCase.getUprn()).isEqualTo(parentCase.getUprn());
    assertThat(actualChildCase.getAddressType()).isEqualTo(parentCase.getAddressType());
    assertThat(actualChildCase.getCaseType()).isEqualTo(HOUSEHOLD_INDIVIDUAL_RESPONSE_ADDRESS_TYPE);
    assertThat(actualChildCase.getEstabType()).isEqualTo(parentCase.getEstabType());
    assertThat(actualChildCase.getAddressLevel()).isEqualTo(parentCase.getAddressLevel());
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
    assertThat(actualChildCase.getAddressLevel()).isEqualTo(parentCase.getAddressLevel());
  }

  @Test
  public void testUnreceiptCaseWithMetadata() {
    // Given
    Case caze = getRandomCase();
    caze.setReceiptReceived(true);

    Metadata metadata = new Metadata();
    metadata.setBlankQuestionnaireReceived(true);
    metadata.setFieldDecision(ActionInstructionType.UPDATE);
    metadata.setCauseEventType(EventTypeDTO.RESPONSE_RECEIVED);

    // When
    underTest.unreceiptCase(caze, metadata);

    // Then
    // Check the case is saved with receipt received false
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository).saveAndFlush(caseArgumentCaptor.capture());
    Case actualSavedCase = caseArgumentCaptor.getValue();
    assertThat(actualSavedCase.getCaseId()).isEqualTo(caze.getCaseId());
    assertThat(actualSavedCase.isReceiptReceived()).isFalse();

    // Check the correct case updated event is emitted
    ArgumentCaptor<ResponseManagementEvent> eventArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(rabbitTemplate)
        .convertAndSend(eq(null), eq(CASE_UPDATE_ROUTING_KEY), eventArgumentCaptor.capture());
    ResponseManagementEvent emittedEvent = eventArgumentCaptor.getValue();
    assertThat(emittedEvent.getEvent().getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
    assertThat(emittedEvent.getPayload().getCollectionCase().getReceiptReceived()).isFalse();
    assertThat(emittedEvent.getPayload().getCollectionCase().getId())
        .isEqualTo(caze.getCaseId().toString());
    assertThat(emittedEvent.getPayload().getMetadata()).isEqualToComparingFieldByField(metadata);
  }
}
