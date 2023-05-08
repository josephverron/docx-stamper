package org.wickedsource.docxstamper.util;

import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.CommentsPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.*;
import org.jvnet.jaxb2_commons.ppp.Child;
import org.wickedsource.docxstamper.api.DocxStamperException;

import java.util.*;
import java.util.stream.Collectors;

public class CommentWrapper {

	private final Set<CommentWrapper> children = new HashSet<>();
	private Comments.Comment comment;
	private CommentRangeStart commentRangeStart;
	private CommentRangeEnd commentRangeEnd;
	private R.CommentReference commentReference;

	public CommentWrapper() {
	}

	public Comments.Comment getComment() {
		return comment;
	}

	void setComment(Comments.Comment comment) {
		this.comment = comment;
	}

	public CommentRangeStart getCommentRangeStart() {
		return commentRangeStart;
	}

	void setCommentRangeStart(CommentRangeStart commentRangeStart) {
		this.commentRangeStart = commentRangeStart;
	}

	public CommentRangeEnd getCommentRangeEnd() {
		return commentRangeEnd;
	}

	void setCommentRangeEnd(CommentRangeEnd commentRangeEnd) {
		this.commentRangeEnd = commentRangeEnd;
	}

	public R.CommentReference getCommentReference() {
		return commentReference;
	}

	void setCommentReference(R.CommentReference commentReference) {
		this.commentReference = commentReference;
	}

	public Set<CommentWrapper> getChildren() {
		return children;
	}

	void setChildren(Set<CommentWrapper> children) {
		this.children.addAll(children);
	}

	public ContentAccessor getParent() {
		return findGreatestCommonParent(
				getCommentRangeEnd().getParent(),
				(ContentAccessor) getCommentRangeStart().getParent()
		);
	}

	private ContentAccessor findGreatestCommonParent(Object end, ContentAccessor start) {
		if (depthElementSearch(end, start)) {
			return findInsertableParent(start);
		}
		return findGreatestCommonParent(end, (ContentAccessor) ((Child) start).getParent());
	}

	private boolean depthElementSearch(Object searchTarget, Object content) {
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

	private ContentAccessor findInsertableParent(ContentAccessor searchFrom) {
		if (!(searchFrom instanceof Tc || searchFrom instanceof Body)) {
			return findInsertableParent((ContentAccessor) ((Child) searchFrom).getParent());
		}
		return searchFrom;
	}

	public List<Object> getRepeatElements() {
		List<Object> repeatElements = new ArrayList<>();
		boolean startFound = false;
		for (Object element : getParent().getContent()) {
			if (!startFound
					&& depthElementSearch(getCommentRangeStart(), element)) {
				startFound = true;
			}
			if (startFound) {
				repeatElements.add(element);
				if (depthElementSearch(getCommentRangeEnd(), element)) {
					break;
				}
			}
		}
		return repeatElements;
	}

	private void removeCommentAnchorsFromFinalElements(List<Object> finalRepeatElements) {
		ContentAccessor fakeBody = () -> finalRepeatElements;
		CommentUtil.deleteCommentFromElement(fakeBody, getComment().getId());
	}

	private void extractedSubComments(List<Comments.Comment> commentList, Set<CommentWrapper> commentWrapperChildren) {
		Queue<CommentWrapper> q = new ArrayDeque<>(commentWrapperChildren);
		while (!q.isEmpty()) {
			CommentWrapper element = q.remove();
			commentList.add(element.getComment());
			if (element.getChildren() != null)
				q.addAll(element.getChildren());
		}
	}

	public WordprocessingMLPackage getSubTemplate(WordprocessingMLPackage document) throws Exception {
		List<Object> repeatElements = getRepeatElements();

		WordprocessingMLPackage subDocument = WordprocessingMLPackage.createPackage();
		MainDocumentPart subDocumentMainDocumentPart = subDocument.getMainDocumentPart();

		CommentsPart commentsPart = new CommentsPart();
		subDocumentMainDocumentPart.addTargetPart(commentsPart);

		// copy the elements to repeat without comment range anchors
		List<Object> finalRepeatElements = repeatElements.stream().map(XmlUtils::deepCopy).collect(Collectors.toList());
		removeCommentAnchorsFromFinalElements(finalRepeatElements);
		subDocumentMainDocumentPart.getContent().addAll(finalRepeatElements);

		// copy the images from parent document using the original repeat elements
		ObjectFactory wmlObjectFactory = Context.getWmlObjectFactory();
		ContentAccessor fakeBody = wmlObjectFactory.createBody();
		fakeBody.getContent().addAll(repeatElements);
		DocumentUtil.walkObjectsAndImportImages(fakeBody, document, subDocument);

		Comments comments = wmlObjectFactory.createComments();
		extractedSubComments(comments.getComment(), this.getChildren());
		commentsPart.setContents(comments);

		return subDocument;
	}

	public WordprocessingMLPackage tryBuildingSubtemplate(WordprocessingMLPackage document) {
		try {
			return getSubTemplate(document);
		} catch (Exception e) {
			throw new DocxStamperException(e);
		}
	}
}
