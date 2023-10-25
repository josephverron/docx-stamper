package org.wickedsource.docxstamper;

import org.docx4j.TextUtils;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.R;
import org.junit.jupiter.api.Test;
import org.wickedsource.docxstamper.util.DocumentUtil;
import pro.verron.docxstamper.utils.TestDocxStamper;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wickedsource.docxstamper.DefaultTests.getResource;

class MultiSectionTest {

	@Test
    void expressionsInMultipleSections() throws Docx4JException, IOException {
		var context = new NamesContext("Homer", "Marge");
		var template = getResource("MultiSectionTest.docx");
		var stamper = new TestDocxStamper<NamesContext>(
				new DocxStamperConfiguration());
		var document = stamper.stampAndLoad(template, context);
		assertTableRows(document);
	}

	private void assertTableRows(WordprocessingMLPackage document) {
		final List<R> runs = DocumentUtil.streamElements(document, R.class).toList();
		assertTrue(runs.stream().map(TextUtils::getText).anyMatch(s -> s.contains("Homer")));
		assertTrue(runs.stream().map(TextUtils::getText).anyMatch(s -> s.contains("Marge")));
	}

	public record NamesContext(String firstName, String secondName) {
	}
}
