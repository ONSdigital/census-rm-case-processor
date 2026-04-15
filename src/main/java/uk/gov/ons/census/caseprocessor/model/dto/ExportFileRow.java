package uk.gov.ons.census.caseprocessor.model.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class ExportFileRow {
  private String row;
  private UUID batchId;
  private int batchQuantity;
  private String exportFileDestination;
  private String packCode;
}
