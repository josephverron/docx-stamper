package org.wickedsource.docxstamper.util;

import org.docx4j.TextUtils;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.exceptions.InvalidFormatException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.WordprocessingML.CommentsPart;
import org.docx4j.wml.*;
import org.jvnet.jaxb2_commons.ppp.Child;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wickedsource.docxstamper.api.DocxStamperException;
import org.wickedsource.docxstamper.util.walk.BaseDocumentWalker;
import org.wickedsource.docxstamper.util.walk.DocumentWalker;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;

/**
 * <p>CommentUtil class.</p>
 *
 * @author joseph
 * @version $Id: $Id
 */
public class CommentUtil {
    private static final Logger logger = LoggerFactory.getLogger(
            CommentUtil.class);
    private static final String WORD_COMMENTS_PART_NAME = "/word/comments.xml";

    private CommentUtil() {
        throw new DocxStamperException(
                "Utility class shouldn't be instantiated");
    }

    /**
     * Returns the comment the given DOCX4J run is commented with.
     *
     * @param run      the DOCX4J run whose comment to retrieve.
     * @param document the document that contains the run.
     * @return the comment, if found, null otherwise.
     */
    public static Optional<Comments.Comment> getCommentAround(R run, WordprocessingMLPackage document) {
        if (run == null)
            return Optional.empty();

        ContentAccessor parent = (ContentAccessor) ((Child) run).getParent();
        if (parent == null)
            return Optional.empty();
        CommentRangeStart possibleComment = null;
        boolean foundChild = false;
        try {
            for (Object contentElement : parent.getContent()) {
                // so first we look for the start of the comment
                if (XmlUtils.unwrap(
                        contentElement) instanceof CommentRangeStart) {
                    possibleComment = (CommentRangeStart) contentElement;
                }
                // then we check if the child we are looking for is ours
                else if (possibleComment != null && run.equals(
                        contentElement)) {
                    foundChild = true;
                }
                // and then if we have an end of a comment we are good!
                else if (possibleComment != null && foundChild && XmlUtils
                        .unwrap(contentElement) instanceof CommentRangeEnd) {
                    try {
                        BigInteger id = possibleComment.getId();
                        Optional<Comments.Comment> comment = findComment(
                                document, id);
                        if (comment != null) return comment;
                    } catch (InvalidFormatException e) {
                        logger.warn(String.format(
                                "Error while searching comment. Skipping run %s.",
                                run), e);
                    }
                }
                // else restart
                else {
                    possibleComment = null;
                    foundChild = false;
                }
            }
            return Optional.empty();
        } catch (Docx4JException e) {
            throw new DocxStamperException(
                    "error accessing the comments of the document!", e);
        }
    }

    public static Optional<Comments.Comment> findComment(WordprocessingMLPackage document, BigInteger id) throws Docx4JException {
        var name = new PartName(WORD_COMMENTS_PART_NAME);
        var parts = document.getParts();
        var wordComments = (CommentsPart) parts.get(name);
        var comments = wordComments.getContents();
        return comments.getComment()
                .stream()
                .filter(comment -> comment.getId().equals(id))
                .findFirst();
    }

    /**
     * Returns the comment the given DOCX4J object is commented with.
     *
     * @param object   the DOCX4J object whose comment to retrieve.
     * @param document the document that contains the object.
     * @return the comment, if found, null otherwise.
     */
    public static String getCommentStringFor(ContentAccessor object, WordprocessingMLPackage document) {
        Comments.Comment comment = getCommentFor(object,
                                                 document).orElseThrow();
        return getCommentString(comment);
    }

