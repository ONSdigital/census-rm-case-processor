package uk.gov.ons.census.casesvc.builders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import uk.gov.ons.census.casesvc.model.dto.CcsToField;
import uk.gov.ons.census.casesvc.model.entity.Case;

public class CcsToFieldBuilderTest {

  @Test
  public void testCcsToFieldBuilder() {
    EasyRandom easyRandom = new EasyRandom();
    Case caze = easyRandom.nextObject(Case.class);

    CcsToFieldBuilder underTest = new CcsToFieldBuilder();

    CcsToField actualResult =
        underTest.buildCcsToField(caze, UUID.randomUUID().toString(), UUID.randomUUID().toString());

    assertThat(actualResult.getSurveyName()).isEqualTo("CCS");
    assertThat(actualResult.getUndeliveredAsAddress()).isEqualTo(false);
    assertThat(actualResult.getBlankQreReturned()).isFalse();
    assertThat(actualResult.getCaseId()).isEqualTo(caze.getCaseId().toString());
    assertThat(actualResult.getCaseRef()).isEqualTo(Integer.toString(caze.getCaseRef()));
  }
}
