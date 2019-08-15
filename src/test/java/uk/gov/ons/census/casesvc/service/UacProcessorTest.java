package uk.gov.ons.census.casesvc.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.generateUacCreatedEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;

import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.client.UacQidServiceClient;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class UacProcessorTest {

  @Mock UacQidLinkRepository uacQidLinkRepository;

  @Mock CaseRepository caseRepository;

  @Mock RabbitTemplate rabbitTemplate;

  @Mock UacQidServiceClient uacQidServiceClient;

  @Mock EventLogger eventLogger;

  @InjectMocks UacProcessor underTest;

  @Test
  public void testSaveUacQidLinkEnglandHousehold() {
    // Given
    Case caze = new Case();

    UacQidDTO uacQidDTO = new UacQidDTO();
    uacQidDTO.setUac("testuac");
    uacQidDTO.setQid("01testqid");
    when(uacQidServiceClient.generateUacQid(anyInt())).thenReturn(uacQidDTO);

    // When
    UacQidLink result;
    result = underTest.generateAndSaveUacQidLink(caze, 1);

    // Then
    assertEquals("01", result.getQid().substring(0, 2));
    verify(uacQidServiceClient).generateUacQid(eq(1));
  }

  @Test
  public void testEmitUacUpdatedEvent() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setUac("12345");
    Case caze = new Case();
    UUID caseUuid = UUID.randomUUID();
    caze.setCaseId(caseUuid);
    ReflectionTestUtils.setField(underTest, "outboundExchange", "TEST_EXCHANGE");

    // When
    underTest.emitUacUpdatedEvent(uacQidLink, caze);

    // Then
    ArgumentCaptor<ResponseManagementEvent> responseManagementEventArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("TEST_EXCHANGE"),
            eq("event.uac.update"),
            responseManagementEventArgumentCaptor.capture());
    assertEquals(
        "12345", responseManagementEventArgumentCaptor.getValue().getPayload().getUac().getUac());
  }

  @Test
  public void testIngestUacCreatedEventSavesUacQidLink() {
    // Given
    Case linkedCase = getRandomCase();
    ResponseManagementEvent uacCreatedEvent = generateUacCreatedEvent(linkedCase);
    when(caseRepository.findByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId()))
        .thenReturn(Optional.of(linkedCase));
    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);

    // When
    underTest.ingestUacCreatedEvent(uacCreatedEvent);

    // Then
    verify(uacQidLinkRepository).save(uacQidLinkArgumentCaptor.capture());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getQid(),
        uacQidLinkArgumentCaptor.getValue().getQid());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getUac(),
        uacQidLinkArgumentCaptor.getValue().getUac());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getCaseId(),
        uacQidLinkArgumentCaptor.getValue().getCaze().getCaseId());
  }

  @Test
  public void testIngestUacCreatedEventEmitsUacUpdatedEvent() {
    // Given
    Case linkedCase = getRandomCase();
    ResponseManagementEvent uacCreatedEvent = generateUacCreatedEvent(linkedCase);
    when(caseRepository.findByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId()))
        .thenReturn(Optional.of(linkedCase));
    ArgumentCaptor<ResponseManagementEvent> responseManagementEventArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    ReflectionTestUtils.setField(underTest, "outboundExchange", "TEST_EXCHANGE");

    // When
    underTest.ingestUacCreatedEvent(uacCreatedEvent);

    // Then
    verify(rabbitTemplate)
        .convertAndSend(
            eq("TEST_EXCHANGE"),
            eq("event.uac.update"),
            responseManagementEventArgumentCaptor.capture());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getQid(),
        responseManagementEventArgumentCaptor
            .getValue()
            .getPayload()
            .getUac()
            .getQuestionnaireId());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getUac(),
        responseManagementEventArgumentCaptor.getValue().getPayload().getUac().getUac());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getCaseId().toString(),
        responseManagementEventArgumentCaptor.getValue().getPayload().getUac().getCaseId());
  }

  @Test
  public void testIngestUacCreatedEventLogsRmUacCreatedEvent() {
    // Given
    Case linkedCase = getRandomCase();
    ResponseManagementEvent uacCreatedEvent = generateUacCreatedEvent(linkedCase);
    when(caseRepository.findByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId()))
        .thenReturn(Optional.of(linkedCase));
    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);

    // When
    underTest.ingestUacCreatedEvent(uacCreatedEvent);

    // Then
    verify(eventLogger)
        .logEvent(
            uacQidLinkArgumentCaptor.capture(),
            eq("RM UAC QID pair created"),
            eq(uacCreatedEvent.getPayload()),
            eq(uacCreatedEvent.getEvent()));
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getQid(),
        uacQidLinkArgumentCaptor.getValue().getQid());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getUac(),
        uacQidLinkArgumentCaptor.getValue().getUac());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getCaseId(),
        uacQidLinkArgumentCaptor.getValue().getCaze().getCaseId());
  }

  @Test(expected = RuntimeException.class)
  public void testIngestUacCreatedEventThrowsRuntimeErrorIfCaseNotFound() {
    // Given
    Case unknownCase = getRandomCase();
    ResponseManagementEvent uacCreatedEvent = generateUacCreatedEvent(unknownCase);
    when(caseRepository.findByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId()))
        .thenReturn(Optional.empty());

    try {

      // When
      underTest.ingestUacCreatedEvent(uacCreatedEvent);
    } catch (RuntimeException re) {

      // Then
      assertEquals("No case found matching UAC created event", re.getMessage());
      throw re;
    }
  }
}
