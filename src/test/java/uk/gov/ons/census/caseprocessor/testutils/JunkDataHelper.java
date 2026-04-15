package uk.gov.ons.census.caseprocessor.testutils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.ons.census.caseprocessor.model.dto.EventHeaderDTO;
import uk.gov.ons.census.caseprocessor.model.repository.CaseRepository;
import uk.gov.ons.census.caseprocessor.model.repository.CollectionExerciseRepository;
import uk.gov.ons.census.caseprocessor.model.repository.ExportFileTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentSurveyExportFileTemplateRepository;
import uk.gov.ons.census.caseprocessor.model.repository.SurveyRepository;
import uk.gov.ons.ssdc.common.model.entity.Case;
import uk.gov.ons.ssdc.common.model.entity.CollectionExercise;
import uk.gov.ons.ssdc.common.model.entity.CollectionInstrumentSelectionRule;
import uk.gov.ons.ssdc.common.model.entity.ExportFileTemplate;
import uk.gov.ons.ssdc.common.model.entity.FulfilmentSurveyExportFileTemplate;
import uk.gov.ons.ssdc.common.model.entity.Survey;
import uk.gov.ons.ssdc.common.validation.ColumnValidator;
import uk.gov.ons.ssdc.common.validation.MandatoryRule;
import uk.gov.ons.ssdc.common.validation.Rule;

@Component
@ActiveProfiles("test")
public class JunkDataHelper {
  private static final Random RANDOM = new Random();

  @Autowired private CaseRepository caseRepository;
  @Autowired private CollectionExerciseRepository collectionExerciseRepository;
  @Autowired private SurveyRepository surveyRepository;
  @Autowired private ExportFileTemplateRepository exportFileTemplateRepository;

  @Autowired
  private FulfilmentSurveyExportFileTemplateRepository fulfilmentSurveyExportFileTemplateRepository;

  public Case setupJunkCase() {
    Case junkCase = new Case();
    junkCase.setId(UUID.randomUUID());
    junkCase.setInvalid(false);
    junkCase.setCollectionExercise(setupJunkCollex());
    junkCase.setCaseRef(RANDOM.nextLong());
    junkCase.setSample(Map.of("foo", "bar"));
    junkCase.setSampleSensitive(Map.of("phoneNumber", "123", "emailAddress", "junk@junk.com"));
    caseRepository.save(junkCase);

    return junkCase;
  }

  public ColumnValidator[] setUpColumnValidatorsMandatory(
      List<String> columnNames, boolean sensitive) {
    ColumnValidator[] columnValidators = new ColumnValidator[columnNames.size()];

    for (int i = 0; i < columnNames.size(); i++) {
      columnValidators[i] =
          new ColumnValidator(columnNames.get(i), sensitive, new Rule[] {new MandatoryRule()});
    }

    return columnValidators;
  }

  public CollectionExercise setupJunkCollex() {
    return setUpJunkCollexWithThisColumnValidators(
        new ColumnValidator[] {
          new ColumnValidator("Junk", false, new Rule[] {new MandatoryRule()}),
          new ColumnValidator("SensitiveJunk", true, new Rule[] {new MandatoryRule()})
        });
  }

  public CollectionExercise setUpJunkCollexWithThisColumnValidators(
      ColumnValidator[] columnValidators) {
    Survey junkSurvey = new Survey();
    junkSurvey.setId(UUID.randomUUID());
    junkSurvey.setName("Junk survey");
    junkSurvey.setSampleValidationRules(columnValidators);
    junkSurvey.setSampleSeparator('j');
    junkSurvey.setSampleDefinitionUrl("http://junk");
    surveyRepository.saveAndFlush(junkSurvey);

    CollectionExercise junkCollectionExercise = new CollectionExercise();
    junkCollectionExercise.setId(UUID.randomUUID());
    junkCollectionExercise.setName("Junk collex");
    junkCollectionExercise.setSurvey(junkSurvey);
    junkCollectionExercise.setReference("MVP012021");
    junkCollectionExercise.setStartDate(OffsetDateTime.now());
    junkCollectionExercise.setEndDate(OffsetDateTime.now().plusDays(2));
    junkCollectionExercise.setMetadata(null);
    junkCollectionExercise.setCollectionInstrumentSelectionRules(
        new CollectionInstrumentSelectionRule[] {
          new CollectionInstrumentSelectionRule(0, null, "junkCollectionInstrumentUrl", null)
        });
    collectionExerciseRepository.saveAndFlush(junkCollectionExercise);

    return junkCollectionExercise;
  }

  public ExportFileTemplate setUpJunkExportFileTemplate(String[] template) {
    ExportFileTemplate junkExportFileTemplate = new ExportFileTemplate();
    junkExportFileTemplate.setExportFileDestination("junk");
    junkExportFileTemplate.setPackCode("JUNK");
    junkExportFileTemplate.setTemplate(template);
    junkExportFileTemplate.setDescription("junk");
    exportFileTemplateRepository.saveAndFlush(junkExportFileTemplate);
    return junkExportFileTemplate;
  }

  public void linkExportFileTemplateToSurveyFulfilment(
      ExportFileTemplate exportFileTemplate, Survey survey) {
    FulfilmentSurveyExportFileTemplate fulfilmentSurveyExportFileTemplate =
        new FulfilmentSurveyExportFileTemplate();
    fulfilmentSurveyExportFileTemplate.setSurvey(survey);
    fulfilmentSurveyExportFileTemplate.setExportFileTemplate(exportFileTemplate);
    fulfilmentSurveyExportFileTemplate.setId(UUID.randomUUID());
    fulfilmentSurveyExportFileTemplateRepository.saveAndFlush(fulfilmentSurveyExportFileTemplate);
  }

  public void junkify(EventHeaderDTO eventHeaderDTO) {
    if (eventHeaderDTO.getChannel() == null) {
      eventHeaderDTO.setChannel("Junk");
    }

    if (eventHeaderDTO.getSource() == null) {
      eventHeaderDTO.setSource("Junk");
    }

    if (eventHeaderDTO.getCorrelationId() == null) {
      eventHeaderDTO.setCorrelationId(UUID.randomUUID());
    }

    if (eventHeaderDTO.getMessageId() == null) {
      eventHeaderDTO.setMessageId(UUID.randomUUID());
    }

    if (eventHeaderDTO.getDateTime() == null) {
      eventHeaderDTO.setDateTime(OffsetDateTime.now());
    }
  }
}
