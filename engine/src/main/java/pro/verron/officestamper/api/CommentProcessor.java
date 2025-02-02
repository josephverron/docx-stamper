package pro.verron.officestamper.api;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.springframework.lang.Nullable;

/**
 * CommentProcessor is an interface that defines the methods for processing comments in a .docx template.
 */
public interface CommentProcessor {

    void setProcessorContext(ProcessorContext processorContext);

    /**
     * Passes the run that is currently being processed (i.e., the run that is commented in the
     * .docx template). This method is always called BEFORE the custom
     * methods of the custom comment processor interface
     * are called.
     *
     * @param run coordinates of the currently processed run within the template.
     */
    void setCurrentRun(@Nullable R run);

    /**
     * This method is called after all comments in the .docx template have been passed to the comment processor.
     * All manipulations of the .docx document SHOULD BE done in this method.
     * If certain manipulations are already done
     * within the custom methods of a comment processor,
     * the ongoing iteration over the paragraphs in the document
     * may be disturbed.
     * <p>
     * This method replaces the previous {@link #commitChanges(WordprocessingMLPackage)} and called with a DocxPart
     * as the parameter.
     *
     * @param docxPart The DocxPart that can be manipulated by using the DOCX4J api.
     */
    default void commitChanges(DocxPart docxPart) {
        commitChanges(docxPart.document());
    }

    /**
     * This method is called after all comments in the .docx template have been passed to the comment processor.
     * All manipulations of the .docx document SHOULD BE done in this method.
     * If certain manipulations are already done
     * within the custom methods of a comment processor,
     * the ongoing iteration over the paragraphs in the document
     * may be disturbed.
     * <p>
     * This method replaces the previous {@link #commitChanges(DocxPart)} and called with a DocxPart
     * as the parameter.
     *
     * @param document The document that can be manipulated by using the DOCX4J api.
     *
     * @deprecated replaced by {@link #commitChanges(DocxPart)}
     */
    @Deprecated(since = "2.3", forRemoval = true) default void commitChanges(WordprocessingMLPackage document) {
        throw new OfficeStamperException("Should not be called since deprecation, only legacy implementations have a "
                                         + "reason to keep implementing this");
    }

    Paragraph getParagraph();

    /**
     * Passes the paragraph that is currently being processed (i.e., the paragraph that is commented in the
     * .docx template). This method is always called BEFORE the custom
     * methods of the custom comment processor interface
     * are called.
     *
     * @param paragraph coordinates of the currently processed paragraph within the template.
     *
     * @deprecated use {@link #setProcessorContext(ProcessorContext)} instead
     */
    @Deprecated(since = "2.6", forRemoval = true)
    void setParagraph(P paragraph);

    /**
     * Passes the comment range wrapper that is currently being processed
     * (i.e., the start and end of comment that in the .docx template).
     * This method is always called BEFORE the custom methods of the custom comment
     * processor interface are called.
     *
     * @param comment of the currently processed comment within the template.
     */
    void setCurrentCommentWrapper(Comment comment);

    /**
     * Resets all states in the comment processor so that it can be re-used in another stamping process.
     */
    void reset();
}
