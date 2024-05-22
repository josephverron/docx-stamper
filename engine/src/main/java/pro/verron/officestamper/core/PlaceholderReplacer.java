package pro.verron.officestamper.core;

import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Br;
import org.docx4j.wml.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelParseException;
import pro.verron.officestamper.api.OfficeStamperException;
import pro.verron.officestamper.api.Paragraph;
import pro.verron.officestamper.api.ParagraphPlaceholderReplacer;
import pro.verron.officestamper.api.Placeholder;

/**
 * Replaces expressions in a document with the values provided by the {@link ExpressionResolver}.
 *
 * @author Joseph Verron
 * @author Tom Hombergs
 * @version ${version}
 * @since 1.0.0
 */
public class PlaceholderReplacer
        implements ParagraphPlaceholderReplacer {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderReplacer.class);

    private final ExpressionResolver resolver;
    private final ObjectResolverRegistry registry;
    private final boolean failOnUnresolvedExpression;
    private final boolean leaveEmptyOnExpressionError;
    private final boolean replaceUnresolvedExpressions;
    private final String unresolvedExpressionsDefaultValue;
    private final Placeholder lineBreakPlaceholder;

    /**
     * <p>Constructor for PlaceholderReplacer.</p>
     *
     * @param registry                          the registry containing all available type resolvers.
     * @param resolver                          the expression resolver used to resolve expressions in the document.
     * @param failOnUnresolvedExpression        if set to true, an exception is thrown when an expression cannot be
     *                                          resolved.
     * @param replaceUnresolvedExpressions      if set to true, expressions that cannot be resolved are replaced by the
     *                                          value provided in the unresolvedExpressionsDefaultValue parameter.
     * @param unresolvedExpressionsDefaultValue the value to use when replacing unresolved expressions.
     * @param leaveEmptyOnExpressionError       if set to true, expressions
     *                                          that cannot be resolved will
     *                                          be by replaced by an
     *                                          empty string.
     * @param linebreakPlaceholder              if set to a non-null value,
     *                                          all occurrences of this placeholder will be
     *                                          replaced with a line break.
     */
    public PlaceholderReplacer(
            ObjectResolverRegistry registry,
            ExpressionResolver resolver,
            boolean failOnUnresolvedExpression,
            boolean replaceUnresolvedExpressions,
            String unresolvedExpressionsDefaultValue,
            boolean leaveEmptyOnExpressionError,
            Placeholder linebreakPlaceholder
    ) {
        this.registry = registry;
        this.resolver = resolver;
        this.failOnUnresolvedExpression = failOnUnresolvedExpression;
        this.replaceUnresolvedExpressions = replaceUnresolvedExpressions;
        this.unresolvedExpressionsDefaultValue = unresolvedExpressionsDefaultValue;
        this.leaveEmptyOnExpressionError = leaveEmptyOnExpressionError;
        this.lineBreakPlaceholder = linebreakPlaceholder;
    }

    /**
     * Finds expressions in a document and resolves them against the specified context object.
     * The resolved values will then replace the expressions in the document.
     *
     * @param document          the document in which to replace all expressions.
     * @param expressionContext the context root
     */
    public void resolveExpressions(
            final WordprocessingMLPackage document, Object expressionContext
    ) {
        DocumentUtil.streamParagraphs(document)
                    .map(StandardParagraph::new)
                    .forEach(paragraph -> resolveExpressionsForParagraph(paragraph, expressionContext, document));
    }

    /**
     * Finds expressions in the given paragraph and replaces them with the values provided by the expression resolver.
     *
     * @param paragraph the paragraph in which to replace expressions.
     * @param context   the context root
     * @param document  the document in which to replace all expressions.
     */
    @Override public void resolveExpressionsForParagraph(
            Paragraph paragraph,
            Object context,
            WordprocessingMLPackage document
    ) {
        var expressions = Placeholders.findVariables(paragraph);
        for (var expression : expressions) {
            try {
                resolver.setContext(context);
                var resolution = resolver.resolve(expression);
                var replacement = registry.resolve(document, expression, resolution);
                paragraph.replace(expression, replacement);
            } catch (SpelEvaluationException
                     | SpelParseException
                     | OfficeStamperException e
            ) {
                if (failOnUnresolvedExpression) {
                    var template = "Expression %s could not be resolved against context of type %s";
                    var message = template.formatted(expression, context.getClass());
                    throw new OfficeStamperException(message, e);
                }
                else if (leaveEmptyOnExpressionError) {
                    var template = "Expression {} seems erroneous when evaluating against root of type {}."
                            + " Reason: {}."
                            + " Set log level to TRACE to view Stacktrace.";
                    log.warn(template, expression, context.getClass(), e.getMessage());
                    log.trace("Reason for skipping expression:", e);
                    paragraph.replace(expression, RunUtil.create(""));
                }
                else if (replaceUnresolvedExpressions) {
                    log.warn("Expression {} could not be resolved against context root of type {}."
                                    + " Reason: {}. "
                                    + "Set log level to TRACE to view Stacktrace.",
                            expression,
                            context.getClass(),
                            e.getMessage());
                    log.trace("Reason for skipping expression:", e);
                    paragraph.replace(expression, RunUtil.create(unresolvedExpressionsDefaultValue));
                }
            }
        }
        replaceLineBreaks(paragraph);
    }

    private void replaceLineBreaks(Paragraph paragraph) {
        var lineBreak = getBr();
        var run = getR(lineBreak);
        paragraph.replaceAll(lineBreakPlaceholder, run);
    }

    private static Br getBr() {
        var factory = Context.getWmlObjectFactory();
        var lineBreak = factory.createBr();
        lineBreak.setType(null);
        return lineBreak;
    }

    private static R getR(Br lineBreak) {
        var factory = Context.getWmlObjectFactory();
        var run = factory.createR();
        run.getContent()
           .add(lineBreak);
        return run;
    }

}
