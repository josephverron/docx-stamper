package org.wickedsource.docxstamper.processor.replaceExpression;

/**
 * Interface for processors that replace a single word with an expression defined
 * in a comment.
 *
 * @author Joseph Verron
 * @author Tom Hombergs
 * @version ${version}
 * @since 1.0.8
 */
public interface IReplaceWithProcessor {

    /**
     * May be called to replace a single word inside a paragraph with an expression
     * defined in a comment. The comment must be applied to a single word for the
     * replacement to take effect!
     *
     * @param expression the expression to replace the text with
     */
    void replaceWordWith(String expression);
}
