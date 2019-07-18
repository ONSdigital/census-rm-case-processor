package uk.gov.ons.census.casesvc.utility;

import java.util.UUID;
import org.jeasy.random.EasyRandom;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.Refusal;
import uk.gov.ons.census.casesvc.model.entity.Case;

public class DataUtils {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  private static EasyRandom easyRandom;

  static {
    easyRandom = new EasyRandom();
  }

  public static Case getRandomCase() {
    Case caze = easyRandom.nextObject(Case.class);
    caze.setCaseId(TEST_CASE_ID);

    return caze;
  }

  public static Refusal getTestRefusal() {
    CollectionCase collectionCase = easyRandom.nextObject(CollectionCase.class);
    collectionCase.setId(UUID.randomUUID().toString());
    collectionCase.setRefusalReceived(false);

    Refusal refusal = easyRandom.nextObject(Refusal.class);
    refusal.setCollectionCase(collectionCase);

    return refusal;
  }
}
