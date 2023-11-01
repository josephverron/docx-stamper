package org.wickedsource.docxstamper.util;

import jakarta.xml.bind.JAXBElement;
import org.docx4j.TraversalUtil;
import org.docx4j.finders.ClassFinder;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.relationships.Namespaces;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.wml.*;
import org.wickedsource.docxstamper.api.DocxStamperException;
import org.wickedsource.docxstamper.replace.typeresolver.image.ImageResolver;

import java.util.*;
import java.util.stream.Stream;

/**
 * Utility class to retrieve elements from a document.
 *
 * @author joseph
 * @version $Id: $Id
 */
public class DocumentUtil {
    private DocumentUtil() {
        throw new DocxStamperException(
                "Utility classes shouldn't be instantiated");
    }

    /**
     * Retrieve all the paragraphs from a document
     *
     * @param parentObject the document to get the paragraphs from
     * @return a list of paragraphs
     */
    public static List<P> getParagraphsFromObject(Object parentObject) {
        return streamElements(parentObject, P.class).toList();
    }

    /**
     * Retrieve all the elements of a given class from an object.
     *
     * @param object       the object to get the elements from
     * @param elementClass the class of the elements to get
     * @param <T>          the type of the elements to get
     * @return a stream of the elements
     */
    public static <T> Stream<T> streamElements(
            Object object,
            Class<T> elementClass
    ) {
        return object instanceof WordprocessingMLPackage document
                ? streamDocumentElements(document, elementClass)
                : streamObjectElements(object, elementClass);
    }

    /**
     * we handle full documents slightly differently as they have headers and footers,
     * and we want to get all the elements from them as well
     *
     * @param document     the document to get the elements from
     * @param elementClass the class of the elements to get
     * @param <T>          the type of the elements to get
     * @return a stream of the elements
     */
    private static <T> Stream<T> streamDocumentElements(
            WordprocessingMLPackage document,
            Class<T> elementClass
    ) {
        RelationshipsPart mainParts = document.getMainDocumentPart()
                .getRelationshipsPart();
        return Stream.of(
                        streamElements(mainParts, Namespaces.HEADER, elementClass),
                        streamObjectElements(document.getMainDocumentPart(),
                                             elementClass),
                        streamElements(mainParts, Namespaces.FOOTER, elementClass)
                )
                .reduce(Stream.empty(), Stream::concat);
    }

    private static <T> Stream<T> streamObjectElements(
            Object obj,
            Class<T> elementClass
    ) {
        ClassFinder finder = new ClassFinder(elementClass);
        TraversalUtil.visit(obj, finder);
        return finder.results.stream()
                .map(elementClass::cast);
    }

    private static <T> Stream<T> streamElements(
            RelationshipsPart mainParts,
            String namespace,
            Class<T> elementClass
    ) {
        return mainParts
                .getRelationshipsByType(namespace)
                .stream()
                .map(mainParts::getPart)
                .flatMap(part -> streamObjectElements(part, elementClass));
    }

    /**
     * Retrieve all the tables from an object.
     *
     * @param parentObject the object to get the tables from
     * @return a list of tables
     */
    public static List<Tbl> getTableFromObject(Object parentObject) {
        return streamElements(parentObject, Tbl.class).toList();
    }

    /**
     * Retrieve all the rows from an object.
     *
     * @param parentObject the object to get the rows from
     * @return a list of rows
     */
    public static List<Tr> getTableRowsFromObject(Object parentObject) {
        return streamElements(parentObject, Tr.class).toList();
    }

    /**
     * Retrieve all the cells from an object.
     *
     * @param parentObject the object to get the cells from
     * @return a list of cells
     */
    public static List<Tc> getTableCellsFromObject(Object parentObject) {
        return streamElements(parentObject, Tc.class).toList();
    }

    /**
     * Retrieve the first element from an object.
     *
     * @param subDocument the object to get the first element from
     * @return the first element
     */
    public static Object lastElement(WordprocessingMLPackage subDocument) {
        return new ArrayDeque<>(subDocument.getMainDocumentPart()
                                        .getContent()).getLast();
    }

    /**
     * Retrieve the last element from an object.
     *
     * @param subDocument the object to get the last element from
     * @return the last element
     */
    public static List<Object> allElements(WordprocessingMLPackage subDocument) {
        return subDocument.getMainDocumentPart()
                .getContent();
    }

    /**
     * Recursively walk through source to find embedded images and import them in the target document.
     *
     * @param source source document containing image files.
     * @param target target document to add image files to.
     * @return a {@link java.util.Map} object
     */
    public static Map<R, R> walkObjectsAndImportImages(
            WordprocessingMLPackage source,
            WordprocessingMLPackage target
    ) {
        return walkObjectsAndImportImages(source.getMainDocumentPart(),
                                          source,
                                          target);
    }

    /**
     * Recursively walk through source accessor to find embedded images and import the target document.
     *
     * @param container source container to walk.
     * @param source    source document containing image files.
     * @param target    target document to add image files to.
     * @return a {@link java.util.Map} object
     */
    public static Map<R, R> walkObjectsAndImportImages(
            ContentAccessor container,
            WordprocessingMLPackage source,
            WordprocessingMLPackage target
    ) {
        Map<R, R> replacements = new HashMap<>();
        for (Object obj : container.getContent()) {
            Queue<Object> queue = new ArrayDeque<>();
            queue.add(obj);

            while (!queue.isEmpty()) {
                Object currentObj = queue.remove();

                if (currentObj instanceof R currentR && isImageRun(currentR)) {
                    DocxImageExtractor docxImageExtractor = new DocxImageExtractor(
                            source);
                    byte[] imageData = docxImageExtractor.getRunDrawingData(
                            currentR);
                    Integer maxWidth = docxImageExtractor.getRunDrawingMaxWidth(
                            currentR);
                    BinaryPartAbstractImage imagePart = tryCreateImagePart(
                            target,
                            imageData);
                    replacements.put(currentR,
                                     ImageResolver.createRunWithImage(maxWidth,
                                                                      imagePart));
                } else if (currentObj instanceof ContentAccessor contentAccessor)
                    queue.addAll(contentAccessor.getContent());
            }
        }
        return replacements;
    }

    /**
     * Check if a run contains an embedded image.
     *
     * @param run the run to analyze
     * @return true if the run contains an image, false otherwise.
     */
    private static boolean isImageRun(R run) {
        return run.getContent()
                .stream()
                .filter(JAXBElement.class::isInstance)
                .map(JAXBElement.class::cast)
                .map(JAXBElement::getValue)
                .anyMatch(Drawing.class::isInstance);
    }

    private static BinaryPartAbstractImage tryCreateImagePart(
            WordprocessingMLPackage destDocument,
            byte[] imageData
    ) {
        try {
            return BinaryPartAbstractImage.createImagePart(destDocument,
                                                           imageData);
        } catch (Exception e) {
            throw new DocxStamperException(e);
        }
    }

    /**
     * Retrieve all the runs from a document.
     *
     * @param document the document to get the runs from
     * @return the runs
     */
    public static Stream<P> streamParagraphs(WordprocessingMLPackage document) {
        return streamElements(document, P.class);
    }
}
