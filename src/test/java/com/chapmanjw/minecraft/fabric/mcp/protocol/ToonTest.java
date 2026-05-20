package com.chapmanjw.minecraft.fabric.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

class ToonTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    // --- scalars at root -----------------------------------------------------

    @Test
    void nullRoot() {
        assertEquals("null", Toon.encode(null));
        assertEquals("null", Toon.encode(NF.nullNode()));
    }

    @Test
    void primitiveRoots() {
        assertEquals("hello", Toon.encode(NF.textNode("hello")));
        assertEquals("42", Toon.encode(NF.numberNode(42)));
        assertEquals("true", Toon.encode(NF.booleanNode(true)));
        assertEquals("false", Toon.encode(NF.booleanNode(false)));
    }

    // --- empty document forms ------------------------------------------------

    @Test
    void emptyObjectRootProducesEmptyDocument() {
        assertEquals("", Toon.encode(NF.objectNode()));
    }

    @Test
    void emptyArrayRootEmitsBracketLiteral() {
        assertEquals("[]", Toon.encode(NF.arrayNode()));
    }

    // --- objects -------------------------------------------------------------

    @Test
    void flatObject() {
        ObjectNode o = NF.objectNode();
        o.put("id", 123);
        o.put("name", "Ada");
        o.put("active", true);
        assertEquals("id: 123\nname: Ada\nactive: true", Toon.encode(o));
    }

    @Test
    void nestedObject() {
        ObjectNode o = NF.objectNode();
        ObjectNode user = o.putObject("user");
        user.put("id", 123);
        user.put("name", "Ada");
        assertEquals("user:\n  id: 123\n  name: Ada", Toon.encode(o));
    }

    @Test
    void emptyNestedObjectEmitsBareColon() {
        ObjectNode o = NF.objectNode();
        o.putObject("meta");
        o.put("after", 1);
        assertEquals("meta:\nafter: 1", Toon.encode(o));
    }

    // --- arrays --------------------------------------------------------------

    @Test
    void inlinePrimitiveArray() {
        ObjectNode o = NF.objectNode();
        ArrayNode arr = o.putArray("tags");
        arr.add("admin");
        arr.add("ops");
        arr.add("dev");
        assertEquals("tags[3]: admin,ops,dev", Toon.encode(o));
    }

    @Test
    void emptyArrayFieldUsesBracketShorthand() {
        ObjectNode o = NF.objectNode();
        o.putArray("tags");
        assertEquals("tags: []", Toon.encode(o));
    }

    @Test
    void tabularArrayOfUniformObjects() {
        ObjectNode o = NF.objectNode();
        ArrayNode items = o.putArray("items");
        ObjectNode a = items.addObject();
        a.put("sku", "A1");
        a.put("qty", 2);
        a.put("price", 9.99);
        ObjectNode b = items.addObject();
        b.put("sku", "B2");
        b.put("qty", 1);
        b.put("price", 14.5);
        assertEquals(
                "items[2]{sku,qty,price}:\n  A1,2,9.99\n  B2,1,14.5",
                Toon.encode(o));
    }

    @Test
    void nonUniformArrayFallsBackToExpandedList() {
        ObjectNode o = NF.objectNode();
        ArrayNode arr = o.putArray("items");
        ObjectNode a = arr.addObject();
        a.put("kind", "a");
        a.put("x", 1);
        ObjectNode b = arr.addObject();
        b.put("kind", "b");
        b.put("y", 2);
        // Keys differ → not tabular; expanded list form.
        String out = Toon.encode(o);
        // Header line + 2 list items.
        assertEquals(
                "items[2]:\n  - kind: a\n    x: 1\n  - kind: b\n    y: 2",
                out);
    }

    @Test
    void objectWithNestedFieldDisablesTabular() {
        // An object array where one element has a nested object should NOT be tabular.
        ObjectNode o = NF.objectNode();
        ArrayNode arr = o.putArray("items");
        ObjectNode a = arr.addObject();
        a.put("id", 1);
        a.putObject("nested").put("inner", true);
        ObjectNode b = arr.addObject();
        b.put("id", 2);
        b.putObject("nested").put("inner", false);
        String out = Toon.encode(o);
        // Same keyset but values include nested object → expanded list form.
        assertEquals(
                "items[2]:\n  - id: 1\n    nested:\n      inner: true\n  - id: 2\n    nested:\n      inner: false",
                out);
    }

    @Test
    void mixedTypeArrayUsesExpandedList() {
        ObjectNode o = NF.objectNode();
        ArrayNode arr = o.putArray("items");
        arr.add(1);
        ObjectNode obj = arr.addObject();
        obj.put("a", 1);
        arr.add("text");
        assertEquals("items[3]:\n  - 1\n  - a: 1\n  - text", Toon.encode(o));
    }

    // --- quoting --------------------------------------------------------------

    @Test
    void stringWithCommaQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("text", "a,b");
        assertEquals("text: \"a,b\"", Toon.encode(o));
    }

    @Test
    void stringWithColonQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("url", "https://a:b/c");
        assertEquals("url: \"https://a:b/c\"", Toon.encode(o));
    }

    @Test
    void stringWithQuoteEscapedAndQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("q", "he said \"hi\"");
        assertEquals("q: \"he said \\\"hi\\\"\"", Toon.encode(o));
    }

    @Test
    void stringWithBackslashEscaped() {
        ObjectNode o = NF.objectNode();
        o.put("p", "C:\\Users");
        assertEquals("p: \"C:\\\\Users\"", Toon.encode(o));
    }

    @Test
    void stringWithNewlineEscaped() {
        ObjectNode o = NF.objectNode();
        o.put("multi", "line1\nline2");
        assertEquals("multi: \"line1\\nline2\"", Toon.encode(o));
    }

    @Test
    void emptyStringQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("e", "");
        assertEquals("e: \"\"", Toon.encode(o));
    }

    @Test
    void numericLikeStringsQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("a", "42");
        o.put("b", "-3.14");
        o.put("c", "1e-6");
        o.put("d", "05");
        assertEquals(
                "a: \"42\"\nb: \"-3.14\"\nc: \"1e-6\"\nd: \"05\"",
                Toon.encode(o));
    }

    @Test
    void literalLookalikesQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("a", "true");
        o.put("b", "false");
        o.put("c", "null");
        assertEquals(
                "a: \"true\"\nb: \"false\"\nc: \"null\"",
                Toon.encode(o));
    }

    @Test
    void leadingHyphenQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("a", "-foo");
        o.put("b", "-");
        assertEquals("a: \"-foo\"\nb: \"-\"", Toon.encode(o));
    }

    @Test
    void leadingTrailingWhitespaceQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("a", " leading");
        o.put("b", "trailing ");
        assertEquals("a: \" leading\"\nb: \"trailing \"", Toon.encode(o));
    }

    @Test
    void internalSpacesNotQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("a", "two words");
        assertEquals("a: two words", Toon.encode(o));
    }

    @Test
    void unicodeNotQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("a", "café");
        o.put("b", "👋 hi");
        assertEquals("a: café\nb: 👋 hi", Toon.encode(o));
    }

    // --- numbers -------------------------------------------------------------

    @Test
    void integerCanonicalForm() {
        ObjectNode o = NF.objectNode();
        o.put("a", 0);
        o.put("b", -42);
        o.put("c", 1_000_000_000L);
        assertEquals("a: 0\nb: -42\nc: 1000000000", Toon.encode(o));
    }

    @Test
    void doubleNoExponentNoTrailingZeros() {
        ObjectNode o = NF.objectNode();
        o.put("a", 1.5);
        o.put("b", 1.5000);
        o.put("c", 0.000001);
        o.put("d", 1_000_000.0);
        assertEquals(
                "a: 1.5\nb: 1.5\nc: 0.000001\nd: 1000000",
                Toon.encode(o));
    }

    @Test
    void negativeZeroNormalizedToZero() {
        ObjectNode o = NF.objectNode();
        o.put("a", -0.0);
        assertEquals("a: 0", Toon.encode(o));
    }

    @Test
    void nanAndInfinityBecomeNull() {
        ObjectNode o = NF.objectNode();
        o.put("a", Double.NaN);
        o.put("b", Double.POSITIVE_INFINITY);
        o.put("c", Double.NEGATIVE_INFINITY);
        assertEquals("a: null\nb: null\nc: null", Toon.encode(o));
    }

    // --- keys ----------------------------------------------------------------

    @Test
    void unquotedKeyEligibility() {
        ObjectNode o = NF.objectNode();
        o.put("normal_key", 1);
        o.put("with.dots", 2);
        o.put("_underscore", 3);
        assertEquals(
                "normal_key: 1\nwith.dots: 2\n_underscore: 3",
                Toon.encode(o));
    }

    @Test
    void keyWithHyphenQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("my-key", 1);
        assertEquals("\"my-key\": 1", Toon.encode(o));
    }

    @Test
    void keyStartingWithDigitQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("1st", "first");
        assertEquals("\"1st\": first", Toon.encode(o));
    }

    @Test
    void keyWithSpaceQuoted() {
        ObjectNode o = NF.objectNode();
        o.put("two words", 1);
        assertEquals("\"two words\": 1", Toon.encode(o));
    }

    // --- delimiter quoting in inline arrays -----------------------------------

    @Test
    void inlineArrayValueWithCommaIsQuoted() {
        ObjectNode o = NF.objectNode();
        ArrayNode arr = o.putArray("tags");
        arr.add("red");
        arr.add("blue,green");
        arr.add("yellow");
        assertEquals("tags[3]: red,\"blue,green\",yellow", Toon.encode(o));
    }

    @Test
    void tabularRowCellWithCommaQuoted() {
        ObjectNode o = NF.objectNode();
        ArrayNode arr = o.putArray("rows");
        ObjectNode a = arr.addObject();
        a.put("name", "Alice");
        a.put("note", "ok");
        ObjectNode b = arr.addObject();
        b.put("name", "Bob");
        b.put("note", "needs, work");
        assertEquals(
                "rows[2]{name,note}:\n  Alice,ok\n  Bob,\"needs, work\"",
                Toon.encode(o));
    }

    // --- nested arrays + complex shapes --------------------------------------

    @Test
    void arrayOfPrimitiveArrays() {
        ObjectNode o = NF.objectNode();
        ArrayNode pairs = o.putArray("pairs");
        ArrayNode a = pairs.addArray();
        a.add(1);
        a.add(2);
        ArrayNode b = pairs.addArray();
        b.add(3);
        b.add(4);
        assertEquals("pairs[2]:\n  - [2]: 1,2\n  - [2]: 3,4", Toon.encode(o));
    }

    @Test
    void emptyInnerArrayInListUsesBracketZero() {
        ObjectNode o = NF.objectNode();
        ArrayNode arr = o.putArray("outer");
        arr.addArray();  // empty inner array
        arr.addArray().add(1);
        assertEquals("outer[2]:\n  - [0]:\n  - [1]: 1", Toon.encode(o));
    }

    @Test
    void tabularArrayInsideListItemFirstFieldOnHyphenLine() {
        // §10 example: tabular array as first field of a list-item object.
        ObjectNode o = NF.objectNode();
        ArrayNode items = o.putArray("items");
        ObjectNode item = items.addObject();
        ArrayNode users = item.putArray("users");
        ObjectNode u1 = users.addObject();
        u1.put("id", 1);
        u1.put("name", "Ada");
        ObjectNode u2 = users.addObject();
        u2.put("id", 2);
        u2.put("name", "Bob");
        item.put("status", "active");
        // The spec example:
        //   items[1]:
        //     - users[2]{id,name}:
        //         1,Ada
        //         2,Bob
        //       status: active
        assertEquals(
                "items[1]:\n  - users[2]{id,name}:\n      1,Ada\n      2,Bob\n    status: active",
                Toon.encode(o));
    }

    @Test
    void rootTabularArray() {
        ArrayNode arr = NF.arrayNode();
        ObjectNode a = arr.addObject();
        a.put("id", 1);
        a.put("name", "Ada");
        ObjectNode b = arr.addObject();
        b.put("id", 2);
        b.put("name", "Bob");
        assertEquals("[2]{id,name}:\n  1,Ada\n  2,Bob", Toon.encode(arr));
    }

    @Test
    void rootPrimitiveArray() {
        ArrayNode arr = NF.arrayNode();
        arr.add(1);
        arr.add(2);
        arr.add(3);
        assertEquals("[3]: 1,2,3", Toon.encode(arr));
    }

    // --- realistic Minecraft-shaped payload ----------------------------------

    @Test
    void indentSizeBelowOneIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> Toon.encode(NF.objectNode(), 0));
    }

    @Test
    void customIndentSizeWidensSubsequentLevels() {
        ObjectNode o = NF.objectNode();
        o.putObject("nested").put("x", 1);
        // With indent=4 the nested line gets 4 spaces.
        assertEquals("nested:\n    x: 1", Toon.encode(o, 4));
    }

    @Test
    void rootMixedArrayUsesExpandedListForm() {
        // Exercises the root-mixed-array branch in writeRootArray.
        ArrayNode arr = NF.arrayNode();
        arr.add(1);
        ObjectNode o = arr.addObject();
        o.put("a", "x");
        arr.add("text");
        assertEquals("[3]:\n  - 1\n  - a: x\n  - text", Toon.encode(arr));
    }

    @Test
    void nullFieldValueRendersAsNullLiteral() {
        ObjectNode o = NF.objectNode();
        o.putNull("missing");
        o.put("present", 1);
        assertEquals("missing: null\npresent: 1", Toon.encode(o));
    }

    @Test
    void bigDecimalValueRendersCanonical() {
        ObjectNode o = NF.objectNode();
        o.put("a", new java.math.BigDecimal("1.5000"));
        o.put("b", new java.math.BigDecimal("100"));
        o.put("c", new java.math.BigDecimal("0.000001"));
        assertEquals("a: 1.5\nb: 100\nc: 0.000001", Toon.encode(o));
    }

    @Test
    void bigIntegerValueRendersAsPlainInteger() {
        ObjectNode o = NF.objectNode();
        o.put("a", new java.math.BigInteger("12345678901234567890"));
        assertEquals("a: 12345678901234567890", Toon.encode(o));
    }

    @Test
    void floatTypeRoundTripsThroughStringForm() {
        // Float 0.1 must NOT print as 0.10000000149011612 — we go via Float.toString().
        ObjectNode o = NF.objectNode();
        o.put("a", 0.1f);
        assertEquals("a: 0.1", Toon.encode(o));
    }

    @Test
    void inventoryShapedTabularPayload() throws Exception {
        // A typical inventory_get response — array of stacks where each stack is all
        // primitives. Should produce a single tabular block with ~half the tokens of
        // the equivalent JSON. Every "minecraft:foo" id contains a colon, so each is
        // quoted per §7.2.
        JsonNode parsed =
                M.readTree(
                        "{\"size\":3,\"slots\":["
                                + "{\"id\":\"minecraft:stone\",\"count\":64,\"damage\":0},"
                                + "{\"id\":\"minecraft:iron_sword\",\"count\":1,\"damage\":42},"
                                + "{\"id\":\"minecraft:air\",\"count\":0,\"damage\":0}]}");
        String out = Toon.encode(parsed);
        assertEquals(
                "size: 3\n"
                        + "slots[3]{id,count,damage}:\n"
                        + "  \"minecraft:stone\",64,0\n"
                        + "  \"minecraft:iron_sword\",1,42\n"
                        + "  \"minecraft:air\",0,0",
                out);
    }
}
