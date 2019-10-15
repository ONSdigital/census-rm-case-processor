package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.exception.ListenerExecutionFailedException;
import uk.gov.ons.census.casesvc.client.ExceptionManagerClient;
import uk.gov.ons.census.casesvc.model.dto.ExceptionReportResponse;
import uk.gov.ons.census.casesvc.model.dto.SkippedMessage;

public class ManagedMessageRecovererTest {
  private static final String MESSAGE_HASH =
      "4f1ec3a5f36117da0e9ba42c2eda77dea47b279358a7b2bb538a51d3e13bd229";

  @Test
  public void testRecover() {
    ExceptionManagerClient exceptionManagerClient = mock(ExceptionManagerClient.class);
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    ManagedMessageRecoverer underTest =
        new ManagedMessageRecoverer(
            exceptionManagerClient,
            Object.class,
            false,
            "test service",
            "test queue",
            "test delay exchange",
            "test quarantine exchange",
            rabbitTemplate);

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

    underTest.recover(message, failedException);

    verify(exceptionManagerClient)
        .reportException(
            eq(MESSAGE_HASH), eq("test service"), eq("test queue"), eq(cause.getCause()));

    verify(rabbitTemplate).send(eq("test delay exchange"), eq("test queue"), eq(message));

    verifyNoMoreInteractions(exceptionManagerClient);
    verifyNoMoreInteractions(rabbitTemplate);
  }

  @Test
  public void testRecoverExceptionManagerUnavailable() {
    ExceptionManagerClient exceptionManagerClient = mock(ExceptionManagerClient.class);
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    ManagedMessageRecoverer underTest =
        new ManagedMessageRecoverer(
            exceptionManagerClient,
            Object.class,
            false,
            "test service",
            "test queue",
            "test delay exchange",
            "test quarantine exchange",
            rabbitTemplate);

    Message message = new Message("test message body".getBytes(), new MessageProperties());
    Throwable cause = new Exception(new RuntimeException());
    ListenerExecutionFailedException failedException =
        new ListenerExecutionFailedException("test error message", cause, message);

    when(exceptionManagerClient.reportException(any(), any(), any(), any()))
        .thenThrow(new RuntimeException());

    underTest.recover(message, failedException);

    verify(exceptionManagerClient)
        .reportException(
            eq(MESSAGE_HASH), eq("test service"), eq("test queue"), eq(cause.getCause()));

    verify(rabbitTemplate).send(eq("test delay exchange"), eq("test queue"), eq(message));

    verifyNoMoreInteractions(exceptionManagerClient);
    verifyNoMoreInteractions(rabbitTemplate);
  }

  @Test
  public void testRecoverQuarantine() {
    ExceptionManagerClient exceptionManagerClient = mock(ExceptionManagerClient.class);
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    ManagedMessageRecoverer underTest =
        new ManagedMessageRecoverer(
            exceptionManagerClient,
            Object.class,
            false,
            "test service",
            "test queue",
            "test delay exchange",
            "test quarantine exchange",
            rabbitTemplate);

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

    InOrder inOrder = inOrder(exceptionManagerClient, rabbitTemplate);

    ArgumentCaptor<SkippedMessage> skippedMessageArgumentCaptor =
        ArgumentCaptor.forClass(SkippedMessage.class);
    inOrder
        .verify(exceptionManagerClient)
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

    inOrder
        .verify(rabbitTemplate)
        .send(eq("test quarantine exchange"), eq("test queue"), eq(message));

    verifyNoMoreInteractions(exceptionManagerClient);
    verifyNoMoreInteractions(rabbitTemplate);
  }

  @Test
  public void testRecoverPeek() {
    ExceptionManagerClient exceptionManagerClient = mock(ExceptionManagerClient.class);
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    ManagedMessageRecoverer underTest =
        new ManagedMessageRecoverer(
            exceptionManagerClient,
            Object.class,
            false,
            "test service",
            "test queue",
            "test delay exchange",
            "test quarantine exchange",
            rabbitTemplate);

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

    underTest.recover(message, failedException);

    verify(exceptionManagerClient)
        .reportException(
            eq(MESSAGE_HASH), eq("test service"), eq("test queue"), eq(cause.getCause()));

    verify(exceptionManagerClient)
        .respondToPeek(eq(MESSAGE_HASH), eq("test message body".getBytes()));

    verify(rabbitTemplate).send(eq("test delay exchange"), eq("test queue"), eq(message));

    verifyNoMoreInteractions(exceptionManagerClient);
    verifyNoMoreInteractions(rabbitTemplate);
  }
}
