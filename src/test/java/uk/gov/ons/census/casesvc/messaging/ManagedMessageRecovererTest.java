package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.exception.ListenerExecutionFailedException;
import uk.gov.ons.census.casesvc.client.ExceptionManagerClient;
import uk.gov.ons.census.casesvc.model.dto.ExceptionReportResponse;
import uk.gov.ons.census.casesvc.model.dto.SkippedMessage;

public class ManagedMessageRecovererTest {

  private static final String MESSAGE_HASH =
      "4f1ec3a5f36117da0e9ba42c2eda77dea47b279358a7b2bb538a51d3e13bd229";

  @Test(expected = AmqpRejectAndDontRequeueException.class)
  public void testRecover() {
    // Given
    ExceptionManagerClient exceptionManagerClient = mock(ExceptionManagerClient.class);
    ManagedMessageRecoverer underTest =
        new ManagedMessageRecoverer(
            exceptionManagerClient, Object.class, false, "test service", "test queue");

    Message message = new Message("test message body".getBytes(), new MessageProperties());
    Throwable cause = new Exception(new RuntimeException());
    ListenerExecutionFailedException failedException =
        new ListenerExecutionFailedException("test error message", cause, message);

    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setLogIt(true);
    exceptionReportResponse.setPeek(false);
    exceptionReportResponse.setSkipIt(false);
    when(exceptionManagerClient.reportException(any(), any(), any(), any()))
        .thenReturn(exceptionReportResponse);

    // When
    try {
      underTest.recover(message, failedException);
    } catch (AmqpRejectAndDontRequeueException expectedException) {

      // Then
      verify(exceptionManagerClient)
          .reportException(
              eq(MESSAGE_HASH), eq("test service"), eq("test queue"), eq(cause.getCause()));

      verifyNoMoreInteractions(exceptionManagerClient);

      throw expectedException;
    }
  }

  @Test(expected = AmqpRejectAndDontRequeueException.class)
  public void testRecoverExceptionManagerUnavailable() {
    ExceptionManagerClient exceptionManagerClient = mock(ExceptionManagerClient.class);
    ManagedMessageRecoverer underTest =
        new ManagedMessageRecoverer(
            exceptionManagerClient, Object.class, false, "test service", "test queue");

    Message message = new Message("test message body".getBytes(), new MessageProperties());
    Throwable cause = new Exception(new RuntimeException());
    ListenerExecutionFailedException failedException =
        new ListenerExecutionFailedException("test error message", cause, message);

    when(exceptionManagerClient.reportException(any(), any(), any(), any()))
        .thenThrow(new RuntimeException());

    try {
      underTest.recover(message, failedException);
    } catch (AmqpRejectAndDontRequeueException expectedException) {

      verify(exceptionManagerClient)
          .reportException(
              eq(MESSAGE_HASH), eq("test service"), eq("test queue"), eq(cause.getCause()));

      verifyNoMoreInteractions(exceptionManagerClient);
      throw expectedException;
    }
  }

  @Test
  public void testRecoverQuarantine() {
    ExceptionManagerClient exceptionManagerClient = mock(ExceptionManagerClient.class);
    ManagedMessageRecoverer underTest =
        new ManagedMessageRecoverer(
            exceptionManagerClient, Object.class, false, "test service", "test queue");

    MessageProperties messageProperties = new MessageProperties();
    messageProperties.setContentType("test content type");
    messageProperties.setHeader("foo", "bar");
    messageProperties.setReceivedRoutingKey("test received routing key");
    Message message = new Message("test message body".getBytes(), messageProperties);
    Throwable cause = new Exception(new RuntimeException());
    ListenerExecutionFailedException failedException =
        new ListenerExecutionFailedException("test error message", cause, message);

    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setLogIt(true);
    exceptionReportResponse.setPeek(false);
    exceptionReportResponse.setSkipIt(true);
    when(exceptionManagerClient.reportException(any(), any(), any(), any()))
        .thenReturn(exceptionReportResponse);

    underTest.recover(message, failedException);

    verify(exceptionManagerClient)
        .reportException(
            eq(MESSAGE_HASH), eq("test service"), eq("test queue"), eq(cause.getCause()));

    ArgumentCaptor<SkippedMessage> skippedMessageArgumentCaptor =
        ArgumentCaptor.forClass(SkippedMessage.class);
    verify(exceptionManagerClient)
        .storeMessageBeforeSkipping(skippedMessageArgumentCaptor.capture());
    SkippedMessage actualSkippedMessage = skippedMessageArgumentCaptor.getValue();
    assertThat(actualSkippedMessage.getMessageHash()).isEqualTo(MESSAGE_HASH);
    assertThat(actualSkippedMessage.getContentType()).isEqualTo("test content type");
    assertThat(actualSkippedMessage.getHeaders()).isEqualTo(Map.of("foo", "bar"));
    assertThat(actualSkippedMessage.getMessagePayload())
        .isEqualTo(("test message body".getBytes()));
    assertThat(actualSkippedMessage.getQueue()).isEqualTo("test queue");
    assertThat(actualSkippedMessage.getRoutingKey()).isEqualTo("test received routing key");
    assertThat(actualSkippedMessage.getService()).isEqualTo("test service");

    verifyNoMoreInteractions(exceptionManagerClient);
  }

  @Test(expected = AmqpRejectAndDontRequeueException.class)
  public void testRecoverPeek() {
    ExceptionManagerClient exceptionManagerClient = mock(ExceptionManagerClient.class);
    ManagedMessageRecoverer underTest =
        new ManagedMessageRecoverer(
            exceptionManagerClient, Object.class, false, "test service", "test queue");

    MessageProperties messageProperties = new MessageProperties();
    messageProperties.setContentType("test content type");
    messageProperties.setHeader("foo", "bar");
    messageProperties.setReceivedRoutingKey("test received routing key");
    Message message = new Message("test message body".getBytes(), messageProperties);
    Throwable cause = new Exception(new RuntimeException());
    ListenerExecutionFailedException failedException =
        new ListenerExecutionFailedException("test error message", cause, message);

    ExceptionReportResponse exceptionReportResponse = new ExceptionReportResponse();
    exceptionReportResponse.setLogIt(false);
    exceptionReportResponse.setPeek(true);
    exceptionReportResponse.setSkipIt(false);
    when(exceptionManagerClient.reportException(any(), any(), any(), any()))
        .thenReturn(exceptionReportResponse);

    try {
      underTest.recover(message, failedException);
    } catch (AmqpRejectAndDontRequeueException expectedException) {

      verify(exceptionManagerClient)
          .reportException(
              eq(MESSAGE_HASH), eq("test service"), eq("test queue"), eq(cause.getCause()));

      verify(exceptionManagerClient)
          .respondToPeek(eq(MESSAGE_HASH), eq("test message body".getBytes()));

      verifyNoMoreInteractions(exceptionManagerClient);

      throw expectedException;
    }
  }
}
