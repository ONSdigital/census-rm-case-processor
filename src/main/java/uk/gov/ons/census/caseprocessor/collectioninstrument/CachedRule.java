package uk.gov.ons.census.caseprocessor.collectioninstrument;

import lombok.Value;
import org.springframework.expression.Expression;

@Value
public class CachedRule {
  Expression spelExpression; // This is the main thing we want to cache in memory, for performance
  int priority;
  String collectionInstrumentUrl;
}
