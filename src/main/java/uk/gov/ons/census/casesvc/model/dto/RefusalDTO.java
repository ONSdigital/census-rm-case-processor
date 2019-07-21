package uk.gov.ons.census.casesvc.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.ons.census.casesvc.model.entity.RefusalType;

@Data
@NoArgsConstructor
public class RefusalDTO extends BaseDTO {

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("caseId")
  private String caseId;

  private RefusalType type;

  private String report;

  private String agentId;

  private CollectionCase collectionCase;

  private String questionnaireId;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("dateTime")
  private OffsetDateTime responseDateTime;
}
