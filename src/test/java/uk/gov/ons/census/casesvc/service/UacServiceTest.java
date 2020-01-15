package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.generateRandomUacQidLink;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.generateUacCreatedEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;

import java.time.OffsetDateTime;
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
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class UacServiceTest {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock UacQidLinkRepository uacQidLinkRepository;

  @Mock CaseService caseService;

  @Mock RabbitTemplate rabbitTemplate;

  @Mock UacQidServiceClient uacQidServiceClient;

  @Mock EventLogger eventLogger;

  @InjectMocks UacService underTest;

  @Test
  public void testSaveUacQidLink() {
    UacQidLink expectedUacQidLink = generateRandomUacQidLink();

    // Given
    when(uacQidLinkRepository.save(any(UacQidLink.class))).then(obj -> obj.getArgument(0));

    // When
    underTest.saveUacQidLink(expectedUacQidLink);

    // Then
    ArgumentCaptor<UacQidLink> caseArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacQidLinkRepository).save(caseArgumentCaptor.capture());
    UacQidLink actualUacQidLink = caseArgumentCaptor.getValue();
    assertThat(actualUacQidLink).isEqualTo(expectedUacQidLink);
  }

  //  @Test
  //  public void testSaveUacQidLinkEnglandHousehold() {
  //    // Given
  //    Case caze = new Case();
  //
  //    UacQidDTO uacQidDTO = new UacQidDTO();
  //    uacQidDTO.setUac("testuac");
  //    uacQidDTO.setQid("01testqid");
  //    when(uacQidServiceClient.generateUacQid(anyInt())).thenReturn(uacQidDTO);
  //
  //    // When
  //    UacQidLink result;
  //    result = underTest.buildUacQidLink(caze, 1);
  //
  //    // Then
  //    assertEquals("01", result.getQid().substring(0, 2));
  //    verify(uacQidServiceClient).generateUacQid(eq(1));
  //  }

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
    underTest.saveAndEmitUacUpdatedEvent(uacQidLink);

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
    when(caseService.getCaseByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId()))
        .thenReturn(linkedCase);
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
    when(caseService.getCaseByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId()))
        .thenReturn(linkedCase);

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
    when(caseService.getCaseByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId()))
        .thenReturn(linkedCase);

    // When
    underTest.ingestUacCreatedEvent(uacCreatedEvent);

    // Then
    verify(eventLogger)
        .logUacQidEvent(
            any(UacQidLink.class),
            any(OffsetDateTime.class),
            eq("RM UAC QID pair created"),
            eq(EventType.RM_UAC_CREATED),
            eq(uacCreatedEvent.getEvent()),
            anyString());
  }

  @Test
  public void testFindUacLinkExists() {
    // Given
    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setId(UUID.randomUUID());

    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(Optional.of(expectedUacQidLink));

    // When
    UacQidLink actualUacQidLink = underTest.findByQid("Test qid");

    // Then
    assertThat(actualUacQidLink.getId()).isEqualTo(expectedUacQidLink.getId());
  }

  @Test(expected = RuntimeException.class)
  public void testCantFindUacLink() {
    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(Optional.empty());
    underTest.findByQid("Test qid");
  }
}
