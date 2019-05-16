package uk.gov.ons.census.casesvc.messaging;

import org.jeasy.random.EasyRandom;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.EventType;
import uk.gov.ons.census.casesvc.model.dto.Receipt;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.Uac;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNull;
import static uk.gov.ons.census.casesvc.service.ReceiptProcessor.QID_RECEIPTED;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class ReceiptReceiverIT {
    private static final UUID TEST_CASE_ID = UUID.randomUUID();
    private static final EasyRandom easyRandom = new EasyRandom();
    private static final String TEST_QID = easyRandom.nextObject(String.class);
    private static final String TEST_UAC = easyRandom.nextObject(String.class);

    @Value("${queueconfig.receipt-response-inbound-queue}")
    private String inboundQueue;

    @Value("${queueconfig.emit-case-event-action-queue}")
    private String emitCaseEventActionQueue;

    @Autowired
    private RabbitQueueHelper rabbitQueueHelper;
    @Autowired private CaseRepository caseRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UacQidLinkRepository uacQidLinkRepository;

    @Before
    @Transactional
    public void setUp() {
        rabbitQueueHelper.purgeQueue(inboundQueue);
        rabbitQueueHelper.purgeQueue(emitCaseEventActionQueue);
        eventRepository.deleteAllInBatch();
        uacQidLinkRepository.deleteAllInBatch();
        caseRepository.deleteAllInBatch();
    }

    @Test
    public void testGoodReceiptEmitsMessageAndLogsEvent() throws InterruptedException, IOException {
        // GIVEN
        BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(emitCaseEventActionQueue);

        EasyRandom easyRandom = new EasyRandom();
        Case caze = easyRandom.nextObject(Case.class);
        caze.setCaseId(TEST_CASE_ID);
        caze.setUacQidLinks(null);
        caze = caseRepository.saveAndFlush(caze);

        UacQidLink uacQidLink = new UacQidLink();
        uacQidLink.setId(UUID.randomUUID());
        uacQidLink.setCaze(caze);
        uacQidLink.setQid(TEST_QID);
        uacQidLink.setUac(TEST_UAC);
        uacQidLinkRepository.saveAndFlush(uacQidLink);

        Receipt receipt = new Receipt();
        receipt.setCase_id(TEST_CASE_ID.toString());

        // WHEN
        rabbitQueueHelper.sendMessage(inboundQueue, receipt);

        //check the emitted event
        ResponseManagementEvent responseManagementEvent = rabbitQueueHelper.checkExpectedMessageReceived(outboundQueue);
        assertThat(responseManagementEvent.getEvent().getType()).isEqualTo(EventType.UAC_UPDATED);
        Uac actualUacObject = responseManagementEvent.getPayload().getUac();
        assertThat(actualUacObject.getUac()).isEqualTo(TEST_UAC);
        assertThat(actualUacObject.getQuestionnaireId()).isEqualTo(TEST_QID);
        assertThat(actualUacObject.getCaseId()).isEqualTo(TEST_CASE_ID.toString());

        // check database for log event
        List<Event> events = eventRepository.findAll();
        assertThat(events.size()).isEqualTo(1);
        Event event = events.get(0);
        assertThat(event.getEventDescription()).isEqualTo(QID_RECEIPTED);
        UacQidLink actualUacQuidLink = event.getUacQidLink();
        assertThat(actualUacQuidLink.getQid()).isEqualTo(TEST_QID);
        assertThat(actualUacQuidLink.getUac()).isEqualTo(TEST_UAC);
        assertThat(actualUacQuidLink.getCaze().getCaseId()).isEqualTo(TEST_CASE_ID);
    }

//    @Test
//    public void testTransactionality() throws InterruptedException, IOException {
//        //Stick no cases on the database
//        BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(emitCaseEventActionQueue);
//
//        Receipt receipt = new Receipt();
//        receipt.setCase_id(TEST_CASE_ID.toString());
//
//        // WHEN
//        rabbitQueueHelper.sendMessage(inboundQueue, receipt);
//
//        //Poll Queue, expected failure
//        String actualMessage = outboundQueue.poll(5, TimeUnit.SECONDS);
//        assertNull(actualMessage);
//
//        //Log events empty
//        assertThat(eventRepository.findAll().size()).isEqualTo(0);
//
//        //Save case and UacQidLink
//        EasyRandom easyRandom = new EasyRandom();
//        Case caze = easyRandom.nextObject(Case.class);
//        caze.setCaseId(TEST_CASE_ID);
//        caze.setUacQidLinks(null);
//        caze = caseRepository.saveAndFlush(caze);
//
//        UacQidLink uacQidLink = new UacQidLink();
//        uacQidLink.setId(UUID.randomUUID());
//        uacQidLink.setCaze(caze);
//        uacQidLink.setQid(TEST_QID);
//        uacQidLink.setUac(TEST_UAC);
//        uacQidLinkRepository.saveAndFlush(uacQidLink);
//
//        //Poll Queue, expected message to be there
//        rabbitQueueHelper.checkExpectedMessageReceived(outboundQueue);
//
//        //check Log Events
//        assertThat(eventRepository.findAll().size()).isEqualTo(1);
//    }
}
