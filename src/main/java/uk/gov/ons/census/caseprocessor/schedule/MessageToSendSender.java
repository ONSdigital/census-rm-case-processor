package uk.gov.ons.census.caseprocessor.schedule;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.ssdc.common.model.entity.MessageToSend;

@Component
public class MessageToSendSender {
  private final PubSubTemplate pubSubTemplate;

  @Value("${queueconfig.publishtimeout}")
  private int publishTimeout;

  public MessageToSendSender(PubSubTemplate pubSubTemplate) {
    this.pubSubTemplate = pubSubTemplate;
  }

  public void sendMessage(MessageToSend messageToSend) {
    PubsubMessage pubsubMessage =
        PubsubMessage.newBuilder()
            .setData(ByteString.copyFromUtf8(messageToSend.getMessageBody()))
            .build();

    CompletableFuture<String> future =
        pubSubTemplate.publish(messageToSend.getDestinationTopic(), pubsubMessage);

    try {
      future.get(publishTimeout, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
}
