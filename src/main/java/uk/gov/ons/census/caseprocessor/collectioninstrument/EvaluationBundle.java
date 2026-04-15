package uk.gov.ons.census.caseprocessor.collectioninstrument;

import lombok.Value;
import uk.gov.ons.ssdc.common.model.entity.Case;

@Value
public class EvaluationBundle {
  Case caze;
  Object uacMetadata;
}
