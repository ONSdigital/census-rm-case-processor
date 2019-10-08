package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class Peek {
  private String messageHash;
  private byte[] messagePayload;
}
