package pro.verron.officestamper.test;

import jakarta.xml.bind.JAXBElement;
import org.docx4j.TextUtils;
import org.docx4j.TraversalUtil;
import org.docx4j.dml.*;
import org.docx4j.dml.picture.Pic;
import org.docx4j.dml.wordprocessingDrawing.Inline;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.PresentationMLPackage;
import org.docx4j.openpackaging.packages.SpreadsheetMLPackage;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.WordprocessingML.BinaryPartAbstractImage;
import org.docx4j.openpackaging.parts.WordprocessingML.CommentsPart;
import org.docx4j.wml.*;
import org.docx4j.wml.Comments.Comment;
import org.xlsx4j.org.apache.poi.ss.usermodel.DataFormatter;
import org.xlsx4j.sml.Cell;
import pro.verron.officestamper.api.OfficeStamperException;
import pro.verron.officestamper.experimental.ExcelCollector;
import pro.verron.officestamper.experimental.PowerpointCollector;
import pro.verron.officestamper.experimental.PowerpointParagraph;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;

/**
 * <p>Stringifier class.</p>
 *
 * @author Joseph Verron
 * @version ${version}
 * @since 1.6.5
 */
public class Stringifier {

    private final Supplier<WordprocessingMLPackage> documentSupplier;

    /**
     * <p>Constructor for Stringifier.</p>
     *
     * @since 1.6.6
     */
    public Stringifier(Supplier<WordprocessingMLPackage> documentSupplier) {
        this.documentSupplier = documentSupplier;
    }

    public static String stringifyPowerpoint(PresentationMLPackage presentation) {
        var collector = new PowerpointCollector<>(CTTextParagraph.class);
        collector.visit(presentation);
        var collected = collector.collect();

        var powerpoint = new StringBuilder();
        for (CTTextParagraph paragraph : collected) {
            powerpoint.append(new PowerpointParagraph(paragraph).asString());
        }
        return powerpoint.toString();
    }

    public static String stringifyExcel(SpreadsheetMLPackage presentation) {
        var collector = new ExcelCollector<>(Cell.class);
        collector.visit(presentation);
        var formatter = new DataFormatter();
        return collector.collect()
                        .stream()
                        .map(cell -> cell.getR() + ": " + formatter.formatCellValue(cell))
                        .collect(joining("\n"));
    }

