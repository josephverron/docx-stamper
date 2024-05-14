package org.wickedsource.docxstamper.processor.repeat;

import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;
import org.wickedsource.docxstamper.processor.BaseCommentProcessor;
import org.wickedsource.docxstamper.util.ParagraphUtil;
import org.wickedsource.docxstamper.util.SectionUtil;
import pro.verron.officestamper.api.*;
import pro.verron.officestamper.core.CommentUtil;
import pro.verron.officestamper.core.PlaceholderReplacer;
import pro.verron.officestamper.core.StandardParagraph;
import pro.verron.officestamper.preset.Resolvers;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;

import static java.util.Collections.singletonList;

/**
 * This class is used to repeat paragraphs and tables.
 * <p>
 * It is used internally by the DocxStamper and should not be instantiated by
 * clients.
 *
 * @author Joseph Verron
 * @author Youssouf Naciri
 * @version ${version}
 * @since 1.2.2
 */
public class ParagraphRepeatProcessor
        extends BaseCommentProcessor
        implements IParagraphRepeatProcessor {
    private final Supplier<? extends List<? extends P>> nullSupplier;
    private Map<P, Paragraphs> pToRepeat = new HashMap<>();

    /**
     * @param placeholderReplacer replaces placeholders with values
     * @param nullSupplier        supplies a list of paragraphs if the list of objects to repeat is null
     */
    private ParagraphRepeatProcessor(
            ParagraphPlaceholderReplacer placeholderReplacer,
            Supplier<? extends List<? extends P>> nullSupplier
    ) {
        super(placeholderReplacer);
        this.nullSupplier = nullSupplier;
    }

    /**
     * <p>newInstance.</p>
     *
     * @param pr              replaces expressions with values
     * @param nullReplacement replaces null values
     *
     * @return a new instance of ParagraphRepeatProcessor
     *
     * @deprecated use {@link ParagraphRepeatProcessor#newInstance(ParagraphPlaceholderReplacer)} for instantiation
     * and {@link OfficeStamperConfiguration#addResolver(ObjectResolver)} with
     * {@link Resolvers#nullToDefault(String)} instead
     */
    @Deprecated(since = "1.6.8", forRemoval = true)
    public static CommentProcessor newInstance(
            PlaceholderReplacer pr,
            String nullReplacement
    ) {
        return new ParagraphRepeatProcessor(pr,
                () -> singletonList(ParagraphUtil.create(
                        nullReplacement)));
    }

    /**
     * <p>newInstance.</p>
     *
     * @param placeholderReplacer replaces expressions with values
     *
     * @return a new instance of ParagraphRepeatProcessor
     */
    public static CommentProcessor newInstance(ParagraphPlaceholderReplacer placeholderReplacer) {
        return new ParagraphRepeatProcessor(placeholderReplacer,
                Collections::emptyList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void repeatParagraph(List<Object> objects) {
        P paragraph = getParagraph();

        Deque<P> paragraphs = getParagraphsInsideComment(paragraph);

        Paragraphs toRepeat = new Paragraphs();
        toRepeat.comment = getCurrentCommentWrapper();
        toRepeat.data = new ArrayDeque<>(objects);
        toRepeat.paragraphs = paragraphs;
        toRepeat.sectionBreakBefore = SectionUtil.getPreviousSectionBreakIfPresent(
                paragraph,
                (ContentAccessor) paragraph.getParent());
        toRepeat.firstParagraphSectionBreak = SectionUtil.getParagraphSectionBreak(
                paragraph);
        toRepeat.hasOddSectionBreaks = SectionUtil.isOddNumberOfSectionBreaks(
                new ArrayList<>(toRepeat.paragraphs));

        if (paragraph.getPPr() != null && paragraph.getPPr()
                                                   .getSectPr() != null) {
            // we need to clear the first paragraph's section break to be able to control how to repeat it
            paragraph.getPPr()
                     .setSectPr(null);
        }
        pToRepeat.put(paragraph, toRepeat);
    }

    /**
     * Returns all paragraphs inside the comment of the given paragraph.
     * <p>
     * If the paragraph is not inside a comment, the given paragraph is returned.
     *
     * @param paragraph the paragraph to analyze
     *
     * @return all paragraphs inside the comment of the given paragraph
     */
    public static Deque<P> getParagraphsInsideComment(P paragraph) {
        BigInteger commentId = null;
        boolean foundEnd = false;

        Deque<P> paragraphs = new ArrayDeque<>();
        paragraphs.add(paragraph);

        for (Object object : paragraph.getContent()) {
            if (object instanceof CommentRangeStart crs)
                commentId = crs.getId();
            if (object instanceof CommentRangeEnd cre && Objects.equals(
                    commentId,
                    cre.getId())) foundEnd = true;
        }
        if (foundEnd || commentId == null) return paragraphs;

        Object parent = paragraph.getParent();
        if (parent instanceof ContentAccessor contentAccessor) {
            int index = contentAccessor.getContent()
                                       .indexOf(paragraph);
            for (int i = index + 1; i < contentAccessor.getContent()
                                                       .size() && !foundEnd; i++) {
                Object next = contentAccessor.getContent()
                                             .get(i);

                if (next instanceof CommentRangeEnd cre && cre.getId()
                                                              .equals(commentId)) {
                    foundEnd = true;
                }
                else {
                    if (next instanceof P p) {
                        paragraphs.add(p);
                    }
                    if (next instanceof ContentAccessor childContent) {
                        for (Object child : childContent.getContent()) {
                            if (child instanceof CommentRangeEnd cre && cre.getId()
                                                                           .equals(commentId)) {
                                foundEnd = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return paragraphs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commitChanges(WordprocessingMLPackage document) {
        for (Map.Entry<P, Paragraphs> entry : pToRepeat.entrySet()) {
            P currentP = entry.getKey();
            ContentAccessor parent = (ContentAccessor) currentP.getParent();
            List<Object> parentContent = parent.getContent();
            int index = parentContent.indexOf(currentP);
            if (index < 0) throw new OfficeStamperException("Impossible");

            Paragraphs paragraphsToRepeat = entry.getValue();
            Deque<Object> expressionContexts = Objects.requireNonNull(
                    paragraphsToRepeat).data;
            Deque<P> collection = expressionContexts == null
                    ? new ArrayDeque<>(nullSupplier.get())
                    : generateParagraphsToAdd(document,
                            paragraphsToRepeat,
                            expressionContexts);
            restoreFirstSectionBreakIfNeeded(paragraphsToRepeat, collection);
            parentContent.addAll(index, collection);
            parentContent.removeAll(paragraphsToRepeat.paragraphs);
        }
    }

    private Deque<P> generateParagraphsToAdd(
            WordprocessingMLPackage document,
            Paragraphs paragraphs,
            Deque<Object> expressionContexts
    ) {
        Deque<P> paragraphsToAdd = new ArrayDeque<>();

        Object lastExpressionContext = expressionContexts.peekLast();
        P lastParagraph = paragraphs.paragraphs.peekLast();

        for (Object expressionContext : expressionContexts) {
            for (P paragraphToClone : paragraphs.paragraphs) {
                P pClone = XmlUtils.deepCopy(paragraphToClone);

                if (paragraphs.sectionBreakBefore != null
                        && paragraphs.hasOddSectionBreaks
                        && expressionContext != lastExpressionContext
                        && paragraphToClone == lastParagraph
                ) {
                    SectionUtil.applySectionBreakToParagraph(paragraphs.sectionBreakBefore,
                            pClone);
                }

                CommentUtil.deleteCommentFromElements(pClone.getContent(),
                        paragraphs.comment.getComment()
                                          .getId());
                placeholderReplacer.resolveExpressionsForParagraph(
                        new StandardParagraph(pClone),
                        expressionContext,
                        document
                );
                paragraphsToAdd.add(pClone);
            }
        }
        return paragraphsToAdd;
    }

    private static void restoreFirstSectionBreakIfNeeded(
            Paragraphs paragraphs,
            Deque<P> paragraphsToAdd
    ) {
        if (paragraphs.firstParagraphSectionBreak != null) {
            P breakP = paragraphsToAdd.getLast();
            SectionUtil.applySectionBreakToParagraph(paragraphs.firstParagraphSectionBreak,
                    breakP);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        pToRepeat = new HashMap<>();
    }

    private static class Paragraphs {
        Comment comment;
        Deque<Object> data;
        Deque<P> paragraphs;
        // hasOddSectionBreaks is true if the paragraphs to repeat contain an odd number of section breaks
        // changing the layout, false otherwise
        boolean hasOddSectionBreaks;
        // section break right before the first paragraph to repeat if present, or null
        SectPr sectionBreakBefore;
        // section break on the first paragraph to repeat if present, or null
        SectPr firstParagraphSectionBreak;
    }
}
