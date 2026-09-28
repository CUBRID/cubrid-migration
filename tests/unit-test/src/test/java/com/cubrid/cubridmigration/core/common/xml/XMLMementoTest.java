/*
 * Copyright (C) 2016 CUBRID Corporation.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * - Neither the name of the copyright holder nor the names of its contributors
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 *
 */
package com.cubrid.cubridmigration.core.common.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.transform.TransformerException;

@DisplayName("XMLMemento")
class XMLMementoTest {

    private static final String LEGACY_DOCUMENT =
            "<MySQL2CUBRID><DataTypeMapping>"
                + "<SourceDataType><type>bit</type><precision>1</precision><scale></scale>"
                + "</SourceDataType>"
                + "<TargetDataType><type>character</type><precision>1</precision><scale></scale>"
                + "</TargetDataType></DataTypeMapping><DataTypeMapping>"
                + "<SourceDataType><type>bit</type><precision>n</precision></SourceDataType>"
                + "<TargetDataType><type>bit</type><precision>n</precision><scale></scale>"
                + "</TargetDataType></DataTypeMapping></MySQL2CUBRID>";

    private static final String ATTRIBUTES =
            "<r text=\"abc\" empty=\"\" f1=\" 1.5 \" b1=\"TRUE\" i1=\"+1\"/>";

