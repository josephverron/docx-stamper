package pro.verron.officestamper.core;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Comments;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.lang.Nullable;
import pro.verron.officestamper.api.Comment;
import pro.verron.officestamper.api.CommentProcessor;
import pro.verron.officestamper.api.OfficeStamperException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static pro.verron.officestamper.core.CommentUtil.getCommentString;
import static pro.verron.officestamper.core.CommentUtil.getComments;

/**
 * Allows registration of {@link CommentProcessor} objects. Each registered
 * ICommentProcessor must implement an interface which has to be specified at
 * registration time. Provides several getter methods to access the registered
 * {@link CommentProcessor}.
 *
 * @author Joseph Verron
 * @author Tom Hombergs
 * @version ${version}
 * @since 1.0.0
 */
public class CommentProcessorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(CommentProcessorRegistry.class);

    private final Map<Class<?>, Object> commentProcessors;
    private final boolean failOnUnresolvedExpression;
    private final ExpressionResolver expressionResolver;

    /**
     * Creates a new CommentProcessorRegistry.
     *
     * @param expressionResolver         the expression resolver
     * @param commentProcessors          the comment processors
     * @param failOnUnresolvedExpression whether to fail on unresolved expressions
     */
    public CommentProcessorRegistry(
            ExpressionResolver expressionResolver,
            Map<Class<?>, Object> commentProcessors,
            boolean failOnUnresolvedExpression
    ) {
        this.expressionResolver = expressionResolver;
        this.commentProcessors = commentProcessors;
        this.failOnUnresolvedExpression = failOnUnresolvedExpression;
    }

    /**
     * Lets each registered ICommentProcessor run on the specified docx
     * document. At the end of the document, the commit method is called for each
     * ICommentProcessor. The ICommentProcessors are run in the order they were
     * registered.
     *
     * @param document          the docx document over which to run the registered ICommentProcessors.
     * @param expressionContext the context root object
     * @param <T>               a T class
     */
    public <T> void runProcessors(
            WordprocessingMLPackage document, T expressionContext
    ) {
        var comments = getComments(document);
        var proceedComments = new ArrayList<Comment>();

        new BaseCoordinatesWalker() {
            @Override protected void onRun(R run, P paragraph) {
                runProcessorsOnRunComment(document, comments, expressionContext, paragraph, run)
                        .ifPresent(proceedComments::add);
            }

            @Override protected void onParagraph(P paragraph) {
                runProcessorsOnParagraphComment(document, comments, expressionContext, paragraph)
                        .ifPresent(proceedComments::add);
                runProcessorsOnInlineContent(expressionContext, paragraph);
            }

        }.walk(document);

        for (Object processor : commentProcessors.values()) {
            ((CommentProcessor) processor).commitChanges(document);
        }
        for (Comment comment : proceedComments) {
            CommentUtil.deleteComment(comment);
        }
    }

    private <T> Optional<Comment> runProcessorsOnRunComment(
            WordprocessingMLPackage document,
            Map<BigInteger, Comment> comments,
            T expressionContext,
            P paragraph,
            R run
    ) {
        return CommentUtil
                .getCommentAround(run, document)
                .flatMap(c -> runCommentProcessors(comments, expressionContext, c, paragraph, run));
    }

    /**
     * Takes the first comment on the specified paragraph and tries to evaluate
     * the string within the comment against all registered
     * {@link CommentProcessor}s.
     *
     * @param document          the Word document.
     * @param comments          the comments within the document.
     * @param expressionContext the context root object
     * @param paragraph         the paragraph whose comments to evaluate.
     * @param <T>               the type of the context root object.
     */
    private <T> Optional<Comment> runProcessorsOnParagraphComment(
            WordprocessingMLPackage document,
            Map<BigInteger, Comment> comments,
            T expressionContext,
            P paragraph
    ) {
        return CommentUtil
                .getCommentFor(paragraph, document)
                .flatMap(c -> runCommentProcessors(comments, expressionContext, c, paragraph, null));
    }

    /**
     * Finds all processor expressions within the specified paragraph and tries
     * to evaluate it against all registered {@link CommentProcessor}s.
     *
     * @param expressionContext a builder for a proxy around the context root object to customize its interface
     * @param paragraph         the paragraph to process.
     * @param <T>               type of the context root object
     */
    private <T> void runProcessorsOnInlineContent(
            T expressionContext, P paragraph
    ) {
        var paragraphWrapper = new StandardParagraph(paragraph);
        String text = paragraphWrapper.asString();
        var expressions = Placeholders.findProcessors(text);

        for (var expression : expressions) {
            for (final Object processor : commentProcessors.values()) {
                ((CommentProcessor) processor).setParagraph(paragraph);
            }

            try {
                expressionResolver.resolve(expression, expressionContext);
                paragraphWrapper.replace(expression, RunUtil.create(""));
                logger.debug("Processor expression '{}' has been successfully processed by a comment processor.",
                        expression);
            } catch (SpelEvaluationException | SpelParseException e) {
                String msg = "Expression '%s' failed since no processor solves it".formatted(expression);
                if (failOnUnresolvedExpression) {
                    throw new OfficeStamperException(msg, e);
                }
                else {
                    logger.warn(msg, e);
                }
            }
        }
    }

    private <T> Optional<Comment> runCommentProcessors(
            Map<BigInteger, Comment> comments,
            T expressionContext,
            Comments.Comment comment,
            P paragraph,
            @Nullable R run
    ) {
        Comment commentWrapper = comments.get(comment.getId());

        if (Objects.isNull(commentWrapper)) {
            // no comment to process
            return Optional.empty();
        }

        var commentExpression = getCommentString(comment);

        for (final Object processor : commentProcessors.values()) {
            ((CommentProcessor) processor).setParagraph(paragraph);
            ((CommentProcessor) processor).setCurrentRun(run);
            ((CommentProcessor) processor).setCurrentCommentWrapper(commentWrapper);
        }

        try {
            expressionResolver.resolve(commentExpression, expressionContext);
            comments.remove(comment.getId());
            logger.debug("Comment {} has been successfully processed by a comment processor.", commentExpression);
            return Optional.of(commentWrapper);
        } catch (SpelEvaluationException | SpelParseException e) {
            if (failOnUnresolvedExpression) {
                throw new OfficeStamperException(commentExpression.toString(), e);
            }
            else {
                logger.warn(String.format(
                        "Skipping comment expression '%s' because it can not be resolved by any comment processor. "
                                + "Reason: %s. Set log level to TRACE to view Stacktrace.",
                        commentExpression,
                        e.getMessage()));
                logger.trace("Reason for skipping comment: ", e);
            }
        }
        return Optional.empty();
    }

    /**
     * Resets all registered ICommentProcessors.
     */
    public void reset() {
        for (Object processor : commentProcessors.values()) {
            ((CommentProcessor) processor).reset();
        }
    }
}
