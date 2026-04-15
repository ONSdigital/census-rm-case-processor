package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class EqFlushTaskPayload {
  private String qid;
  private UUID transactionId;
}