    /**
     * Returns the first comment found for the given docx object. Note that an object is
     * only considered commented if the comment STARTS within the object. Comments
     * spanning several objects are not supported by this method.
     *
     * @param object   the object whose comment to load.
     * @param document the document in which the object is embedded (needed to load the
     *                 comment from the comments.xml part).
     * @return the concatenated string of all paragraphs of text within the comment or
     * null if the specified object is not commented.
     */
    public static Optional<Comments.Comment> getCommentFor(ContentAccessor object, WordprocessingMLPackage document) {
        for (Object contentObject : object.getContent()) {
            if (!(contentObject instanceof CommentRangeStart crs))
                continue;
            BigInteger id = crs.getId();
            PartName partName;
            try {
                partName = new PartName(WORD_COMMENTS_PART_NAME);
            } catch (InvalidFormatException e) {
                String message = String.format(
                        "Error while searching comment. Skipping object %s.",
                        object);
                throw new DocxStamperException(message, e);
            }
            CommentsPart commentsPart = (CommentsPart) document.getParts()
                    .get(partName);
            Comments comments;
            try {
                comments = commentsPart.getContents();
            } catch (Docx4JException e) {
                throw new DocxStamperException(
                        "error accessing the comments of the document!", e);
            }

            for (Comments.Comment comment : comments.getComment()) {
                if (comment.getId()
                        .equals(id)) {
                    return Optional.of(comment);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the string value of the specified comment object.
     *
     * @param comment a {@link org.docx4j.wml.Comments.Comment} object
     * @return a {@link java.lang.String} object
     */
    public static String getCommentString(Comments.Comment comment) {
        StringBuilder builder = new StringBuilder();
        for (Object commentChildObject : comment.getContent()) {
            if (commentChildObject instanceof P p) {
                builder.append(new ParagraphWrapper(p).getText());
            }
        }
        return builder.toString();
    }

    /**
     * Returns the string value of the specified comment object.
     *
     * @param comment a {@link org.wickedsource.docxstamper.util.CommentWrapper} object
     */
    public static void deleteComment(CommentWrapper comment) {
        CommentRangeEnd end = comment.getCommentRangeEnd();
        if (end != null) {
            ContentAccessor endParent = (ContentAccessor) end.getParent();
            endParent.getContent()
                    .remove(end);
        }
        CommentRangeStart start = comment.getCommentRangeStart();
        if (start != null) {
            ContentAccessor startParent = (ContentAccessor) start.getParent();
            startParent.getContent()
                    .remove(start);
        }
        R.CommentReference reference = comment.getCommentReference();
        if (reference != null) {
            ContentAccessor referenceParent = (ContentAccessor) reference.getParent();
            referenceParent.getContent()
                    .remove(reference);
        }
    }

    /**
     * Returns the string value of the specified comment object.
     *
     * @param items     a {@link java.util.List} object
     * @param commentId a {@link java.math.BigInteger} object
     */
    public static void deleteCommentFromElement(List<Object> items, BigInteger commentId) {
        List<Object> elementsToRemove = new ArrayList<>();
        for (Object item : items) {
            Object unwrapped = XmlUtils.unwrap(item);
            if (unwrapped instanceof CommentRangeStart crs) {
                if (crs.getId()
                        .equals(commentId)) {
                    elementsToRemove.add(item);
                }
            } else if (unwrapped instanceof CommentRangeEnd cre) {
                if (cre.getId()
                        .equals(commentId)) {
                    elementsToRemove.add(item);
                }
            } else if (unwrapped instanceof R.CommentReference rcr) {
                if (rcr.getId()
                        .equals(commentId)) {
                    elementsToRemove.add(item);
                }
            } else if (unwrapped instanceof ContentAccessor ca) {
                deleteCommentFromElement(ca.getContent(), commentId);
            }
        }
        items.removeAll(elementsToRemove);
    }

    /**
     * Returns the string value of the specified comment object.
     *
     * @param document a {@link org.docx4j.openpackaging.packages.WordprocessingMLPackage} object
     * @return a {@link java.util.Map} object
     */
    public static Map<BigInteger, CommentWrapper> getComments(WordprocessingMLPackage document) {
        Map<BigInteger, CommentWrapper> rootComments = new HashMap<>();
        Map<BigInteger, CommentWrapper> allComments = new HashMap<>();
        collectCommentRanges(rootComments, allComments, document);
        collectComments(allComments, document);
        return cleanMalformedComments(rootComments);
    }

    private static Map<BigInteger, CommentWrapper> cleanMalformedComments(Map<BigInteger, CommentWrapper> rootComments) {
        Map<BigInteger, CommentWrapper> filteredCommentEntries = new HashMap<>();

        rootComments.forEach((key, comment) -> {
            if (isCommentMalformed(comment)) {
                logger.error(
                        "Skipping malformed comment, missing range start and/or range end : {}",
                        getCommentContent(comment)
                );
            } else {
                filteredCommentEntries.put(key, comment);
                comment.setChildren(
                        cleanMalformedComments(comment.getChildren()));
            }
        });
        return filteredCommentEntries;
    }

    private static Set<CommentWrapper> cleanMalformedComments(Set<CommentWrapper> children) {
        return children.stream()
                .filter(comment -> {
                    if (isCommentMalformed(comment)) {
                        logger.error(
                                "Skipping malformed comment, missing range start and/or range end : {}",
                                getCommentContent(comment)
                        );
                        return false;
                    }
                    comment.setChildren(
                            cleanMalformedComments(comment.getChildren()));
                    return true;
                })
                .collect(toSet());
    }

    private static String getCommentContent(CommentWrapper comment) {
        return comment.getComment() != null
                ? comment.getComment()
                .getContent()
                .stream()
                .map(TextUtils::getText)
                .collect(Collectors.joining(""))
                : "<no content>";
    }

    private static boolean isCommentMalformed(CommentWrapper comment) {
        return comment.getCommentRangeStart() == null || comment.getCommentRangeEnd() == null || comment.getComment() == null;
    }

    private static void collectCommentRanges(
            Map<BigInteger, CommentWrapper> rootComments,
            Map<BigInteger, CommentWrapper> allComments,
            WordprocessingMLPackage document
    ) {
        Queue<CommentWrapper> stack = Collections.asLifoQueue(
                new ArrayDeque<>());
        DocumentWalker documentWalker = new BaseDocumentWalker(
                document.getMainDocumentPart()) {
            @Override
            protected void onCommentRangeStart(CommentRangeStart commentRangeStart) {
                CommentWrapper commentWrapper = allComments.get(
                        commentRangeStart.getId());
                if (commentWrapper == null) {
                    commentWrapper = new CommentWrapper();
                    allComments.put(commentRangeStart.getId(), commentWrapper);
                    if (stack.isEmpty()) {
                        rootComments.put(commentRangeStart.getId(),
                                         commentWrapper);
                    } else {
                        stack.peek()
                                .getChildren()
                                .add(commentWrapper);
                    }
                }
                commentWrapper.setCommentRangeStart(commentRangeStart);
                stack.add(commentWrapper);
            }

            @Override
            protected void onCommentRangeEnd(CommentRangeEnd commentRangeEnd) {
                CommentWrapper commentWrapper = allComments.get(
                        commentRangeEnd.getId());
                if (commentWrapper == null)
                    throw new DocxStamperException(
                            "Found a comment range end before the comment range start !");

                commentWrapper.setCommentRangeEnd(commentRangeEnd);

                if (stack.isEmpty())
                    return;

                if (stack.peek()
                        .equals(commentWrapper))
                    stack.remove();
                else
                    throw new DocxStamperException(
                            "Cannot figure which comment contains the other !");
            }

            @Override
            protected void onCommentReference(R.CommentReference commentReference) {
                CommentWrapper commentWrapper = allComments.get(
                        commentReference.getId());
                if (commentWrapper == null)
                    throw new DocxStamperException(
                            "Found a comment reference before the comment range start !");
                commentWrapper.setCommentReference(commentReference);
            }
        };
        documentWalker.walk();
    }

    private static void collectComments(Map<BigInteger, CommentWrapper> allComments, WordprocessingMLPackage document) {
        try {
            CommentsPart commentsPart = (CommentsPart) document.getParts()
                    .get(new PartName(WORD_COMMENTS_PART_NAME));
            if (commentsPart != null) {
                for (Comments.Comment comment : commentsPart.getContents()
                        .getComment()) {
                    CommentWrapper commentWrapper = allComments.get(
                            comment.getId());
                    if (commentWrapper != null) {
                        commentWrapper.setComment(comment);
                    }
                }
            }
        } catch (Docx4JException e) {
            throw new IllegalStateException(e);
        }
    }

}
