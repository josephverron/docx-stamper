package org.wickedsource.docxstamper.processor.repeat;

import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.CommentsPart;
import org.docx4j.wml.*;
import org.jvnet.jaxb2_commons.ppp.Child;
import org.springframework.util.CollectionUtils;
import org.wickedsource.docxstamper.DocxStamper;
import org.wickedsource.docxstamper.DocxStamperConfiguration;
import org.wickedsource.docxstamper.api.typeresolver.TypeResolverRegistry;
import org.wickedsource.docxstamper.processor.BaseCommentProcessor;
import org.wickedsource.docxstamper.util.CommentWrapper;
import org.wickedsource.docxstamper.util.DocumentUtil;
import org.wickedsource.docxstamper.util.ParagraphUtil;
import org.wickedsource.docxstamper.util.walk.BaseDocumentWalker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

public class RepeatDocPartProcessor extends BaseCommentProcessor implements IRepeatDocPartProcessor {

    private Map<CommentWrapper, List<Object>> subContexts = new HashMap<>();
    private Map<CommentWrapper, List<Object>> repeatElementsMap = new HashMap<>();
    private Map<CommentWrapper, WordprocessingMLPackage> subTemplates = new HashMap<>();
    private Map<CommentWrapper, ContentAccessor> gcpMap = new HashMap<>();
    private Map<CommentWrapper, SectPr> previousSectionBreak = new HashMap<>();

    private static ObjectFactory objectFactory = null;

    public RepeatDocPartProcessor(DocxStamperConfiguration config, TypeResolverRegistry typeResolverRegistry) {
        super(config, typeResolverRegistry);
    }


    @Override
    public void repeatDocPart(List<Object> contexts) throws Exception {
        if (contexts == null) {
            contexts = Collections.emptyList();
        }

        CommentWrapper currentCommentWrapper = getCurrentCommentWrapper();
        ContentAccessor gcp = findGreatestCommonParent(
                currentCommentWrapper.getCommentRangeEnd().getParent(),
                (ContentAccessor) currentCommentWrapper.getCommentRangeStart().getParent()
        );
        List<Object> repeatElements = getRepeatElements(currentCommentWrapper, gcp);

        if (!repeatElements.isEmpty()) {
            subTemplates.put(currentCommentWrapper, extractSubTemplate(currentCommentWrapper, repeatElements, getOrCreateObjectFactory()));
            subContexts.put(currentCommentWrapper, contexts);
            gcpMap.put(currentCommentWrapper, gcp);
            repeatElementsMap.put(currentCommentWrapper, repeatElements);
        }
    }

    private static ObjectFactory getOrCreateObjectFactory() {
        if (objectFactory == null) {
            objectFactory = Context.getWmlObjectFactory();
        }
        return objectFactory;
    }

