package uk.gov.ons.census.caseprocessor.testutils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.messaging.Message;
import uk.gov.ons.census.caseprocessor.utils.JsonHelper;

public class MessageConstructor {
  public static Message<byte[]> constructMessage(Object payload) {
    byte[] payloadBytes = JsonHelper.convertObjectToJson(payload).getBytes();
    return constructMessageInternal(payloadBytes);
  }

  private static <T> Message<T> constructMessageInternal(T msgPayload) {
    Message<T> message = mock(Message.class);
    when(message.getPayload()).thenReturn(msgPayload);
    return message;
  }
}
