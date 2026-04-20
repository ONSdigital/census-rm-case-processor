package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.Map;
import lombok.Data;

@Data
public class SkippedMessage {
  private String messageHash;
  private byte[] messagePayload;
  private String service;
  private String subscription;
  private String routingKey;
  private String contentType;
  private Map headers;
}
