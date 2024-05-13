package org.wickedsource.docxstamper.processor.repeat;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.P;
import org.docx4j.wml.Tr;
import org.wickedsource.docxstamper.util.walk.BaseDocumentWalker;
import pro.verron.officestamper.api.ParagraphPlaceholderReplacer;
import pro.verron.officestamper.core.StandardParagraph;

/**
 * Walks through a document and replaces expressions with values from the given
 * expression context.
 * This walker only replaces expressions in paragraphs, not in tables.
 *
 * @author Joseph Verron
 * @version ${version}
 * @since 1.4.7
 */
public class ParagraphResolverDocumentWalker
        extends BaseDocumentWalker {
    private final Object expressionContext;
    private final WordprocessingMLPackage document;
    private final ParagraphPlaceholderReplacer placeholderReplacer;

    /**
     * <p>Constructor for ParagraphResolverDocumentWalker.</p>
     *
     * @param rowClone          The row to start with
     * @param expressionContext The context of the expressions to resolve
     * @param document          The document to walk through
     * @param replacer          The placeholderReplacer to use for resolving
     */
    public ParagraphResolverDocumentWalker(
            Tr rowClone,
            Object expressionContext,
            WordprocessingMLPackage document,
            ParagraphPlaceholderReplacer replacer
    ) {
        super(rowClone);
        this.expressionContext = expressionContext;
        this.document = document;
        this.placeholderReplacer = replacer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onParagraph(P paragraph) {
        placeholderReplacer.resolveExpressionsForParagraph(
                new StandardParagraph(paragraph),
                expressionContext, document
        );
    }
}
