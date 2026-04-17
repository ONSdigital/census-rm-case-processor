package uk.gov.ons.census.caseprocessor.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.caseprocessor.testutils.MessageConstructor.constructMessage;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_CORRELATION_ID;
import static uk.gov.ons.census.caseprocessor.testutils.TestConstants.TEST_ORIGINATING_USER;
import static uk.gov.ons.census.caseprocessor.utils.Constants.OUTBOUND_EVENT_SCHEMA_VERSION;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import uk.gov.ons.census.caseprocessor.logging.EventLogger;
import uk.gov.ons.census.caseprocessor.model.dto.EventDTO;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.dto.PayloadDTO;
import uk.gov.ons.census.caseprocessor.model.dto.ReceiptDTO;
import uk.gov.ons.census.caseprocessor.service.UacService;
import uk.gov.ons.census.common.model.entity.EventType;
import uk.gov.ons.census.common.model.entity.UacQidLink;

@ExtendWith(MockitoExtension.class)
public class ReceiptReceiverTest {
  private static final String QID = "12345";

  @Mock private UacService uacService;
  @Mock private EventLogger eventLogger;

  @InjectMocks ReceiptReceiver underTest;

  @Test
  public void testUnlinkedUacQidReceiptWhereActive() {
    ReceiptDTO receiptDTO = new ReceiptDTO();
    receiptDTO.setQid(QID);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setReceipt(receiptDTO);
    EventDTO event = new EventDTO();
    event.setPayload(payloadDTO);

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeader.setCorrelationId(TEST_CORRELATION_ID);
    eventHeader.setOriginatingUser(TEST_ORIGINATING_USER);
    eventHeader.setTopic("Test topic");
    eventHeader.setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    event.setHeader(eventHeader);
    Message<byte[]> message = constructMessage(event);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setActive(true);

    when(uacService.findByQid(any())).thenReturn(uacQidLink);
    when(uacService.saveAndEmitUacUpdateEvent(any(UacQidLink.class), any(UUID.class), anyString()))
        .thenReturn(uacQidLink);

    underTest.receiveMessage(message);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService)
        .saveAndEmitUacUpdateEvent(
            uacQidLinkCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.isActive()).isFalse();

    verify(eventLogger)
        .logUacQidEvent(
            eq(actualUacQidLink),
            eq("Receipt received"),
            eq(EventType.RECEIPT),
            eq(event),
            eq(message));
  }

  @Test
  public void testUnlinkedUacQidReceiptWherePreviouslyReceipted() {
    ReceiptDTO receiptDTO = new ReceiptDTO();
    receiptDTO.setQid(QID);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setReceipt(receiptDTO);
    EventDTO event = new EventDTO();
    event.setPayload(payloadDTO);

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeader.setTopic("Test topic");
    eventHeader.setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    event.setHeader(eventHeader);
    Message<byte[]> message = constructMessage(event);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setActive(true);
    uacQidLink.setReceiptReceived(true);

    when(uacService.findByQid(any())).thenReturn(uacQidLink);

    underTest.receiveMessage(message);

    ArgumentCaptor<String> uacQidLinkCaptor = ArgumentCaptor.forClass(String.class);
    verify(uacService).findByQid(uacQidLinkCaptor.capture());

    verify(eventLogger)
        .logUacQidEvent(
            eq(uacQidLink), eq("Receipt received"), eq(EventType.RECEIPT), eq(event), eq(message));

    verifyNoMoreInteractions(uacService);
  }

  @Test
  public void testUnlinkedUacQidReceiptsUacQid() {
    ReceiptDTO receiptDTO = new ReceiptDTO();
    receiptDTO.setQid(QID);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setReceipt(receiptDTO);
    EventDTO event = new EventDTO();
    event.setPayload(payloadDTO);

    EventHeaderDTO eventHeader = new EventHeaderDTO();
    eventHeader.setVersion(OUTBOUND_EVENT_SCHEMA_VERSION);
    eventHeader.setCorrelationId(TEST_CORRELATION_ID);
    eventHeader.setOriginatingUser(TEST_ORIGINATING_USER);
    eventHeader.setTopic("Test topic");
    eventHeader.setDateTime(OffsetDateTime.now(ZoneId.of("UTC")));
    event.setHeader(eventHeader);
    Message<byte[]> message = constructMessage(event);

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setActive(true);

    when(uacService.findByQid(any())).thenReturn(uacQidLink);
    when(uacService.saveAndEmitUacUpdateEvent(any(UacQidLink.class), any(UUID.class), anyString()))
        .thenReturn(uacQidLink);

    underTest.receiveMessage(message);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService)
        .saveAndEmitUacUpdateEvent(
            uacQidLinkCaptor.capture(), eq(TEST_CORRELATION_ID), eq(TEST_ORIGINATING_USER));
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.isActive()).isFalse();
    assertThat(actualUacQidLink.isReceiptReceived()).isTrue();

    verify(eventLogger)
        .logUacQidEvent(
            eq(actualUacQidLink),
            eq("Receipt received"),
            eq(EventType.RECEIPT),
            eq(event),
            eq(message));
  }
}
