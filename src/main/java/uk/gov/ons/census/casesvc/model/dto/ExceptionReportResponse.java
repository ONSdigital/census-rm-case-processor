package uk.gov.ons.census.casesvc.model.dto;

import lombok.Data;

@Data
public class ExceptionReportResponse {
  private boolean peek;
  private boolean logIt;
  private boolean skipIt;
}