    private static MessageDigest findDigest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new OfficeStamperException(e);
        }
    }

    /**
     * Finds a comment with the given ID in the specified WordprocessingMLPackage document.
     *
     * @param document the WordprocessingMLPackage document to search for the comment
     * @param id       the ID of the comment to find
     *
     * @return an Optional containing the Comment if found, or an empty Optional if not found
     *
     * @throws Docx4JException if an error occurs while searching for the comment
     */
    public static Optional<Comment> findComment(
            WordprocessingMLPackage document, BigInteger id
    )
            throws Docx4JException {
        var name = new PartName("/word/comments.xml");
        var parts = document.getParts();
        var wordComments = (CommentsPart) parts.get(name);
        var comments = wordComments.getContents();
        return comments.getComment()
                       .stream()
                       .filter(idEqual(id))
                       .findFirst();
    }

    private static Predicate<Comment> idEqual(BigInteger id) {
        return comment -> {
            var commentId = comment.getId();
            return commentId.equals(id);
        };
    }

    private static void extract(
            Map<String, Object> map,
            String key,
            Object value
    ) {
        if (value != null)
            map.put(key, value);
    }

    private static Function<Entry<?, ?>, String> format(String format) {
        return entry -> format.formatted(entry.getKey(), entry.getValue());
    }

    private String stringify(Text text) {
        return TextUtils.getText(text);
    }

    private WordprocessingMLPackage document() {
        return documentSupplier.get();
    }

    private String stringify(R.LastRenderedPageBreak ignored) {
        return ""; // do not render
    }

    private String stringify(Br br) {
        return "|BR(" + br.getType() + ")|";
    }

    private String stringify(R.Tab ignored) {
        return "|TAB|";
    }

    /**
     * <p>stringify.</p>
     *
     * @param blip a {@link org.docx4j.dml.CTBlip} object
     *
     * @return a {@link java.lang.String} object
     *
     * @since 1.6.6
     */
    private String stringify(CTBlip blip) {
        var image = document()
                .getParts()
                .getParts()
                .entrySet()
                .stream()
                .filter(e -> e.getKey()
                              .getName()
                              .contains(blip.getEmbed()))
                .map(Entry::getValue)
                .findFirst()
                .map(BinaryPartAbstractImage.class::cast)
                .orElseThrow();
        byte[] imageBytes = image.getBytes();
        return "%s:%s:%s:sha1=%s:cy=$d".formatted(
                blip.getEmbed(),
                image.getContentType(),
                humanReadableByteCountSI(imageBytes.length),
                sha1b64(imageBytes));
    }

    /**
     * <p>humanReadableByteCountSI.</p>
     *
     * @param bytes a long
     *
     * @return a {@link java.lang.String} object
     *
     * @since 1.6.6
     */
    private String humanReadableByteCountSI(long bytes) {
        if (-1000 < bytes && bytes < 1000) return bytes + "B";

        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format(Locale.US, "%.1f%cB", bytes / 1000.0,
                ci.current());
    }

    private String sha1b64(byte[] imageBytes) {
        MessageDigest messageDigest = findDigest();
        Base64.Encoder encoder = Base64.getEncoder();
        byte[] digest = messageDigest.digest(imageBytes);
        return encoder.encodeToString(digest);
    }

    /**
     * <p>stringify.</p>
     *
     * @param o a {@link java.lang.Object} object
     *
     * @return a {@link java.lang.String} object
     *
     * @since 1.6.6
     */
    public String stringify(Object o) {
        if (o instanceof JAXBElement<?> jaxb) return stringify(jaxb.getValue());
        if (o instanceof List<?> list) return stringify(list);
        if (o instanceof Text text) return stringify(text);
        if (o instanceof P p) return stringify(p);
        if (o instanceof Drawing drawing) return stringify(drawing);
        if (o instanceof Inline inline) return stringify(inline);
        if (o instanceof Graphic graphic) return getStringify(graphic);
        if (o instanceof GraphicData graphicData) return stringify(graphicData);
        if (o instanceof Pic pic) return stringify(pic);
        if (o instanceof CTBlipFillProperties bfp) return stringify(bfp);
        if (o instanceof CTBlip blip) return stringify(blip);
        if (o instanceof R.LastRenderedPageBreak lrpb) return stringify(lrpb);
        if (o instanceof Br br) return stringify(br);
        if (o instanceof R.Tab tab) return stringify(tab);
        if (o instanceof R.CommentReference cr) return stringify(cr);
        if (o == null) throw new RuntimeException("Unsupported content: NULL");
        throw new RuntimeException("Unsupported content: " + o.getClass());
    }

    private String stringify(Pic pic) {
        return stringify(pic.getBlipFill());
    }

    private String stringify(CTBlipFillProperties blipFillProperties) {
        return stringify(blipFillProperties.getBlip());
    }

    private String stringify(R.CommentReference commentReference) {
        try {
            return findComment(document(),
                    commentReference.getId())
                    .map(c -> stringify(c.getContent()))
                    .orElseThrow();
        } catch (Docx4JException e) {
            throw new RuntimeException(e);
        }
    }

    private String stringify(GraphicData graphicData) {
        return stringify(graphicData.getPic());
    }

    private String getStringify(Graphic graphic) {
        return stringify(graphic.getGraphicData());
    }

    private String stringify(Inline inline) {
        var graphic = inline.getGraphic();
        var extent = inline.getExtent();
        return "%s:%d".formatted(
                stringify(graphic),
                extent.getCx());
    }

    private String stringify(Drawing drawing) {
        return stringify(drawing.getAnchorOrInline());
    }

    private String stringify(List<?> list) {
        return list.stream()
                   .map(this::stringify)
                   .collect(joining());
    }

    /**
     * <p>stringify.</p>
     *
     * @param spacing a {@link org.docx4j.wml.PPrBase.Spacing} object
     *
     * @return a {@link java.util.Optional} object
     *
     * @since 1.6.6
     */
    private Optional<String> stringify(PPrBase.Spacing spacing) {
        if (spacing == null) return Optional.empty();
        SortedMap<String, Object> map = new TreeMap<>();
        extract(map, "after", spacing.getAfter());
        extract(map, "before", spacing.getBefore());
        extract(map, "beforeLines", spacing.getBeforeLines());
        extract(map, "afterLines", spacing.getAfterLines());
        extract(map, "line", spacing.getLine());
        extract(map, "lineRule", spacing.getLineRule());
        return map.isEmpty()
                ? Optional.empty()
                : Optional.of(map.entrySet()
                                 .stream()
                                 .map(format("%s=%s"))
                                 .collect(joining(",", "{", "}")));
    }

    /**
     * <p>stringify.</p>
     *
     * @param p a {@link org.docx4j.wml.P} object
     *
     * @return a {@link java.lang.String} object
     *
     * @since 1.6.6
     */
    private String stringify(P p) {
        String runs = extractDocumentRuns(p);
        return ofNullable(p.getPPr())
                .flatMap(this::stringify)
                .map(ppr -> "❬%s❘%s❭".formatted(runs, ppr))
                .orElse(runs);
    }

    /**
     * <p>extractDocumentRuns.</p>
     *
     * @param p a {@link java.lang.Object} object
     *
     * @return a {@link java.lang.String} object
     *
     * @since 1.6.6
     */
    public String extractDocumentRuns(Object p) {
        var runCollector = new RunCollector();
        TraversalUtil.visit(p, runCollector);
        return runCollector
                .runs()
                .filter(r -> !r.getContent()
                               .isEmpty())
                .map(this::stringify)
                .collect(joining());
    }

    private Optional<String> stringify(PPr pPr) {
        if (pPr == null)
            return Optional.empty();
        var set = new TreeSet<String>();
        if (pPr.getJc() != null) set.add("jc=" + pPr.getJc()
                                                    .getVal()
                                                    .value());
        if (pPr.getInd() != null) set.add("ind=" + pPr.getInd()
                                                      .getLeft()
                                                      .intValue());
        if (pPr.getKeepLines() != null)
            set.add("keepLines=" + pPr.getKeepLines()
                                      .isVal());
        if (pPr.getKeepNext() != null) set.add("keepNext=" + pPr.getKeepNext()
                                                                .isVal());
        if (pPr.getOutlineLvl() != null)
            set.add("outlineLvl=" + pPr.getOutlineLvl()
                                       .getVal()
                                       .intValue());
        if (pPr.getPageBreakBefore() != null)
            set.add("pageBreakBefore=" + pPr.getPageBreakBefore()
                                            .isVal());
        if (pPr.getPBdr() != null) set.add("pBdr=xxx");
        if (pPr.getPPrChange() != null) set.add("pPrChange=xxx");
        stringify(pPr.getRPr())
                .ifPresent(set::add);
        stringify(pPr.getSectPr())
                .ifPresent(set::add);
        if (pPr.getShd() != null) set.add("shd=xxx");
        stringify(pPr.getSpacing())
                .ifPresent(spacing -> set.add("spacing=" + spacing));
        if (pPr.getSuppressAutoHyphens() != null)
            set.add("suppressAutoHyphens=xxx");
        if (pPr.getSuppressLineNumbers() != null)
            set.add("suppressLineNumbers=xxx");
        if (pPr.getSuppressOverlap() != null) set.add("suppressOverlap=xxx");
        if (pPr.getTabs() != null) set.add("tabs=xxx");
        if (pPr.getTextAlignment() != null) set.add("textAlignment=xxx");
        if (pPr.getTextDirection() != null) set.add("textDirection=xxx");
        if (pPr.getTopLinePunct() != null) set.add("topLinePunct=xxx");
        if (pPr.getWidowControl() != null) set.add("widowControl=xxx");
        if (pPr.getWordWrap() != null) set.add("wordWrap=xxx");
        if (pPr.getFramePr() != null) set.add("framePr=xxx");
        if (pPr.getDivId() != null) set.add("divId=xxx");
        if (pPr.getCnfStyle() != null) set.add("cnfStyle=xxx");
        if (set.isEmpty())
            return Optional.empty();
        return Optional.of(String.join(",", set));
    }

    /**
     * <p>stringify.</p>
     *
     * @param run a {@link org.docx4j.wml.R} object
     *
     * @return a {@link java.lang.String} object
     *
     * @since 1.6.6
     */
    private String stringify(R run) {
        String serialized = stringify(run.getContent());
        if (serialized.isEmpty())
            return "";
        return ofNullable(run.getRPr())
                .flatMap(this::stringify)
                .map(rPr -> "❬%s❘%s❭".formatted(serialized, rPr))
                .orElse(serialized);
    }

    /**
     * <p>stringify.</p>
     *
     * @param rPr a {@link org.docx4j.wml.RPrAbstract} object
     *
     * @return a {@link java.lang.String} object
     *
     * @since 1.6.6
     */
    private Optional<String> stringify(RPrAbstract rPr) {
        if (rPr == null)
            return Optional.empty();
        var set = new TreeSet<String>();
        if (rPr.getB() != null) set.add("b=" + rPr.getB()
                                                  .isVal());
        if (rPr.getBdr() != null) set.add("bdr=xxx");
        if (rPr.getCaps() != null) set.add("caps=" + rPr.getCaps()
                                                        .isVal());
        if (rPr.getColor() != null) set.add("color=" + rPr.getColor()
                                                          .getVal());
        if (rPr.getDstrike() != null) set.add("dstrike=" + rPr.getDstrike()
                                                              .isVal());
        if (rPr.getI() != null) set.add("i=" + rPr.getI()
                                                  .isVal());
        if (rPr.getKern() != null) set.add("kern=" + rPr.getKern()
                                                        .getVal()
                                                        .intValue());
        if (rPr.getLang() != null) set.add("lang=" + rPr.getLang()
                                                        .getVal());
        //if (rPr.getRFonts() != null) set.add("rFonts=xxx:" + rPr.getRFonts().getHint().value());
        if (rPr.getRPrChange() != null) set.add("rPrChange=xxx");
        if (rPr.getRStyle() != null) set.add("rStyle=" + rPr.getRStyle()
                                                            .getVal());
        if (rPr.getRtl() != null) set.add("rtl=" + rPr.getRtl()
                                                      .isVal());
        if (rPr.getShadow() != null) set.add("shadow=" + rPr.getShadow()
                                                            .isVal());
        if (rPr.getShd() != null) set.add("shd=" + rPr.getShd()
                                                      .getColor());
        if (rPr.getSmallCaps() != null)
            set.add("smallCaps=" + rPr.getSmallCaps()
                                      .isVal());
        if (rPr.getVertAlign() != null)
            set.add("vertAlign=" + rPr.getVertAlign()
                                      .getVal()
                                      .value());
        if (rPr.getSpacing() != null) set.add("spacing=" + rPr.getSpacing()
                                                              .getVal()
                                                              .intValue());
        if (rPr.getStrike() != null) set.add("strike=" + rPr.getStrike()
                                                            .isVal());
        if (rPr.getOutline() != null) set.add("outline=" + rPr.getOutline()
                                                              .isVal());
        if (rPr.getEmboss() != null) set.add("emboss=" + rPr.getEmboss()
                                                            .isVal());
        if (rPr.getImprint() != null) set.add("imprint=" + rPr.getImprint()
                                                              .isVal());
        if (rPr.getNoProof() != null) set.add("noProof=" + rPr.getNoProof()
                                                              .isVal());
        if (rPr.getSpecVanish() != null)
            set.add("specVanish=" + rPr.getSpecVanish()
                                       .isVal());
        if (rPr.getU() != null) set.add("u=" + rPr.getU()
                                                  .getVal()
                                                  .value());
        if (rPr.getVanish() != null) set.add("vanish=" + rPr.getVanish()
                                                            .isVal());
        if (rPr.getW() != null) set.add("w=" + rPr.getW()
                                                  .getVal());
        if (rPr.getWebHidden() != null)
            set.add("webHidden=" + rPr.getWebHidden()
                                      .isVal());
        if (rPr.getHighlight() != null)
            set.add("highlight=" + rPr.getHighlight()
                                      .getVal());
        if (rPr.getEffect() != null) set.add("effect=" + rPr.getEffect()
                                                            .getVal()
                                                            .value());
        if (set.isEmpty())
            return Optional.empty();
        return Optional.of(String.join(",", set));
    }

    private Optional<String> stringify(SectPr sectPr) {
        if (sectPr == null)
            return Optional.empty();
        var set = new TreeSet<String>();
        if (sectPr.getEGHdrFtrReferences() != null)
            set.add("eGHdrFtrReferences=xxx");
        if (sectPr.getPgSz() != null)
            set.add("pgSz={" + stringify(sectPr.getPgSz()) + "}");
        if (sectPr.getPgMar() != null) set.add("pgMar=xxx");
        if (sectPr.getPaperSrc() != null) set.add("paperSrc=xxx");
        if (sectPr.getBidi() != null) set.add("bidi=xxx");
        if (sectPr.getRtlGutter() != null) set.add("rtlGutter=xxx");
        if (sectPr.getDocGrid() != null) set.add("docGrid=xxx");
        if (sectPr.getFormProt() != null) set.add("formProt=xxx");
        if (sectPr.getVAlign() != null) set.add("vAlign=xxx");
        if (sectPr.getNoEndnote() != null) set.add("noEndnote=xxx");
        if (sectPr.getTitlePg() != null) set.add("titlePg=xxx");
        if (sectPr.getTextDirection() != null) set.add("textDirection=xxx");
        if (sectPr.getRtlGutter() != null) set.add("rtlGutter=xxx");
        if (set.isEmpty()) return Optional.empty();
        return Optional.of(String.join(",", set));
    }

    private String stringify(SectPr.PgSz pgSz) {
        var set = new TreeSet<String>();
        if (pgSz.getOrient() != null) set.add("orient=" + pgSz.getOrient()
                                                              .value());
        if (pgSz.getW() != null) set.add("w=" + pgSz.getW()
                                                    .intValue());
        if (pgSz.getH() != null) set.add("h=" + pgSz.getH()
                                                    .intValue());
        if (pgSz.getCode() != null) set.add("code=" + pgSz.getCode()
                                                          .intValue());
        return String.join(",", set);
    }
}
