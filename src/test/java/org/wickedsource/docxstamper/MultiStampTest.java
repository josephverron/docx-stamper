package org.wickedsource.docxstamper;

import org.docx4j.TextUtils;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Tr;
import org.junit.jupiter.api.Test;
import org.wickedsource.docxstamper.util.DocumentUtil;
import pro.verron.docxstamper.utils.TestDocxStamper;
import pro.verron.docxstamper.utils.context.Name;
import pro.verron.docxstamper.utils.context.NamesContext;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wickedsource.docxstamper.DefaultTests.getResource;

class MultiStampTest {
	@Test
    void expressionsAreResolvedOnMultiStamp() throws Docx4JException, IOException {
		var context = new NamesContext(List.of(
				new Name("Homer"),
				new Name("Marge"),
				new Name("Bart"),
				new Name("Lisa"),
				new Name("Maggie")));

		var template = getResource("MultiStampTest.docx");
		var stamper = new TestDocxStamper<>(new DocxStamperConfiguration());
		var document = stamper.stampAndLoad(template, context);
		assertTableRows(document);

		template = getResource("MultiStampTest.docx");
		document = stamper.stampAndLoad(template, context);
		assertTableRows(document);
	}

	private void assertTableRows(WordprocessingMLPackage document) {
		final List<Tbl> tablesFromObject = DocumentUtil.streamElements(document, Tbl.class).toList();
		assertEquals(1, tablesFromObject.size());

		final List<Tr> tableRows = DocumentUtil.streamElements(tablesFromObject.get(0), Tr.class).toList();
		assertEquals(5, tableRows.size());

		assertRowContainsText(tableRows.get(0), "Homer");
		assertRowContainsText(tableRows.get(1), "Marge");
		assertRowContainsText(tableRows.get(2), "Bart");
		assertRowContainsText(tableRows.get(3), "Lisa");
		assertRowContainsText(tableRows.get(4), "Maggie");
	}

	private static void assertRowContainsText(Tr row, String text) {
		final List<Tc> cell0 = DocumentUtil.streamElements(row, Tc.class).toList();
		String cellContent = TextUtils.getText(cell0.get(0));
		String message = String.format("'%s' is not contained in '%s'", text, cellContent);
		assertTrue(cellContent.contains(text), message);
	}
}
