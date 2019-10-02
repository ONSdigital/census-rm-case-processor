package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.service.CaseService.CASE_UPDATE_ROUTING_KEY;

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
import uk.gov.ons.census.casesvc.model.dto.SampleUnitDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class CaseServiceTest {

  private static final String FIELD_CORD_ID = "FIELD_CORD_ID";
  private static final String FIELD_OFFICER_ID = "FIELD_OFFICER_ID";
  private static final String CE_CAPACITY = "CE_CAPACITY";
  private static final String TEST_TREATMENT_CODE = "TEST_TREATMENT_CODE";
  private static final String TEST_POSTCODE = "TEST_POSTCODE";
  private static final String TEST_EXCHANGE = "TEST_EXCHANGE";
  private static final UUID TEST_UUID = UUID.randomUUID();

  @Mock CaseRepository caseRepository;

  @Mock UacQidLinkRepository uacQidLinkRepository;

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
  public void testBuildCCSCase() {
    // Given
    String caseId = TEST_UUID.toString();
    SampleUnitDTO sampleUnit = new SampleUnitDTO();
    String collectionExerciseId = TEST_UUID.toString();
    String actionPlanId = TEST_UUID.toString();

    // When
    Case actualCase =
        underTest.buildCCSCase(caseId, sampleUnit, collectionExerciseId, actionPlanId);

    // Then
    verify(mapperFacade).map(sampleUnit, Case.class);
    assertThat(actualCase.isCcsCase()).isTrue();
    assertThat(actualCase.getCaseId()).isEqualTo(UUID.fromString(caseId));
    assertThat(actualCase.getActionPlanId()).isEqualTo(actionPlanId);
    assertThat(actualCase.getCollectionExerciseId()).isEqualTo(collectionExerciseId);
  }

  @Test
  public void testSaveCCSCaseWithUacQidLink() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(TEST_UUID);
    Case caze = new Case();

    // When
    Case actualCase = underTest.saveCCSCaseWithUacQidLink(caze, uacQidLink);

    // Then
    ArgumentCaptor<UacQidLink> uacQidLinkArgCapt = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacQidLinkRepository, times(2)).saveAndFlush(uacQidLinkArgCapt.capture());
    verify(caseRepository, times(1)).saveAndFlush(caze);

    assertThat(uacQidLinkArgCapt.getAllValues().get(0)).isEqualTo(uacQidLink);
    assertThat(uacQidLinkArgCapt.getAllValues().get(1)).isEqualTo(uacQidLink);
    assertThat(actualCase).isEqualTo(caze);
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
}
