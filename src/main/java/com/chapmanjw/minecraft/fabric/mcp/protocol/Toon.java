package com.chapmanjw.minecraft.fabric.mcp.protocol;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Encodes a Jackson {@link JsonNode} as TOON (Token-Oriented Object Notation) v3.2.
 *
 * <p>TOON is a compact, line-oriented text format that encodes the JSON data model with
 * explicit array lengths, tabular headers for uniform object arrays, and indentation
 * instead of braces — saving 30–60% of LLM tokens vs JSON on typical structured payloads.
 *
 * <p>This encoder uses the default options: 2-space indent, comma delimiter, LF line
 * endings, no trailing newline. See https://toonformat.dev for the full spec.
 *
 * <p>Encoding decisions follow the canonical reference encoder in §3 of the spec:
 * arrays of uniform primitive-only objects use the tabular {@code [N]{f1,f2}:} header;
 * mixed arrays use the expanded list form with hyphen markers; primitive arrays are
 * emitted inline as {@code [N]: v1,v2}. Numbers are normalized to canonical decimal
 * form (no exponents, no trailing zeros). Strings are quoted only when §7.2 requires.
 */
public final class Toon {

    private static final int DEFAULT_INDENT = 2;
    /** Highest C0 control codepoint (U+001F). Strings containing these MUST be quoted (§7.2). */
    private static final int LAST_C0_CONTROL = 0x1F;

    private Toon() {}

    /** Encode {@code root} as a TOON document with the default 2-space indent. */
    public static String encode(JsonNode root) {
        return encode(root, DEFAULT_INDENT);
    }

    /** Encode {@code root} as TOON with a custom indent size (spaces per level). */
    public static String encode(JsonNode root, int indentSize) {
        if (indentSize < 1) {
            throw new IllegalArgumentException("indentSize must be >= 1");
        }
        StringBuilder sb = new StringBuilder(256);
        writeRoot(sb, root, indentSize);
        return sb.toString();
    }

    // --- root form -----------------------------------------------------------

    private static void writeRoot(StringBuilder sb, JsonNode node, int indent) {
        if (node == null || node.isNull()) {
            // Per §5, empty document = empty object. A bare "null" at root is decoded
            // as a primitive root, which is valid. Emit literal "null".
            sb.append("null");
            return;
        }
        if (node.isObject()) {
            // Empty root object → empty document per §5/§8.
            if (node.isEmpty()) {
                return;
            }
            writeObjectFields(sb, (ObjectNode) node, 0, indent);
            return;
        }
        if (node.isArray()) {
            writeRootArray(sb, (ArrayNode) node, indent);
            return;
        }
        // Root primitive — §5 allows a single primitive line.
        sb.append(formatScalar(node, /*inline=*/ false));
    }

    private static void writeRootArray(StringBuilder sb, ArrayNode arr, int indent) {
        int n = arr.size();
        if (n == 0) {
            // §9.1 — encoders SHOULD emit `[]` on its own line at root.
            sb.append("[]");
            return;
        }
        if (allPrimitives(arr)) {
            sb.append('[').append(n).append("]: ");
            appendInlinePrimitiveValues(sb, arr);
            return;
        }
        List<String> tabularFields = tabularFields(arr);
        if (tabularFields != null) {
            sb.append('[').append(n).append("]{");
            appendFieldNames(sb, tabularFields);
            sb.append("}:");
            for (JsonNode element : arr) {
                sb.append('\n');
                appendIndent(sb, 1, indent);
                appendTabularRow(sb, element, tabularFields);
            }
            return;
        }
        // Mixed / non-uniform expanded list at root.
        sb.append('[').append(n).append("]:");
        for (JsonNode element : arr) {
            sb.append('\n');
            appendIndent(sb, 1, indent);
            writeListItem(sb, element, 1, indent);
        }
    }

    // --- objects -------------------------------------------------------------

