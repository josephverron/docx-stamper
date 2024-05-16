package pro.verron.officestamper.core;

import org.docx4j.XmlUtils;
import org.docx4j.jaxb.Context;
import org.docx4j.wml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.verron.officestamper.api.OfficeStamperException;

import java.util.List;

import static java.util.Optional.ofNullable;

/**
 * Utility class to handle section breaks in paragraphs.
 *
 * @author Joseph Verron
 * @version ${version}
 * @since 1.6.2
 */
public class SectionUtil {
	private static final Logger log = LoggerFactory.getLogger(SectionUtil.class);

    private SectionUtil() {
		throw new OfficeStamperException("Utility class shouldn't be instantiated");
    }

	private static final ObjectFactory factory = Context.getWmlObjectFactory();

	/**
	 * Creates a new section break object.
	 *
	 * @param firstObject a {@link Object} object
	 * @param parent      a {@link ContentAccessor} object
	 * @return a new section break object.
	 */
	public static SectPr getPreviousSectionBreakIfPresent(Object firstObject, ContentAccessor parent) {
		List<Object> parentContent = parent.getContent();
		int pIndex = parentContent.indexOf(firstObject);

		int i = pIndex - 1;
		while (i >= 0) {
			if (parentContent.get(i) instanceof P prevParagraph) {
				// the first P preceding the object is the one potentially carrying a section break
				PPr pPr = prevParagraph.getPPr();
				if (pPr != null && pPr.getSectPr() != null) {
					return pPr.getSectPr();
				} else return null;
			}
			i--;
		}
		log.info("No previous section break found from : {}, first object index={}", parent, pIndex);
		return null;
	}

	/**
	 * Creates a new section break object.
	 *
	 * @return a new section break object.
	 * @param p a {@link P} object
	 */
	public static SectPr getParagraphSectionBreak(P p) {
		return p.getPPr() != null && p.getPPr().getSectPr() != null
				? p.getPPr().getSectPr()
				: null;
	}

	/**
	 * Creates a new section break object.
	 *
	 * @return a new section break object.
	 * @param objects a {@link List} object
	 */
	public static boolean isOddNumberOfSectionBreaks(List<Object> objects) {
		long count = objects.stream()
				.filter(P.class::isInstance)
				.map(P.class::cast)
							.filter(p -> p.getPPr() != null && p.getPPr().getSectPr() != null)
							.count();
		return count % 2 != 0;
	}

	/**
	 * Creates a new section break object.
	 *
	 * @param sectPr a {@link SectPr} object
	 * @param paragraph a {@link P} object
	 */
	public static void applySectionBreakToParagraph(SectPr sectPr, P paragraph) {
		PPr nextPPr = ofNullable(paragraph.getPPr())
				.orElseGet(factory::createPPr);
		nextPPr.setSectPr(XmlUtils.deepCopy(sectPr));
		paragraph.setPPr(nextPPr);
	}

}
