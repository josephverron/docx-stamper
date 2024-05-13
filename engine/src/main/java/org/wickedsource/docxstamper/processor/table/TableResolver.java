package org.wickedsource.docxstamper.processor.table;

import jakarta.xml.bind.JAXBElement;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;
import org.wickedsource.docxstamper.processor.BaseCommentProcessor;
import org.wickedsource.docxstamper.processor.CommentProcessingException;
import org.wickedsource.docxstamper.util.ParagraphUtil;
import pro.verron.officestamper.api.CommentProcessor;
import pro.verron.officestamper.api.ParagraphPlaceholderReplacer;
import pro.verron.officestamper.core.PlaceholderReplacer;
import pro.verron.officestamper.preset.StampTable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * <p>TableResolver class.</p>
 *
 * @author Joseph Verron
 * @version ${version}
 * @since 1.6.2
 */
public class TableResolver
        extends BaseCommentProcessor
        implements ITableResolver {
    private final Map<Tbl, StampTable> cols = new HashMap<>();
    private final Function<Tbl, List<Object>> nullSupplier;

    private TableResolver(
            ParagraphPlaceholderReplacer placeholderReplacer,
            Function<Tbl, List<Object>> nullSupplier
    ) {
        super(placeholderReplacer);
        this.nullSupplier = nullSupplier;
    }

    /**
     * Generate a new {@link TableResolver} instance
     *
     * @param pr                   a {@link PlaceholderReplacer} instance
     * @param nullReplacementValue in case the value to interpret is <code>null</code>
     * @return a new {@link TableResolver} instance
     * @deprecated should be an internal implementation detail
     */
    @Deprecated(since = "1.6.8", forRemoval = true)
    public static CommentProcessor newInstance(
            PlaceholderReplacer pr,
            String nullReplacementValue
    ) {
        return new TableResolver(pr,
                                 table -> List.of(ParagraphUtil.create(
                                         nullReplacementValue)));
    }

    /**
     * Generate a new {@link TableResolver} instance where value is replaced by an empty list when <code>null</code>
     *
     * @param pr a {@link PlaceholderReplacer} instance
     * @return a new {@link TableResolver} instance
     */
    public static CommentProcessor newInstance(ParagraphPlaceholderReplacer pr) {
        return new TableResolver(pr, table -> Collections.emptyList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resolveTable(StampTable givenTable) {
        P p = getParagraph();
        if (p.getParent() instanceof Tc tc
            && tc.getParent() instanceof Tr tr
            && tr.getParent() instanceof Tbl table
        ) cols.put(table, givenTable);
        else throw new CommentProcessingException("Paragraph is not within a " +
                                                  "table!", p);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commitChanges(WordprocessingMLPackage document) {
        for (Map.Entry<Tbl, StampTable> entry : cols.entrySet()) {
            Tbl wordTable = entry.getKey();

            StampTable stampedTable = entry.getValue();

            if (stampedTable != null) {
                replaceTableInplace(wordTable, stampedTable);
            } else {
                List<Object> tableParentContent = ((ContentAccessor) wordTable.getParent()).getContent();
                int tablePosition = tableParentContent.indexOf(wordTable);
                List<Object> toInsert = nullSupplier.apply(wordTable);
                tableParentContent.set(tablePosition, toInsert);
            }
        }
    }

    private void replaceTableInplace(Tbl wordTable, StampTable stampedTable) {
        var headers = stampedTable.headers();

        var rows = wordTable.getContent();
        var headerRow = (Tr) rows.get(0);
        var firstDataRow = (Tr) rows.get(1);

        growAndFillRow(headerRow, headers);

        if (stampedTable.isEmpty())
            rows.remove(firstDataRow);
        else {
            growAndFillRow(firstDataRow, stampedTable.get(0));
            for (var rowContent : stampedTable.subList(1, stampedTable.size()))
                rows.add(copyRowFromTemplate(firstDataRow, rowContent));
        }
    }

    private void growAndFillRow(Tr row, List<String> values) {
        List<Object> cellRowContent = row.getContent();

        //Replace text in first cell
        JAXBElement<Tc> cell0 = (JAXBElement<Tc>) cellRowContent.get(0);
        Tc cell0tc = cell0.getValue();
        setCellText(cell0tc, values.isEmpty() ? "" : values.get(0));

        if (values.size() > 1) {
            //Copy the first cell and replace content for each remaining value
            for (String cellContent : values.subList(1, values.size())) {
                JAXBElement<Tc> xmlCell = XmlUtils.deepCopy(cell0);
                setCellText(xmlCell.getValue(), cellContent);
                cellRowContent.add(xmlCell);
            }
        }
    }

    private Tr copyRowFromTemplate(Tr firstDataRow, List<String> rowContent) {
        Tr newXmlRow = XmlUtils.deepCopy(firstDataRow);
        List<Object> xmlRow = newXmlRow.getContent();
        for (int i = 0; i < rowContent.size(); i++) {
            String cellContent = rowContent.get(i);
            Tc xmlCell = ((JAXBElement<Tc>) xmlRow.get(i)).getValue();
            setCellText(xmlCell, cellContent);
        }
        return newXmlRow;
    }

    private void setCellText(Tc tableCell, String content) {
        tableCell.getContent()
                .clear();
        tableCell.getContent()
                .add(ParagraphUtil.create(content));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        cols.clear();
    }
}