    private WordprocessingMLPackage copyTemplate(WordprocessingMLPackage doc) throws Docx4JException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos);
        return WordprocessingMLPackage.load(new ByteArrayInputStream(baos.toByteArray()));
    }

    @Override
    public void commitChanges(WordprocessingMLPackage document) {
        for (CommentWrapper commentWrapper : subContexts.keySet()) {
            List<Object> expressionContexts = subContexts.get(commentWrapper);

            // index changes after each replacement so we need to get the insert index at the right moment.
            ContentAccessor insertParentContentAccessor = gcpMap.get(commentWrapper);
            Integer index = insertParentContentAccessor.getContent().indexOf(repeatElementsMap.get(commentWrapper).get(0));

            if (expressionContexts != null) {
                for (Object subContext : expressionContexts) {
                    try {
                        WordprocessingMLPackage subTemplate = copyTemplate(subTemplates.get(commentWrapper));
                        DocxStamper<Object> stamper = new DocxStamper<>(configuration.copy());
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        stamper.stamp(subTemplate, subContext, output);
                        WordprocessingMLPackage subDocument = WordprocessingMLPackage.load(new ByteArrayInputStream(output.toByteArray()));
                        try {
                            List<Object> changes = DocumentUtil.prepareDocumentForInsert(subDocument, document);
                            insertParentContentAccessor.getContent().addAll(index, changes);
                            index += changes.size();

                            // make sure we replicate the previous section break before each repeated doc part
                            if (!expressionContexts.isEmpty() && previousSectionBreak.containsKey(commentWrapper)) {
                                P p = (P) insertParentContentAccessor.getContent().get(index - 1);
                                if (p.getPPr() == null) p.setPPr(new PPr());
                                p.getPPr().setSectPr(previousSectionBreak.get(commentWrapper));
                            }
                        } catch (Exception e) {
                            throw new RuntimeException("Unexpected error occured ! Skipping this comment", e);
                        }
                    } catch (Docx4JException e) {
                        throw new RuntimeException(e);
                    }
                }
            } else if (configuration.isReplaceNullValues() && configuration.getNullValuesDefault() != null) {
                // make sure we replicate the original previous section break before adding the default value
                P p = ParagraphUtil.create(configuration.getNullValuesDefault());
                if (previousSectionBreak.containsKey(commentWrapper)) {
                    if (p.getPPr() == null) p.setPPr(new PPr());
                    p.getPPr().setSectPr(previousSectionBreak.get(commentWrapper));
                }
                insertParentContentAccessor.getContent().add(index, p);
            }

            insertParentContentAccessor.getContent().removeAll(repeatElementsMap.get(commentWrapper));
        }
    }

    @Override
    public void reset() {
        subContexts = new HashMap<>();
        subTemplates = new HashMap<>();
        gcpMap = new HashMap<>();
        repeatElementsMap = new HashMap<>();
        previousSectionBreak = new HashMap<>();
    }

    private WordprocessingMLPackage extractSubTemplate(CommentWrapper commentWrapper, List<Object> repeatElements, ObjectFactory objectFactory) throws Exception {
        WordprocessingMLPackage document = getDocument();
        WordprocessingMLPackage subDocument = WordprocessingMLPackage.createPackage();

        CommentsPart commentsPart = new CommentsPart();
        subDocument.getMainDocumentPart().addTargetPart(commentsPart);

        // copy the elements to repeat without comment range anchors
        List<Object> finalRepeatElements = repeatElements.stream().map(XmlUtils::deepCopy).collect(Collectors.toList());
        removeCommentAnchorsFromFinalElements(commentWrapper, finalRepeatElements);
        subDocument.getMainDocumentPart().getContent().addAll(finalRepeatElements);

        // copy the images from parent document using the original repeat elements
        ContentAccessor fakeBody = getOrCreateObjectFactory().createBody();
        fakeBody.getContent().addAll(repeatElements);
        DocumentUtil.walkObjectsAndImportImages(fakeBody, document, subDocument);

        Comments comments = objectFactory.createComments();
        extractedSubComments(commentWrapper, comments);
        commentsPart.setContents(comments);

        return subDocument;
    }

    private void extractedSubComments(CommentWrapper commentWrapper, Comments comments) {
        for (CommentWrapper child : commentWrapper.getChildren()) {
            comments.getComment().add(child.getComment());
            if (CollectionUtils.isEmpty(child.getChildren())) {
                continue;
            }
            extractedSubComments(child, comments);
        }
    }

    private static void removeCommentAnchorsFromFinalElements(CommentWrapper commentWrapper, List<Object> finalRepeatElements) {
        List<Object> commentsToRemove = new ArrayList<>();

        new BaseDocumentWalker(() -> finalRepeatElements) {
            @Override
            protected void onCommentRangeStart(CommentRangeStart commentRangeStart) {
                if (commentRangeStart.getId().equals(commentWrapper.getComment().getId())) {
                    commentsToRemove.add(commentRangeStart);
                }
            }

            @Override
            protected void onCommentRangeEnd(CommentRangeEnd commentRangeEnd) {
                if (commentRangeEnd.getId().equals(commentWrapper.getComment().getId())) {
                    commentsToRemove.add(commentRangeEnd);
                }
            }
        }.walk();

        for (Object commentAnchorToRemove : commentsToRemove) {
            removeCommentAnchorFromParentOrFinalRepeatElements(commentAnchorToRemove, finalRepeatElements);
        }
    }

    private static void removeCommentAnchorFromParentOrFinalRepeatElements(Object commentAnchorToRemove, List<Object> finalRepeatElements) {
        if (finalRepeatElements.contains(commentAnchorToRemove)) {
            finalRepeatElements.remove(commentAnchorToRemove);
            return;
        }

        Object parent = null;
        if (commentAnchorToRemove instanceof CommentRangeStart) {
            parent = ((CommentRangeStart) commentAnchorToRemove).getParent();
        } else if (commentAnchorToRemove instanceof CommentRangeEnd) {
            parent = ((CommentRangeEnd) commentAnchorToRemove).getParent();
        } else {
            throw new RuntimeException("Unknown comment anchor type given to remove !");
        }

        if (finalRepeatElements.contains(commentAnchorToRemove) && parent != null) {
            ContentAccessor caParent = (ContentAccessor) XmlUtils.unwrap(parent);
            caParent.getContent().removeAll(caParent.getContent().subList(0, caParent.getContent().indexOf(commentAnchorToRemove) + 1));
        }
    }

    private List<Object> getRepeatElements(CommentWrapper commentWrapper, ContentAccessor greatestCommonParent) {
        List<Object> repeatElements = new ArrayList<>();
        boolean startFound = false;
        Object previousElement = null;
        for (Object element : greatestCommonParent.getContent()) {
            if (!startFound
                    && depthElementSearch(commentWrapper.getCommentRangeStart(), element)) {
                startFound = true;
            } else {
                previousElement = element;
            }
            if (startFound) {
                repeatElements.add(element);
                if (depthElementSearch(commentWrapper.getCommentRangeEnd(), element)) {
                    break;
                }
            }
        }
        if (previousElement != null) {
            Object unwrapped = XmlUtils.unwrap(previousElement);
            if (unwrapped instanceof P) {
                P prevP = (P) unwrapped;
                if (prevP.getPPr() != null && prevP.getPPr().getSectPr() != null) {
                    previousSectionBreak.put(commentWrapper, prevP.getPPr().getSectPr());
                }
            }
        }
        return repeatElements;
    }

    private static ContentAccessor findGreatestCommonParent(Object targetSearch, ContentAccessor searchFrom) {
        if (depthElementSearch(targetSearch, searchFrom)) {
            return findInsertableParent(searchFrom);
        }
        return findGreatestCommonParent(targetSearch, (ContentAccessor) ((Child) searchFrom).getParent());
    }

    private static ContentAccessor findInsertableParent(ContentAccessor searchFrom) {
        if (!(searchFrom instanceof Tc || searchFrom instanceof Body)) {
            return findInsertableParent((ContentAccessor) ((Child) searchFrom).getParent());
        }
        return searchFrom;
    }

    private static boolean depthElementSearch(Object searchTarget, Object content) {
        content = XmlUtils.unwrap(content);
        if (searchTarget.equals(content)) {
            return true;
        } else if (content instanceof ContentAccessor) {
            for (Object object : ((ContentAccessor) content).getContent()) {
                Object unwrappedObject = XmlUtils.unwrap(object);
                if (searchTarget.equals(unwrappedObject)
                        || depthElementSearch(searchTarget, unwrappedObject)) {
                    return true;
                }
            }
        }
        return false;
    }
}