    private static void writeObjectFields(StringBuilder sb, ObjectNode obj, int depth, int indent) {
        Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
        boolean first = true;
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (!first) {
                sb.append('\n');
            }
            first = false;
            writeField(sb, e.getKey(), e.getValue(), depth, indent);
        }
    }

    private static void writeField(
            StringBuilder sb, String key, JsonNode value, int depth, int indent) {
        appendIndent(sb, depth, indent);
        sb.append(encodeKey(key));

        if (value == null || value.isNull()) {
            sb.append(": null");
            return;
        }
        if (value.isValueNode()) {
            sb.append(": ").append(formatScalar(value, /*inline=*/ false));
            return;
        }
        if (value.isObject()) {
            if (value.isEmpty()) {
                // Per §8, `key:` is an empty/nested object. No further indent needed.
                sb.append(':');
                return;
            }
            sb.append(":\n");
            writeObjectFields(sb, (ObjectNode) value, depth + 1, indent);
            return;
        }
        if (value.isArray()) {
            // Regular key:array field — rows/items at depth + 1.
            writeArrayField(sb, (ArrayNode) value, depth + 1, indent);
            return;
        }
        throw new IllegalStateException("Unhandled node type: " + value.getNodeType());
    }

    // --- arrays --------------------------------------------------------------

    /**
     * Emit an array value that begins on the current line at logical {@code depth}.
     *
     * @param childDepth depth at which sub-rows / sub-items must appear. For a regular
     *                    {@code key: array} field this is {@code depth + 1}. For a
     *                    tabular array that sits on a list-item hyphen line, §10 requires
     *                    rows to be at {@code depth + 2} relative to the hyphen, so the
     *                    caller passes {@code listItemDepth + 2}.
     */
    private static void writeArrayField(
            StringBuilder sb, ArrayNode arr, int childDepth, int indent) {
        int n = arr.size();
        if (n == 0) {
            // §9.1 — `key: []` is the preferred empty-array form for object fields.
            sb.append(": []");
            return;
        }
        if (allPrimitives(arr)) {
            sb.append('[').append(n).append("]: ");
            appendInlinePrimitiveValues(sb, arr);
            return;
        }
        List<String> tabularFields = tabularFields(arr);
        if (tabularFields != null) {
            sb.append('[').append(n).append("]{");
            appendFieldNames(sb, tabularFields);
            sb.append("}:");
            for (JsonNode element : arr) {
                sb.append('\n');
                appendIndent(sb, childDepth, indent);
                appendTabularRow(sb, element, tabularFields);
            }
            return;
        }
        // Mixed / non-uniform expanded list form.
        sb.append('[').append(n).append("]:");
        for (JsonNode element : arr) {
            sb.append('\n');
            appendIndent(sb, childDepth, indent);
            writeListItem(sb, element, childDepth, indent);
        }
    }

    private static void writeListItem(
            StringBuilder sb, JsonNode element, int depth, int indent) {
        if (element == null || element.isNull()) {
            sb.append("- null");
            return;
        }
        if (element.isValueNode()) {
            sb.append("- ").append(formatScalar(element, /*inline=*/ false));
            return;
        }
        if (element.isArray()) {
            // Inline primitive sub-array as a list item per §9.2.
            ArrayNode inner = (ArrayNode) element;
            if (inner.isEmpty()) {
                // §9.2 — `key: []` does NOT apply to list-item inner arrays; use `- [0]:`.
                sb.append("- [0]:");
                return;
            }
            if (allPrimitives(inner)) {
                sb.append("- [").append(inner.size()).append("]: ");
                appendInlinePrimitiveValues(sb, inner);
                return;
            }
            // Sub-array of objects / mixed inside a list item: header on hyphen line,
            // items recurse at depth +1.
            List<String> innerFields = tabularFields(inner);
            if (innerFields != null) {
                sb.append("- [").append(inner.size()).append("]{");
                appendFieldNames(sb, innerFields);
                sb.append("}:");
                for (JsonNode child : inner) {
                    sb.append('\n');
                    appendIndent(sb, depth + 1, indent);
                    appendTabularRow(sb, child, innerFields);
                }
                return;
            }
            sb.append("- [").append(inner.size()).append("]:");
            for (JsonNode child : inner) {
                sb.append('\n');
                appendIndent(sb, depth + 1, indent);
                writeListItem(sb, child, depth + 1, indent);
            }
            return;
        }
        if (element.isObject()) {
            ObjectNode obj = (ObjectNode) element;
            if (obj.isEmpty()) {
                // Per §10 — bare "-" for an empty list-item object.
                sb.append('-');
                return;
            }
            writeListItemObject(sb, obj, depth, indent);
            return;
        }
        throw new IllegalStateException("Unhandled list-item node: " + element.getNodeType());
    }

    private static void writeListItemObject(
            StringBuilder sb, ObjectNode obj, int depth, int indent) {
        // Per §10: first field goes on the hyphen line. Remaining fields at depth + 1.
        Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
        Map.Entry<String, JsonNode> first = it.next();

        sb.append("- ");
        writeInlineFieldHead(sb, first.getKey(), first.getValue(), depth, indent);

        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            sb.append('\n');
            writeField(sb, e.getKey(), e.getValue(), depth + 1, indent);
        }
    }

    /** Emit a field that begins on the same line as a hyphen list marker. */
    private static void writeInlineFieldHead(
            StringBuilder sb, String key, JsonNode value, int depth, int indent) {
        sb.append(encodeKey(key));
        if (value == null || value.isNull()) {
            sb.append(": null");
            return;
        }
        if (value.isValueNode()) {
            sb.append(": ").append(formatScalar(value, /*inline=*/ false));
            return;
        }
        if (value.isObject()) {
            if (value.isEmpty()) {
                sb.append(':');
                return;
            }
            sb.append(":\n");
            writeObjectFields(sb, (ObjectNode) value, depth + 1, indent);
            return;
        }
        if (value.isArray()) {
            // §10 — tabular rows / sub-items under a list-item-head array live at
            // depth + 2 relative to the hyphen line. `depth` here is the list-item depth.
            writeArrayField(sb, (ArrayNode) value, depth + 2, indent);
            return;
        }
        throw new IllegalStateException("Unhandled node type in list-item head: " + value.getNodeType());
    }

    // --- helpers -------------------------------------------------------------

    private static void appendIndent(StringBuilder sb, int depth, int indent) {
        int spaces = depth * indent;
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
    }

    private static void appendInlinePrimitiveValues(StringBuilder sb, ArrayNode arr) {
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(formatScalar(arr.get(i), /*inline=*/ true));
        }
    }

    private static void appendFieldNames(StringBuilder sb, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(encodeKey(fields.get(i)));
        }
    }

    private static void appendTabularRow(StringBuilder sb, JsonNode obj, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            JsonNode cell = obj.get(fields.get(i));
            // A missing cell is treated as null. The tabular check only allowed homogeneous
            // shapes so this should never fire in practice; defensive nonetheless.
            sb.append(cell == null ? "null" : formatScalar(cell, /*inline=*/ true));
        }
    }

    // --- shape detection -----------------------------------------------------

    private static boolean allPrimitives(ArrayNode arr) {
        for (JsonNode element : arr) {
            if (element != null && (element.isObject() || element.isArray())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Return the ordered field list if {@code arr} qualifies for tabular encoding per
     * §9.3, or {@code null} otherwise.
     */
    private static List<String> tabularFields(ArrayNode arr) {
        if (arr.isEmpty()) {
            return null;
        }
        // All elements must be non-empty objects with primitive-only values.
        Set<String> keySet = null;
        List<String> firstOrder = null;
        for (JsonNode element : arr) {
            if (element == null || !element.isObject() || element.isEmpty()) {
                return null;
            }
            // Verify every value is a primitive (no nested objects/arrays).
            Iterator<Map.Entry<String, JsonNode>> it = element.fields();
            Set<String> localKeys = new LinkedHashSet<>();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode v = e.getValue();
                if (v != null && (v.isObject() || v.isArray())) {
                    return null;
                }
                localKeys.add(e.getKey());
            }
            if (keySet == null) {
                keySet = localKeys;
                firstOrder = new ArrayList<>(localKeys);
            } else if (!keySet.equals(localKeys)) {
                // Key sets differ → not tabular.
                return null;
            }
        }
        return firstOrder;
    }

    // --- key encoding --------------------------------------------------------

    /** Returns an unquoted key if §7.3 allows it, else a quoted-and-escaped key. */
    static String encodeKey(String key) {
        if (key == null) {
            // A null key is not really valid JSON, but be defensive: emit "" quoted.
            return "\"\"";
        }
        if (isUnquotedKeyEligible(key)) {
            return key;
        }
        return quoteAndEscape(key);
    }

    private static boolean isUnquotedKeyEligible(String s) {
        if (s.isEmpty()) {
            return false;
        }
        char c0 = s.charAt(0);
        if (!isAlpha(c0) && c0 != '_') {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!isAlpha(c) && !isDigit(c) && c != '_' && c != '.') {
                return false;
            }
        }
        return true;
    }

    // --- scalar formatting ---------------------------------------------------

    /**
     * Format a scalar JSON value (string, number, boolean, null).
     *
     * @param inline if true, the value will appear in an inline array or tabular row;
     *               such positions need comma quoting per §11.1. If false, the value is
     *               an object field value or root primitive; only the document delimiter
     *               (comma) triggers quoting per §11.1.
     */
    private static String formatScalar(JsonNode node, boolean inline) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isBoolean()) {
            return node.booleanValue() ? "true" : "false";
        }
        if (node.isNumber()) {
            return formatNumber(node);
        }
        // Treat everything else (textual, binary) as a string.
        String s = node.asText();
        return formatString(s, inline);
    }

    /**
     * Format a string per §7.2 — quote only when required. {@code inline} matters here
     * because inline cells / row values must quote on comma (the active delimiter);
     * object-field values must quote on the document delimiter (also comma in our default).
     * In our default-comma encoding the two are the same, so this is symmetric — but the
     * parameter is wired through so a future tab/pipe delimiter option works cleanly.
     */
    static String formatString(String s, boolean inline) {
        if (mustQuote(s, inline)) {
            return quoteAndEscape(s);
        }
        return s;
    }

    private static boolean mustQuote(String s, boolean inline) {
        if (s.isEmpty()) {
            return true;
        }
        // Leading or trailing whitespace
        if (Character.isWhitespace(s.charAt(0)) || Character.isWhitespace(s.charAt(s.length() - 1))) {
            return true;
        }
        // Literals
        if ("true".equals(s) || "false".equals(s) || "null".equals(s)) {
            return true;
        }
        // Numeric-like — /^-?\d+(?:\.\d+)?(?:e[+-]?\d+)?$/i
        if (looksNumeric(s)) {
            return true;
        }
        // Starts with hyphen (§7.2)
        if (s.charAt(0) == '-') {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case ':':
                case '"':
                case '\\':
                case '[':
                case ']':
                case '{':
                case '}':
                case ',':
                    // Comma always triggers quoting because it's our document delimiter
                    // AND (when inline) it's the active delimiter — same character.
                    return true;
                default:
                    if (c <= LAST_C0_CONTROL) {
                        return true;
                    }
            }
        }
        // `inline` is wired through for a future tab/pipe delimiter mode where the
        // active delimiter (rows) differs from the document delimiter (fields). With
        // the default comma delimiter the comma check above already covers both cases,
        // so the parameter is informationally retained but not consulted further.
        return false;
    }

    private static boolean looksNumeric(String s) {
        int i = 0;
        int n = s.length();
        if (i < n && s.charAt(i) == '-') {
            i++;
        }
        if (i >= n) {
            return false;
        }
        int digitStart = i;
        while (i < n && isDigit(s.charAt(i))) {
            i++;
        }
        if (i == digitStart) {
            return false;
        }
        // Optional fractional
        if (i < n && s.charAt(i) == '.') {
            i++;
            int fracStart = i;
            while (i < n && isDigit(s.charAt(i))) {
                i++;
            }
            if (i == fracStart) {
                return false;
            }
        }
        // Optional exponent
        if (i < n && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            i++;
            if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                i++;
            }
            int expStart = i;
            while (i < n && isDigit(s.charAt(i))) {
                i++;
            }
            if (i == expStart) {
                return false;
            }
        }
        return i == n;
    }

    private static String quoteAndEscape(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '"':
                    out.append("\\\"");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
        return out.toString();
    }

    // --- number canonicalization --------------------------------------------

    private static String formatNumber(JsonNode node) {
        if (node.isInt() || node.isLong() || node.isShort() || node.isBigInteger()) {
            // Integer types are already canonical decimal — no exponent, no leading zeros
            // (Long.toString never produces leading zeros for non-zero values).
            return node.bigIntegerValue().toString();
        }
        // Floating-point: convert via BigDecimal then strip trailing zeros + use plain form.
        double d = node.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            // Per §3, non-finite numbers normalize to null at encode time.
            return "null";
        }
        BigDecimal bd;
        if (node.isFloat()) {
            // Use the float's string form to avoid 0.1f → 0.10000000149011612.
            bd = new BigDecimal(Float.toString(node.floatValue()));
        } else if (node.isBigDecimal()) {
            bd = node.decimalValue();
        } else {
            bd = new BigDecimal(Double.toString(d));
        }
        // Normalize -0 → 0.
        if (bd.signum() == 0) {
            return "0";
        }
        BigDecimal stripped = bd.stripTrailingZeros();
        // stripTrailingZeros can produce e.g. 1E+2 for 100; force plain decimal.
        String plain = stripped.toPlainString();
        // If the fractional part is empty after strip, plain is just digits.
        // If stripped exponent was positive (e.g. "100" → "1E+2"), toPlainString still
        // expands properly. But for "1.0" → stripped "1", plain "1". Good.
        return plain;
    }

    // --- char classes --------------------------------------------------------

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
