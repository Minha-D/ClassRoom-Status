package com.university.classroommgmt.importer;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A tiny, read-only .xlsx reader built only on java.util.zip and the JDK's built-in DOM parser
 * (no external libraries — an .xlsx file is just a zip of XML parts).
 * It only understands what this project needs: cell text values and merged-cell ranges,
 * one sheet at a time. It does not evaluate formulas; it reads whatever cached value/text
 * Excel/Sheets already stored in the file.
 */
public class XlsxWorkbook implements Closeable {

    private final ZipFile zip;
    private final List<String> sharedStrings;
    private final LinkedHashMap<String, String> sheetNameToPart; // sheet name -> "xl/worksheets/sheetN.xml"

    private XlsxWorkbook(ZipFile zip, List<String> sharedStrings, LinkedHashMap<String, String> sheetNameToPart) {
        this.zip = zip;
        this.sharedStrings = sharedStrings;
        this.sheetNameToPart = sheetNameToPart;
    }

    public static XlsxWorkbook open(File file) throws IOException {
        ZipFile zip = new ZipFile(file);
        try {
            List<String> shared = readSharedStrings(zip);
            LinkedHashMap<String, String> rIdToTarget = readWorkbookRels(zip);
            LinkedHashMap<String, String> sheetNameToPart = readWorkbookSheets(zip, rIdToTarget);
            return new XlsxWorkbook(zip, shared, sheetNameToPart);
        } catch (IOException | RuntimeException e) {
            zip.close();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    public List<String> getSheetNames() {
        return new ArrayList<>(sheetNameToPart.keySet());
    }

    public Sheet getSheet(String name) throws IOException {
        String part = sheetNameToPart.get(name);
        if (part == null) return null;
        Document doc = parseXmlEntry(zip, part);
        if (doc == null) return null;

        Map<Long, String> values = new HashMap<>();
        Map<Long, Integer> mergeAnchorEndCol = new HashMap<>();

        NodeList rows = doc.getElementsByTagName("row");
        for (int i = 0; i < rows.getLength(); i++) {
            Element rowEl = (Element) rows.item(i);
            NodeList cells = rowEl.getChildNodes();
            for (int j = 0; j < cells.getLength(); j++) {
                Node n = cells.item(j);
                if (!(n instanceof Element)) continue;
                Element c = (Element) n;
                if (!"c".equals(c.getTagName())) continue;
                String ref = c.getAttribute("r");
                int[] colRow = parseCellRef(ref);
                if (colRow == null) continue;
                String value = extractCellText(c);
                if (value != null && !value.isEmpty()) {
                    values.put(key(colRow[0], colRow[1]), value);
                }
            }
        }

        NodeList merges = doc.getElementsByTagName("mergeCell");
        for (int i = 0; i < merges.getLength(); i++) {
            Element m = (Element) merges.item(i);
            String ref = m.getAttribute("ref"); // e.g. "F6:H6"
            String[] parts = ref.split(":");
            if (parts.length != 2) continue;
            int[] start = parseCellRef(parts[0]);
            int[] end = parseCellRef(parts[1]);
            if (start == null || end == null) continue;
            mergeAnchorEndCol.put(key(start[0], start[1]), end[0]);
        }

        return new Sheet(values, mergeAnchorEndCol);
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    /** A single parsed worksheet: cell text values plus merge-anchor -> end-column lookups. */
    public static class Sheet {
        private final Map<Long, String> values;
        private final Map<Long, Integer> mergeAnchorEndCol;

        Sheet(Map<Long, String> values, Map<Long, Integer> mergeAnchorEndCol) {
            this.values = values;
            this.mergeAnchorEndCol = mergeAnchorEndCol;
        }

        /** Raw cell text at (col,row), 1-indexed (col 1 = A). Null if blank (including non-anchor merged cells). */
        public String get(int col, int row) {
            return values.get(key(col, row));
        }

        /** If (col,row) is the top-left anchor of a merged range, returns the range's last column; else null. */
        public Integer mergeEndColumn(int col, int row) {
            return mergeAnchorEndCol.get(key(col, row));
        }

        public int maxRow() {
            int max = 0;
            for (Long k : values.keySet()) max = Math.max(max, (int) (k >> 20));
            return max;
        }
    }

    // ---------- internals ----------

    private static long key(int col, int row) {
        return ((long) row << 20) | (col & 0xFFFFFL);
    }

    private static int[] parseCellRef(String ref) {
        if (ref == null || ref.isEmpty()) return null;
        Matcher m = CELL_REF.matcher(ref.trim());
        if (!m.matches()) return null;
        int col = colIndex(m.group(1));
        int row = Integer.parseInt(m.group(2));
        return new int[]{col, row};
    }

    private static final Pattern CELL_REF = Pattern.compile("([A-Za-z]+)(\\d+)");

    private static int colIndex(String letters) {
        int idx = 0;
        for (char ch : letters.toUpperCase(Locale.ROOT).toCharArray()) {
            idx = idx * 26 + (ch - 'A' + 1);
        }
        return idx;
    }

    private String extractCellText(Element c) {
        String type = c.getAttribute("t");
        if ("s".equals(type)) {
            String idxText = firstChildText(c, "v");
            if (idxText == null) return null;
            try {
                int idx = Integer.parseInt(idxText.trim());
                return idx >= 0 && idx < sharedStrings.size() ? sharedStrings.get(idx) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        } else if ("inlineStr".equals(type)) {
            Element is = firstChildElement(c, "is");
            return is != null ? concatText(is) : null;
        } else {
            // numeric, boolean, cached formula string, etc. — return raw text as-is
            String v = firstChildText(c, "v");
            return v == null ? null : v.trim();
        }
    }

    private static String firstChildText(Element parent, String tag) {
        Element el = firstChildElement(parent, tag);
        return el == null ? null : el.getTextContent();
    }

    private static Element firstChildElement(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element && tag.equals(((Element) n).getTagName())) return (Element) n;
        }
        return null;
    }

    private static String concatText(Element is) {
        // <is><t>...</t></is> or <is><r><t>...</t></r><r><t>...</t></r></is>
        StringBuilder sb = new StringBuilder();
        NodeList tNodes = is.getElementsByTagName("t");
        for (int i = 0; i < tNodes.getLength(); i++) {
            sb.append(tNodes.item(i).getTextContent());
        }
        return sb.toString();
    }

    private static List<String> readSharedStrings(ZipFile zip) throws IOException {
        List<String> out = new ArrayList<>();
        Document doc = parseXmlEntry(zip, "xl/sharedStrings.xml");
        if (doc == null) return out; // no shared strings part — fine, workbook may have none
        NodeList siList = doc.getElementsByTagName("si");
        for (int i = 0; i < siList.getLength(); i++) {
            out.add(concatText((Element) siList.item(i)));
        }
        return out;
    }

    private static LinkedHashMap<String, String> readWorkbookRels(ZipFile zip) throws IOException {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        Document doc = parseXmlEntry(zip, "xl/_rels/workbook.xml.rels");
        if (doc == null) return out;
        NodeList rels = doc.getElementsByTagName("Relationship");
        for (int i = 0; i < rels.getLength(); i++) {
            Element r = (Element) rels.item(i);
            String id = r.getAttribute("Id");
            String target = r.getAttribute("Target");
            out.put(id, target);
        }
        return out;
    }

    private static LinkedHashMap<String, String> readWorkbookSheets(ZipFile zip, Map<String, String> rIdToTarget) throws IOException {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        Document doc = parseXmlEntry(zip, "xl/workbook.xml");
        if (doc == null) throw new IOException("Not a valid .xlsx file (missing xl/workbook.xml).");
        NodeList sheets = doc.getElementsByTagName("sheet");
        for (int i = 0; i < sheets.getLength(); i++) {
            Element s = (Element) sheets.item(i);
            String name = s.getAttribute("name");
            String rId = s.getAttribute("r:id");
            if (rId.isEmpty()) {
                // namespace-agnostic fallback in case the attribute wasn't read as "r:id"
                rId = s.getAttribute("id");
            }
            String target = rIdToTarget.get(rId);
            if (target == null) continue;
            String part = target.startsWith("/") ? target.substring(1) : "xl/" + target;
            out.put(name, part);
        }
        return out;
    }

    private static Document parseXmlEntry(ZipFile zip, String entryName) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readAllBytes();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            try {
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) { /* feature not supported by this parser — continue without it */ }
            dbf.setExpandEntityReferences(false);
            DocumentBuilder builder = dbf.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new IOException("Failed to parse " + entryName + ": " + e.getMessage(), e);
        }
    }
}
