package pro.verron.officestamper.preset;

import pro.verron.officestamper.api.OfficeStamperConfiguration;
import pro.verron.officestamper.core.DocxStamperConfiguration;

/**
 * The OfficeStamperConfigurations class provides static methods
 * to create different configurations for the OfficeStamper.
 */
public class OfficeStamperConfigurations {

    /**
     * Creates a new OfficeStamperConfiguration with the standard configuration and additional preprocessors.
     *
     * @return the OfficeStamperConfiguration
     *
     * @see OfficeStamperConfiguration
     */
    public static OfficeStamperConfiguration standardWithPreprocessing() {
        var configuration = standard();
        configuration.addPreprocessor(Preprocessors.removeLanguageProof());
        configuration.addPreprocessor(Preprocessors.mergeSimilarRuns());
        return configuration;
    }

    /**
     * Creates a new standard OfficeStamperConfiguration.
     *
     * @return the standard OfficeStamperConfiguration
     */
    public static OfficeStamperConfiguration standard() {
        return new DocxStamperConfiguration();
    }

    /**
     * Creates a new standard OfficeStamperConfiguration.
     *
     * @return the standard OfficeStamperConfiguration
     */
    public static OfficeStamperConfiguration raw() {
        var configuration = new DocxStamperConfiguration();
        configuration.resetResolvers();
        configuration.setEvaluationContextConfigurer(EvaluationContextConfigurers.defaultConfigurer());
        configuration.resetCommentProcessors();
        return configuration;
    }
}
