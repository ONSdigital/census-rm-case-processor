package uk.gov.ons.census.casesvc.messaging;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.iac.IacDispenser;
import uk.gov.ons.census.casesvc.model.dto.CaseCreatedEvent;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.utility.QidCreator;

@RunWith(MockitoJUnitRunner.class)
public class SampleReceiverTest {

  @InjectMocks private SampleReceiver underTest;

  @Mock private CaseRepository caseRepository;
  @Mock private UacQidLinkRepository uacQidLinkRepository;
  @Mock private EventRepository eventRepository;
  @Mock private RabbitTemplate rabbitTemplate;
  @Mock private IacDispenser iacDispenser;
  @Mock private QidCreator qidCreator;

  @Test
  public void testHappyPath() {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();
    createCaseSample.setAddressLine1("123 Fake Street");
    createCaseSample.setRgn("E999");
    when(uacQidLinkRepository.saveAndFlush(any()))
        .then(
            obj -> {
              UacQidLink uacQidLink = obj.getArgument(0);
              uacQidLink.setUniqueNumber(12345L);
              return uacQidLink;
            });

    when(caseRepository.saveAndFlush(any())).then(obj -> obj.getArgument(0));

    when(caseRepository.saveAndFlush(any()))
        .then(
            obj -> {
              Case caze = obj.getArgument(0);
              caze.setCaseRef(123456789L);
              return caze;
            });

    ReflectionTestUtils.setField(underTest, "emitCaseEventExchange", "myExchange");

    String uac = "abcd-1234-xyza-4321";
    when(iacDispenser.getIacCode()).thenReturn(uac);

    long qid = 1234567891011125L;
    when(qidCreator.createQid(anyInt(), anyInt(), anyLong())).thenReturn(qid);

    // When
    underTest.receiveMessage(createCaseSample);

    // Then
    ArgumentCaptor<CaseCreatedEvent> emittedMessageArgCaptor =
        ArgumentCaptor.forClass(CaseCreatedEvent.class);
    verify(rabbitTemplate)
        .convertAndSend(eq("myExchange"), eq(""), emittedMessageArgCaptor.capture());
    CaseCreatedEvent caseCreatedEvent = emittedMessageArgCaptor.getValue();
    assertEquals("123456789", caseCreatedEvent.getPayload().getCollectionCase().getCaseRef());
    assertEquals(
        "123 Fake Street",
        caseCreatedEvent.getPayload().getCollectionCase().getAddress().getAddressLine1());
    verify(iacDispenser).getIacCode();
    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacQidLinkRepository).save(uacQidLinkArgumentCaptor.capture());
    UacQidLink uacQidLink = uacQidLinkArgumentCaptor.getValue();
    assertEquals(uac, uacQidLink.getUac());
    assertEquals(qid, uacQidLink.getQid().longValue());
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    Event event = eventArgumentCaptor.getValue();
    assertEquals("Case created", event.getEventDescription());
  }

  @Test(expected = RuntimeException.class)
  public void testDatabaseBlowsUp(){
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();
    createCaseSample.setAddressLine1("123 Fake Street");
    createCaseSample.setRgn("E999");
    when(uacQidLinkRepository.saveAndFlush(any()))
        .thenThrow(new RuntimeException());

    // When
    underTest.receiveMessage(createCaseSample);

    // Then

  }

  @Test(expected = RuntimeException.class)
  public void testRabbitBlowsUp(){
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();
    createCaseSample.setAddressLine1("123 Fake Street");
    createCaseSample.setRgn("E999");
    when(uacQidLinkRepository.saveAndFlush(any()))
        .then(
            obj -> {
              UacQidLink uacQidLink = obj.getArgument(0);
              uacQidLink.setUniqueNumber(12345L);
              return uacQidLink;
            });

    when(caseRepository.saveAndFlush(any())).then(obj -> obj.getArgument(0));

    when(caseRepository.saveAndFlush(any()))
        .then(
            obj -> {
              Case caze = obj.getArgument(0);
              caze.setCaseRef(123456789L);
              return caze;
            });

    ReflectionTestUtils.setField(underTest, "emitCaseEventExchange", "myExchange");

    String uac = "abcd-1234-xyza-4321";
    when(iacDispenser.getIacCode()).thenReturn(uac);

    long qid = 1234567891011125L;
    when(qidCreator.createQid(anyInt(), anyInt(), anyLong())).thenReturn(qid);

    doThrow(new RuntimeException()).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(CaseCreatedEvent.class));

    // When
    underTest.receiveMessage(createCaseSample);

  }

}