    private static XMLMemento load(String xml) {
        return (XMLMemento)
                XMLMemento.loadMemento(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String saved(XMLMemento memento) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        memento.save(out);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("getChild()")
    class GetChild {

        @Test
        @DisplayName("the first direct child with exactly that name is returned")
        void firstChildWithExactName_isReturned() {
            XMLMemento root = load("<root><item n=\"1\"/><Item n=\"case\"/><item n=\"2\"/></root>");

            assertThat(root.getChild("item").getString("n")).isEqualTo("1");
            assertThat(root.getChild("Item").getString("n")).isEqualTo("case");
        }

        @Test
        @DisplayName("an element further down is not a child")
        void grandchild_isNotFound() {
            assertThat(load("<root><a><b/></a></root>").getChild("b")).isNull();
        }

        @Test
        @DisplayName("no child with that name gives null")
        void noMatchingChild_returnsNull() {
            XMLMemento root = load(LEGACY_DOCUMENT);

            assertThat(root.getChild("none")).isNull();
            assertThat(load("<root/>").getChild("item")).isNull();
        }
    }

    @Nested
    @DisplayName("getChildren()")
    class GetChildren {

        @Test
        @DisplayName("the direct children with that name come back in document order")
        void matchingChildren_comeInDocumentOrder() {
            IXMLMemento[] children = load(LEGACY_DOCUMENT).getChildren("DataTypeMapping");

            assertThat(children)
                    .extracting(
                            child ->
                                    child.getChild("SourceDataType")
                                            .getChild("precision")
                                            .getTextData())
                    .containsExactly("1", "n");
        }

        @Test
        @DisplayName("elements further down are not children")
        void grandchildren_areLeftOut() {
            XMLMemento root =
                    load("<root><item n=\"1\"><item n=\"inner\"/></item><item n=\"2\"/></root>");

            assertThat(root.getChildren("item"))
                    .extracting(child -> child.getString("n"))
                    .containsExactly("1", "2");
        }

        @Test
        @DisplayName("no child with that name gives an empty array")
        void noMatchingChild_returnsEmptyArray() {
            assertThat(load(LEGACY_DOCUMENT).getChildren("none")).isEmpty();
            assertThat(load("<root/>").getChildren("item")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getFloat()")
    class GetFloat {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("any text Float accepts is read, spaces, suffix, exponent and hex included")
        @CsvSource({
            "' 1.5 ', 1.5",
            "1.5f, 1.5",
            "1e3, 1000",
            "0x1p3, 8",
            "NaN, NaN",
            "Infinity, Infinity"
        })
        void floatText_isRead(String text, Float expected) {
            assertThat(load("<r v=\"" + text + "\"/>").getFloat("v")).isEqualTo(expected);
        }

        @Test
        @DisplayName("a stored float is read back")
        void storedFloat_isReadBack() {
            XMLMemento memento = load(LEGACY_DOCUMENT);

            memento.putFloat("num", 1.23f);

            assertThat(memento.getFloat("num")).isEqualTo(1.23f);
        }

        @ParameterizedTest(name = "[{index}] {0} -> null")
        @DisplayName("a missing key or text that is not a float gives null")
        @ValueSource(strings = {"text", "empty", "noexistsattribute"})
        void notAFloat_returnsNull(String key) {
            assertThat(load(ATTRIBUTES).getFloat(key)).isNull();
        }
    }

    @Nested
    @DisplayName("getBoolean()")
    class GetBoolean {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> true")
        @DisplayName("true in any case is true")
        @ValueSource(strings = {"true", "TRUE", "True"})
        void trueInAnyCase_returnsTrue(String text) {
            assertThat(load("<r v=\"" + text + "\"/>").getBoolean("v")).isTrue();
        }

        @Test
        @DisplayName("a stored true is read back after a stored false")
        void storedTrue_isReadBack() {
            XMLMemento memento = load(LEGACY_DOCUMENT);

            memento.putBoolean("bool", Boolean.FALSE);
            memento.putBoolean("bool", Boolean.TRUE);

            assertThat(memento.getBoolean("bool")).isTrue();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> false")
        @DisplayName("any other text is false, surrounding spaces included")
        @ValueSource(strings = {"yes", "1", "", "true "})
        void otherText_returnsFalse(String text) {
            assertThat(load("<r v=\"" + text + "\"/>").getBoolean("v")).isFalse();
        }

        @Test
        @DisplayName("a missing key is false, not null")
        void missingKey_returnsFalse() {
            // DEFECT: IXMLMemento documents null for a missing key, but false comes back, so a
            // caller cannot tell a missing flag from a stored false
            // - see XMLMemento.getBoolean()
            assertThat(load(LEGACY_DOCUMENT).getBoolean("noexistsattribute")).isFalse();
        }
    }

    @Nested
    @DisplayName("getInteger()")
    class GetInteger {

        @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
        @DisplayName("text Integer.valueOf accepts is read, a leading sign included")
        @CsvSource({"42, 42", "+1, 1", "-7, -7"})
        void integerText_isRead(String text, int expected) {
            assertThat(load("<r v=\"" + text + "\"/>").getInteger("v")).isEqualTo(expected);
        }

        @Test
        @DisplayName("a stored integer is read back")
        void storedInteger_isReadBack() {
            XMLMemento memento = load(LEGACY_DOCUMENT);

            memento.putInteger("int", 1);

            assertThat(memento.getInteger("int")).isEqualTo(1);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> null")
        @DisplayName("spaces, decimals, overflow and other text give null")
        @ValueSource(strings = {" 1", "1.0", "2147483648", "abc", ""})
        void nonIntegerText_returnsNull(String text) {
            assertThat(load("<r v=\"" + text + "\"/>").getInteger("v")).isNull();
        }

        @Test
        @DisplayName("a missing key gives null")
        void missingKey_returnsNull() {
            assertThat(load(LEGACY_DOCUMENT).getInteger("noexistsattribute")).isNull();
        }
    }

    @Nested
    @DisplayName("getString()")
    class GetString {

        @Test
        @DisplayName("a stored string is read back")
        void storedString_isReadBack() {
            XMLMemento memento = load(LEGACY_DOCUMENT);

            memento.putString("str", "abc");

            assertThat(memento.getString("str")).isEqualTo("abc");
        }

        @Test
        @DisplayName("an empty value stays an empty string")
        void emptyValue_staysEmpty() {
            assertThat(load(ATTRIBUTES).getString("empty")).isEmpty();
        }

        @Test
        @DisplayName("a missing key gives null")
        void missingKey_returnsNull() {
            assertThat(load(LEGACY_DOCUMENT).getString("noexistsattribute")).isNull();
        }
    }

    @Nested
    @DisplayName("getTextData()")
    class GetTextData {

        @ParameterizedTest(name = "[{index}] {0} -> \"{1}\"")
        @DisplayName("the first text child is read, wherever it sits and CDATA included")
        @CsvSource({
            "<r>hello</r>, hello",
            "<r><c/>tail</r>, tail",
            "'<r><![CDATA[x<y]]></r>', x<y"
        })
        void firstTextChild_isRead(String xml, String expected) {
            assertThat(load(xml).getTextData()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] {0} -> null")
        @DisplayName("an element without text gives null")
        @ValueSource(strings = {"<r><c/></r>", "<r></r>"})
        void noTextChild_returnsNull(String xml) {
            assertThat(load(xml).getTextData()).isNull();
        }

        @Test
        @DisplayName("the indentation around child elements is read as the text")
        void indentation_isReadAsText() {
            // DEFECT: a text node holding only white space counts as the text, so an indented
            // element with nothing but children gives its line break and spaces instead of null
            // - see XMLMemento.getTextNode()
            assertThat(load("<r>\n  <c/>\n</r>").getTextData()).isEqualTo("\n  ");
        }
    }

    @Nested
    @DisplayName("getAttributeNames()")
    class GetAttributeNames {

        @Test
        @DisplayName("the names come in the DOM order, which sorts them by name")
        void names_comeSortedByName() {
            XMLMemento memento = load("<r/>");
            memento.putString("z", "1");
            memento.putString("a", "2");
            memento.putString("m", "3");

            assertThat(memento.getAttributeNames()).containsExactly("a", "m", "z");
        }

        @Test
        @DisplayName("changing the returned list leaves the element alone")
        void returnedList_isACopy() {
            XMLMemento memento = load("<r a=\"1\"/>");

            List<String> names = memento.getAttributeNames();
            names.clear();

            assertThat(memento.getAttributeNames()).containsExactly("a");
        }

        @Test
        @DisplayName("an element without attributes gives an empty list")
        void noAttributes_returnsEmptyList() {
            assertThat(load("<r/>").getAttributeNames()).isEmpty();
        }
    }

    @Nested
    @DisplayName("putString()")
    class PutString {

        @Test
        @DisplayName("a value replaces the one stored before")
        void value_replacesOldValue() {
            XMLMemento memento = load("<r k=\"old\"/>");

            memento.putString("k", "new");

            assertThat(memento.getString("k")).isEqualTo("new");
        }

        @Test
        @DisplayName("a null value adds no attribute")
        void nullValue_addsNothing() {
            XMLMemento memento = load("<r/>");

            memento.putString("k", null);

            assertThat(memento.getAttributeNames()).isEmpty();
        }

        @Test
        @DisplayName("a null value leaves the old value in place")
        void nullValue_leavesOldValue() {
            // DEFECT: null returns before touching the element, so the old value stays and a
            // later read gets it back instead of null
            // - see XMLMemento.putString()
            XMLMemento memento = load("<r k=\"old\"/>");

            memento.putString("k", null);

            assertThat(memento.getString("k")).isEqualTo("old");
        }
    }

    @Nested
    @DisplayName("putTextData()")
    class PutTextData {

        @Test
        @DisplayName("without a text child a new text goes in front of the first child")
        void noTextChild_insertsTextFirst() throws Exception {
            XMLMemento memento = load("<r><c/></r>");

            memento.putTextData("t");

            assertThat(memento.getTextData()).isEqualTo("t");
            assertThat(saved(memento)).containsSubsequence("<r>", "t", "<c/>", "</r>");
        }

        @Test
        @DisplayName("an element with no children at all gets the text")
        void emptyElement_getsText() {
            XMLMemento memento = load("<r/>");

            memento.putTextData("aaaaaaaaaa");

            assertThat(memento.getTextData()).isEqualTo("aaaaaaaaaa");
        }

        @Test
        @DisplayName("only the first text child is replaced")
        void firstTextChild_isReplaced() throws Exception {
            XMLMemento memento = load("<r>old<c/>two</r>");

            memento.putTextData("new");

            assertThat(memento.getTextData()).isEqualTo("new");
            assertThat(saved(memento))
                    .containsSubsequence("new", "<c/>", "two")
                    .doesNotContain("old");
        }
    }

    @Nested
    @DisplayName("loadMemento()")
    class LoadMemento {

        @Test
        @DisplayName("a saved file is read back")
        void savedFile_isReadBack(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("testxml.xml");
            try (OutputStream out = Files.newOutputStream(file)) {
                load(LEGACY_DOCUMENT).save(out);
            }

            IXMLMemento memento = XMLMemento.loadMemento(file.toString());

            assertThat(
                            memento.getChild("DataTypeMapping")
                                    .getChild("SourceDataType")
                                    .getChild("type")
                                    .getTextData())
                    .isEqualTo("bit");
        }

        @Test
        @DisplayName("an XML declaration in front of the root is fine")
        void declarationBeforeRoot_isAccepted() {
            assertThat(load("<?xml version=\"1.0\"?>\n<root/>")).isNotNull();
        }

        @Test
        @DisplayName("the stream is closed after reading, whether or not it parses")
        void stream_isClosedAfterReading() {
            TrackingStream parsed = new TrackingStream("<root/>");
            TrackingStream broken = new TrackingStream("<root>");

            XMLMemento.loadMemento(parsed);
            XMLMemento.loadMemento(broken);

            assertThat(parsed.closed).isTrue();
            assertThat(broken.closed).isTrue();
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("a comment, DOCTYPE or processing instruction before the root gives null")
        @ValueSource(strings = {"<!-- c --><root/>", "<!DOCTYPE root><root/>", "<?pi x?><root/>"})
        void nodeBeforeRoot_returnsNull(String xml) {
            // DEFECT: the root is taken from getFirstChild(), which is the node in front of the
            // root element, instead of getDocumentElement(), so a valid document reads as null
            // - see XMLMemento.getRoot()
            assertThat(load(xml)).isNull();
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @DisplayName("text that does not parse gives null")
        @ValueSource(strings = {"<root>", ""})
        void unparsableText_returnsNull(String xml) {
            assertThat(load(xml)).isNull();
        }

        @Test
        @DisplayName("a file that does not parse gives null")
        void unparsableFile_returnsNull(@TempDir Path dir) throws Exception {
            Path file =
                    Files.write(
                            dir.resolve("broken.xml"), "<root>".getBytes(StandardCharsets.UTF_8));

            assertThat(XMLMemento.loadMemento(file.toString())).isNull();
        }

        @Test
        @DisplayName("a missing file is rejected")
        void missingFile_throwsFileNotFoundException(@TempDir Path dir) {
            assertThatThrownBy(() -> XMLMemento.loadMemento(dir.resolve("none.xml").toString()))
                    .isInstanceOf(FileNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("the document is written as UTF-8 XML indented by two spaces")
        void document_isWrittenIndentedByTwoSpaces() throws Exception {
            XMLMemento root = XMLMemento.createWriteRoot("root");
            IXMLMemento child = root.createChild("child");
            child.putString("a", "1");
            child.createChild("leaf");

            assertThat(saved(root))
                    .startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"")
                    .containsSubsequence(
                            "<root>",
                            "\n  <child a=\"1\">",
                            "\n    <leaf/>",
                            "\n  </child>",
                            "</root>");
        }

        @Test
        @DisplayName("a child memento writes the whole document")
        void childMemento_writesWholeDocument() throws Exception {
            XMLMemento root = XMLMemento.createWriteRoot("root");
            XMLMemento child = (XMLMemento) root.createChild("child");

            assertThat(saved(child)).containsSubsequence("<root>", "<child/>", "</root>");
        }

        @Test
        @DisplayName("text outside ASCII is encoded as UTF-8")
        void nonAsciiText_isEncodedAsUtf8() throws Exception {
            XMLMemento root = XMLMemento.createWriteRoot("root");
            root.putString("a", "\uAC00");

            assertThat(saved(root)).contains("<root a=\"\uAC00\"/>");
        }

        @Test
        @DisplayName("a failure while writing is rethrown as an IOException with its cause")
        void writeFailure_isWrappedInIOException() throws Exception {
            XMLMemento root = XMLMemento.createWriteRoot("root");
            OutputStream failing =
                    new OutputStream() {
                        @Override
                        public void write(int b) throws IOException {
                            throw new IOException("disk full");
                        }
                    };

            assertThatThrownBy(() -> root.save(failing))
                    .isInstanceOf(IOException.class)
                    .hasCauseInstanceOf(TransformerException.class);
        }
    }

    private static final class TrackingStream extends ByteArrayInputStream {
        private boolean closed;

        TrackingStream(String xml) {
            super(xml.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
