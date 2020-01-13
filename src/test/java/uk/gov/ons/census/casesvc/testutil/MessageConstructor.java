package uk.gov.ons.census.casesvc.testutil;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

public class MessageConstructor {
  public static <T> Message<T> constructMessageWithValidTimeStamp(T msgPayload) {
    Message<T> message = mock(Message.class);
    when(message.getPayload()).thenReturn(msgPayload);

    // Now the timestamp fun.  Get one an hour+ ago, to 'prove' no fluking
    long timeStamp = OffsetDateTime.now().minusSeconds(3911).toInstant().toEpochMilli();

    MessageHeaders messageHeaders = mock(MessageHeaders.class);
    when(message.getHeaders()).thenReturn(messageHeaders);

    when(messageHeaders.getTimestamp()).thenReturn(timeStamp);

    return message;
  }
}
