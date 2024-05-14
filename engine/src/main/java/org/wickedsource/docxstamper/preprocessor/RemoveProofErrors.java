package org.wickedsource.docxstamper.preprocessor;

import org.docx4j.TraversalUtil;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.utils.TraversalUtilVisitor;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.ProofErr;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes all {@link ProofErr} elements from the document.
 *
 * @author Joseph Verron
 * @version ${version}
 * @since 1.6.4
 */
public class RemoveProofErrors
		implements pro.verron.officestamper.api.PreProcessor {

	private final List<ProofErr> proofErrs = new ArrayList<>();
	private final TraversalUtilVisitor<ProofErr> visitor = new TraversalUtilVisitor<>() {
		@Override
		public void apply(ProofErr element, Object parent, List<Object> siblings) {
			proofErrs.add(element);
		}
	};

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void process(WordprocessingMLPackage document) {
		var mainDocumentPart = document.getMainDocumentPart();
		TraversalUtil.visit(mainDocumentPart, visitor);
		for (ProofErr proofErr : proofErrs) {
			if (proofErr.getParent() instanceof ContentAccessor parent)
				parent.getContent().remove(proofErr);
		}
	}
}
