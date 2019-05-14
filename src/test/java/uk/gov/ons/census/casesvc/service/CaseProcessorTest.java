package uk.gov.ons.census.casesvc.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
public class CaseProcessorTest {

  @Mock CaseRepository caseRepository;

  @Spy
  private MapperFacade mapperFacade = new DefaultMapperFactory.Builder().build().getMapperFacade();

  @Mock RabbitTemplate rabbitTemplate;

  @InjectMocks CaseProcessor underTest;

  @Test
  public void testSaveCase() {
    CreateCaseSample ccs = new CreateCaseSample();
    ccs.setTreatmentCode("TEST_TREATMENT_CODE");
    // Given
    when(caseRepository.saveAndFlush(any(Case.class))).then(obj -> obj.getArgument(0));

    // When
    underTest.saveCase(ccs);

    // Then
    verify(mapperFacade).map(ccs, Case.class);
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository).saveAndFlush(caseArgumentCaptor.capture());
    assertEquals("TEST_TREATMENT_CODE", caseArgumentCaptor.getValue().getTreatmentCode());
  }

  @Test
  public void testEmitCaseCreatedEvent() {
    // Given
    Case caze = new Case();
    caze.setRgn("E");
    caze.setCaseId(UUID.randomUUID());
    caze.setState(CaseState.ACTIONABLE);
    caze.setPostcode("TEST_POSTCODE");
    ReflectionTestUtils.setField(underTest, "emitCaseEventExchange", "TEST_EXCHANGE");

    // When
    underTest.emitCaseCreatedEvent(caze);

    // Then
    ArgumentCaptor<ResponseManagementEvent> rmeArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(rabbitTemplate).convertAndSend(eq("TEST_EXCHANGE"), eq(""), rmeArgumentCaptor.capture());
    assertEquals(
        "TEST_POSTCODE",
        rmeArgumentCaptor.getValue().getPayload().getCollectionCase().getAddress().getPostcode());
  }
}
