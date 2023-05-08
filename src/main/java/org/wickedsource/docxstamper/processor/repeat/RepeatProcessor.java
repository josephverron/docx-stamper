package org.wickedsource.docxstamper.processor.repeat;

import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;
import org.wickedsource.docxstamper.api.commentprocessor.ICommentProcessor;
import org.wickedsource.docxstamper.processor.BaseCommentProcessor;
import org.wickedsource.docxstamper.processor.CommentProcessingException;
import org.wickedsource.docxstamper.replace.PlaceholderReplacer;
import org.wickedsource.docxstamper.util.CommentUtil;
import org.wickedsource.docxstamper.util.CommentWrapper;

import java.math.BigInteger;
import java.util.*;
import java.util.function.BiFunction;

import static java.util.Collections.emptyList;

public class RepeatProcessor extends BaseCommentProcessor implements IRepeatProcessor {

	private final BiFunction<WordprocessingMLPackage, Tr, List<Tr>> nullSupplier;
	private Map<Tr, List<Object>> tableRowsToRepeat = new HashMap<>();
	private Map<Tr, CommentWrapper> tableRowsCommentsToRemove = new HashMap<>();

	private RepeatProcessor(
			PlaceholderReplacer placeholderReplacer,
			BiFunction<WordprocessingMLPackage, Tr, List<Tr>> nullSupplier1
	) {
		super(placeholderReplacer);
		nullSupplier = nullSupplier1;
	}

	public static ICommentProcessor newInstanceWithNullReplacement(PlaceholderReplacer pr) {
		return new RepeatProcessor(pr, (document, row) -> RepeatProcessor.stampEmptyContext(pr, document, row));
	}

	public static List<Tr> stampEmptyContext(PlaceholderReplacer placeholderReplacer, WordprocessingMLPackage document, Tr row1) {
		Tr rowClone = XmlUtils.deepCopy(row1);
		Object emptyContext = new Object();
		new ParagraphResolverDocumentWalker(rowClone, emptyContext, document, placeholderReplacer).walk();
		return List.of(rowClone);
	}

	public static ICommentProcessor newInstance(PlaceholderReplacer pr) {
		return new RepeatProcessor(pr, (document, row) -> emptyList());
	}

	@Override
	public void commitChanges(WordprocessingMLPackage document) {
		repeatRows(document);
	}

	@Override
	public void reset() {
		this.tableRowsToRepeat = new HashMap<>();
		this.tableRowsCommentsToRemove = new HashMap<>();
	}

	private void repeatRows(final WordprocessingMLPackage document) {
		for (Map.Entry<Tr, List<Object>> entry : tableRowsToRepeat.entrySet()) {
			Tr row = entry.getKey();
			List<Object> expressionContexts = entry.getValue();

			Tbl table = (Tbl) XmlUtils.unwrap(row.getParent());
			int index = table.getContent().indexOf(row);


			List<Tr> changes;
			if (expressionContexts == null) {
				changes = nullSupplier.apply(document, row);
			} else {
				changes = new ArrayList<>();
				for (Object expressionContext : expressionContexts) {
					Tr rowClone = XmlUtils.deepCopy(row);
					CommentWrapper commentWrapper = Objects.requireNonNull(tableRowsCommentsToRemove.get(row));
					Comments.Comment comment = Objects.requireNonNull(commentWrapper.getComment());
					BigInteger commentId = comment.getId();
					CommentUtil.deleteCommentFromElement(rowClone, commentId);
					new ParagraphResolverDocumentWalker(rowClone,
														expressionContext,
														document,
														this.placeholderReplacer).walk();
					changes.add(rowClone);
				}
			}
			table.getContent().addAll(index + 1, changes);
			table.getContent().remove(row);
		}
	}

	@Override
	public void repeatTableRow(List<Object> objects) {
		P pCoords = getParagraph();

		if (pCoords.getParent() instanceof Tc tc
				&& tc.getParent() instanceof Tr tableRow) {
			tableRowsToRepeat.put(tableRow, objects);
			tableRowsCommentsToRemove.put(tableRow, getCurrentCommentWrapper());
		} else {
			throw new CommentProcessingException("Paragraph is not within a table!", pCoords);
		}
	}
}
