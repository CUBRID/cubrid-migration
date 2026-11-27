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
package com.cubrid.cubridmigration.core.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@DisplayName("CUBRIDIOUtils")
class CUBRIDIOUtilsTest {

    private static final String LINE_SEPARATOR = System.lineSeparator();

    private static Path write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static List<String> entryNames(Path zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        Collections.sort(names);
        return names;
    }

    private static Path sampleZip(Path dir) throws IOException {
        Path zip = dir.resolve("sample.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            out.putNextEntry(new ZipEntry("d/"));
            out.putNextEntry(new ZipEntry("d/x.txt"));
            out.write("X".getBytes(StandardCharsets.UTF_8));
            out.putNextEntry(new ZipEntry("top.txt"));
            out.write("T".getBytes(StandardCharsets.UTF_8));
            out.putNextEntry(new ZipEntry("e/"));
        }
        return zip;
    }

    private static Path workbook(Path file, Consumer<Sheet> filler) throws IOException {
        try (Workbook workbook = new HSSFWorkbook();
                OutputStream out = new FileOutputStream(file.toFile())) {
            filler.accept(workbook.createSheet("s"));
            workbook.write(out);
        }
        return file;
    }

    private static List<List<String>> sheetRows(Path file, int sheetIndex) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            List<List<String>> rows = new ArrayList<>();
            for (Row row : workbook.getSheetAt(sheetIndex)) {
                List<String> cells = new ArrayList<>();
                for (Cell cell : row) {
                    cells.add(cell.getStringCellValue());
                }
                rows.add(cells);
            }
            return rows;
        }
    }

    @Nested
    @DisplayName("clearFileOrDir()")
    class ClearFileOrDir {

        @Test
        @DisplayName("a directory is removed together with everything inside it")
        void directory_isRemovedWithContents(@TempDir Path dir) throws Exception {
            Path tree = dir.resolve("tree");
            write(tree.resolve("a/b.txt"), "b");

            CUBRIDIOUtils.clearFileOrDir(tree.toFile());

            assertThat(tree).doesNotExist();
        }

        @Test
        @DisplayName("a file is removed")
        void file_isRemoved(@TempDir Path dir) throws Exception {
            Path file = write(dir.resolve("gone.txt"), "g");

            CUBRIDIOUtils.clearFileOrDir(file.toFile());

            assertThat(file).doesNotExist();
        }

        @Test
        @DisplayName("a symbolic link to a directory is followed and the linked files are deleted")
        void symbolicLinkToDirectory_deletesLinkedFiles(@TempDir Path dir) throws Exception {
            // DEFECT: listFiles() on a link to a directory lists the target's entries, and each is
            // deleted before the link itself, so clearing a folder also empties any folder it
            // links to
            // - see CUBRIDIOUtils.clearFileOrDir()
            Path outside = Files.createDirectories(dir.resolve("outside"));
            Path kept = write(outside.resolve("keep.txt"), "precious");
            Path cleared = Files.createDirectories(dir.resolve("cleared"));
            assumeTrue(link(cleared.resolve("link"), outside), "symbolic links are unavailable");

            CUBRIDIOUtils.clearFileOrDir(cleared.toFile());

            assertThat(cleared).doesNotExist();
            assertThat(outside).isDirectory();
            assertThat(kept).doesNotExist();
        }

        @Test
        @DisplayName("a missing path is ignored")
        void missingPath_isIgnored(@TempDir Path dir) {
            CUBRIDIOUtils.clearFileOrDir(dir.resolve("none").toFile());

            assertThat(dir).isEmptyDirectory();
        }

        private boolean link(Path link, Path target) {
            try {
                Files.createSymbolicLink(link, target);
                return true;
            } catch (IOException | UnsupportedOperationException e) {
                return false;
            }
        }
    }

    @Nested
    @DisplayName("copyFile()")
    class CopyFile {

        @Test
        @DisplayName("the target is replaced by the source's bytes")
        void source_replacesTarget(@TempDir Path dir) throws Exception {
            Path source = write(dir.resolve("a.txt"), "hello");
            Path target = write(dir.resolve("b.txt"), "0123456789");

            CUBRIDIOUtils.copyFile(source.toFile(), target.toFile());

            assertThat(read(target)).isEqualTo("hello");
        }

        @Test
        @DisplayName("copying a file onto itself empties it")
        void sameFile_isEmptied(@TempDir Path dir) throws Exception {
            // DEFECT: the target stream is opened, truncating the file, before the source is
            // read, so a copy onto the same path loses its content
            // - see CUBRIDIOUtils.copyFile()
            Path file = write(dir.resolve("a.txt"), "hello");

            CUBRIDIOUtils.copyFile(file.toFile(), file.toFile());

            assertThat(file).isEmptyFile();
        }

        @Test
        @DisplayName("a missing source creates no target")
        void missingSource_createsNoTarget(@TempDir Path dir) throws Exception {
            Path target = dir.resolve("b.txt");

            CUBRIDIOUtils.copyFile(dir.resolve("none").toFile(), target.toFile());

            assertThat(target).doesNotExist();
        }

        @Test
        @DisplayName("a null argument is ignored")
        void nullArgument_isIgnored(@TempDir Path dir) throws Exception {
            Path target = write(dir.resolve("b.txt"), "kept");

            CUBRIDIOUtils.copyFile(null, target.toFile());

            assertThat(read(target)).isEqualTo("kept");
        }
    }

    @Nested
    @DisplayName("copyFolder()")
    class CopyFolder {

        @Test
        @DisplayName("a directory is copied with everything below it")
        void directory_isCopiedRecursively(@TempDir Path dir) throws Exception {
            Path source = dir.resolve("tree");
            write(source.resolve("x.txt"), "x");
            write(source.resolve("sub/y.txt"), "y");
            Path target = dir.resolve("copy");

            CUBRIDIOUtils.copyFolder(source.toFile(), target.toFile());

            assertThat(read(target.resolve("x.txt"))).isEqualTo("x");
            assertThat(read(target.resolve("sub/y.txt"))).isEqualTo("y");
        }

        @Test
        @DisplayName("a file source is copied to the target file")
        void fileSource_isCopiedAsFile(@TempDir Path dir) throws Exception {
            Path source = write(dir.resolve("x.txt"), "x");
            Path target = dir.resolve("single.txt");

            CUBRIDIOUtils.copyFolder(source.toFile(), target.toFile());

            assertThat(read(target)).isEqualTo("x");
        }

        @Test
        @DisplayName("a destination inside the source is copied into itself again and again")
        void destinationInsideSource_isNestedIntoItself(@TempDir Path dir) throws Exception {
            // DEFECT: the destination is created before the source is listed, so it is copied
            // again inside itself, one level deeper each time, until the path gets too long
            // - see CUBRIDIOUtils.copyFolder()
            Path source = Files.createDirectories(dir.resolve("src"));
            // a long name reaches the path length limit within a few dozen levels
            String name = "copy".repeat(15);

            CUBRIDIOUtils.copyFolder(source.toFile(), source.resolve(name).toFile());

            assertThat(source.resolve(String.join("/", Collections.nCopies(3, name))))
                    .isDirectory();
        }

        @Test
        @DisplayName("a missing source creates nothing")
        void missingSource_createsNothing(@TempDir Path dir) throws Exception {
            Path target = dir.resolve("copy");

            CUBRIDIOUtils.copyFolder(dir.resolve("none").toFile(), target.toFile());

            assertThat(target).doesNotExist();
        }
    }

    @Nested
    @DisplayName("extractFromZip()")
    class ExtractFromZip {

        @Test
        @DisplayName("the entry with the given name is extracted and its path returned")
        void matchingEntry_isExtracted(@TempDir Path dir) throws Exception {
            Path output = dir.resolve("out");

            String path =
                    CUBRIDIOUtils.extractFromZip(
                            sampleZip(dir).toString(), "top.txt", output.toString());

            assertThat(path).isEqualTo(output.resolve("top.txt").toString());
            assertThat(read(output.resolve("top.txt"))).isEqualTo("T");
        }

        @Test
        @DisplayName("an entry whose path ends with the name is extracted under its zip path")
        void entryEndingWithName_isExtractedUnderZipPath(@TempDir Path dir) throws Exception {
            Path output = dir.resolve("out");

            String path =
                    CUBRIDIOUtils.extractFromZip(
                            sampleZip(dir).toString(), "x.txt", output.toString());

            assertThat(path).isEqualTo(output.resolve("d/x.txt").toString());
        }

        @Test
        @DisplayName("an existing file at the target path is replaced")
        void existingFile_isReplaced(@TempDir Path dir) throws Exception {
            Path output = dir.resolve("out");
            write(output.resolve("top.txt"), "OLDER");

            CUBRIDIOUtils.extractFromZip(sampleZip(dir).toString(), "top.txt", output.toString());

            assertThat(read(output.resolve("top.txt"))).isEqualTo("T");
        }

        @Test
        @DisplayName("a target path that cannot be deleted is returned without extracting")
        void undeletableTarget_isReturnedWithoutExtracting(@TempDir Path dir) throws Exception {
            Path output = dir.resolve("out");
            Path inside = write(output.resolve("top.txt/inside.txt"), "kept");

            String path =
                    CUBRIDIOUtils.extractFromZip(
                            sampleZip(dir).toString(), "top.txt", output.toString());

            assertThat(path).isEqualTo(output.resolve("top.txt").toString());
            assertThat(read(inside)).isEqualTo("kept");
        }

        @ParameterizedTest(name = "[{index}] \"{0}\" -> null")
        @DisplayName("no file entry with that name gives null, and directory entries are skipped")
        @ValueSource(strings = {"nothing.txt", "d/"})
        void noMatchingFile_returnsNull(String name, @TempDir Path dir) throws Exception {
            assertThat(
                            CUBRIDIOUtils.extractFromZip(
                                    sampleZip(dir).toString(), name, dir.resolve("out").toString()))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("getFileInputStream()")
    class GetFileInputStream {

        @Test
        @DisplayName("a local file is opened as a buffered stream")
        void localFile_opensBufferedStream(@TempDir Path dir) throws Exception {
            Path file = write(dir.resolve("a.txt"), "hello");

            try (InputStream in = CUBRIDIOUtils.getFileInputStream(file.toString())) {
                assertThat(in).isInstanceOf(BufferedInputStream.class).hasContent("hello");
            }
        }

        @Test
        @DisplayName("a missing file cannot be opened")
        void missingFile_throwsFileNotFoundException(@TempDir Path dir) {
            assertThatThrownBy(
                            () -> CUBRIDIOUtils.getFileInputStream(dir.resolve("none").toString()))
                    .isInstanceOf(FileNotFoundException.class);
        }

        @Test
        @DisplayName("a blank name is rejected")
        void blankName_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> CUBRIDIOUtils.getFileInputStream("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Can't create input stream: file name can't be empty.");
        }
    }

    @Nested
    @DisplayName("getFileLength()")
    class GetFileLength {

        @Test
        @DisplayName("a local file gives its length in bytes")
        void localFile_returnsLength(@TempDir Path dir) throws Exception {
            assertThat(CUBRIDIOUtils.getFileLength(write(dir.resolve("a.txt"), "hello").toString()))
                    .isEqualTo(5);
        }

        @Test
        @DisplayName("a missing file gives 0")
        void missingFile_returnsZero(@TempDir Path dir) {
            assertThat(CUBRIDIOUtils.getFileLength(dir.resolve("none").toString())).isZero();
        }

        @Test
        @DisplayName("a blank name is rejected with the input stream message")
        void blankName_throwsWithInputStreamMessage() {
            // DEFECT: the check and its message were copied from getFileInputStream(), so asking
            // for a length reports that an input stream could not be created
            // - see CUBRIDIOUtils.getFileLength()
            assertThatThrownBy(() -> CUBRIDIOUtils.getFileLength(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Can't create input stream: file name can't be empty.");
        }
    }

    @Nested
    @DisplayName("getOSMatches()")
    class GetOSMatches {

        @Test
        @DisplayName("any prefix of the os.name read at class load matches")
        void prefixOfOsName_returnsTrue() throws Exception {
            String osName = System.getProperty("os.name");

            assertThat(getOSMatches(osName)).isTrue();
            assertThat(getOSMatches(osName.substring(0, 1))).isTrue();
        }

        @Test
        @DisplayName("another prefix does not match")
        void otherPrefix_returnsFalse() throws Exception {
            assertThat(getOSMatches("NoSuchOS")).isFalse();
        }

        @Test
        @DisplayName("IS_OS_WINDOWS follows the os.name prefix")
        void windowsFlag_followsOsName() {
            assertThat(CUBRIDIOUtils.IS_OS_WINDOWS)
                    .isEqualTo(System.getProperty("os.name").startsWith("Windows"));
        }

        private boolean getOSMatches(String prefix) throws Exception {
            Method method = CUBRIDIOUtils.class.getDeclaredMethod("getOSMatches", String.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(null, prefix);
        }
    }

    @Nested
    @DisplayName("loadObjectFromXML()")
    class LoadObjectFromXML {

        @Test
        @DisplayName("the first object an XMLEncoder wrote is read back")
        void encodedObject_isReadBack(@TempDir Path dir) throws Exception {
            Path xml = dir.resolve("object.xml");
            try (XMLEncoder encoder = new XMLEncoder(new FileOutputStream(xml.toFile()))) {
                encoder.writeObject(new ArrayList<>(Arrays.asList("a", "b")));
            }

            assertThat(CUBRIDIOUtils.loadObjectFromXML(xml.toString()))
                    .isEqualTo(Arrays.asList("a", "b"));
        }

        @Test
        @DisplayName("a document holding no object fails on the first read")
        void documentWithoutObject_throwsArrayIndexOutOfBoundsException(@TempDir Path dir)
                throws Exception {
            Path xml =
                    write(
                            dir.resolve("empty.xml"),
                            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><java version=\"1.8\""
                                    + " class=\"java.beans.XMLDecoder\"></java>");

            assertThatThrownBy(() -> CUBRIDIOUtils.loadObjectFromXML(xml.toString()))
                    .isInstanceOf(ArrayIndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("a missing file is rethrown as a RuntimeException")
        void missingFile_throwsRuntimeException(@TempDir Path dir) {
            assertThatThrownBy(
                            () ->
                                    CUBRIDIOUtils.loadObjectFromXML(
                                            dir.resolve("none.xml").toString()))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(FileNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("mergeFile()")
    class MergeFile {

        @Test
        @DisplayName("the source bytes are appended to the target")
        void source_isAppendedToTarget(@TempDir Path dir) throws Exception {
            Path source = write(dir.resolve("a.txt"), "hello");
            Path target = write(dir.resolve("b.txt"), "old-");

            CUBRIDIOUtils.mergeFile(source.toString(), target.toString());

            assertThat(read(target)).isEqualTo("old-hello");
        }

        @Test
        @DisplayName("a missing target is created first")
        void missingTarget_isCreated(@TempDir Path dir) throws Exception {
            Path source = write(dir.resolve("a.txt"), "hello");
            Path target = dir.resolve("new/b.txt");

            CUBRIDIOUtils.mergeFile(source.toString(), target.toString());

            assertThat(read(target)).isEqualTo("hello");
        }

        @Test
        @DisplayName("a file is not merged into itself")
        void sameFile_isLeftAlone(@TempDir Path dir) throws Exception {
            Path file = write(dir.resolve("a.txt"), "hello");

            CUBRIDIOUtils.mergeFile(file.toString(), dir.resolve("./a.txt").toString());

            assertThat(read(file)).isEqualTo("hello");
        }

        @Test
        @DisplayName("a missing source fails after the empty target has been created")
        void missingSource_leavesEmptyTarget(@TempDir Path dir) {
            // DEFECT: the target is created and opened before the source, so a missing source
            // fails only after leaving an empty target file behind
            // - see CUBRIDIOUtils.mergeFile()
            Path target = dir.resolve("b.txt");

            assertThatThrownBy(
                            () ->
                                    CUBRIDIOUtils.mergeFile(
                                            dir.resolve("none").toString(), target.toString()))
                    .isInstanceOf(FileNotFoundException.class);
            assertThat(target).isEmptyFile();
        }
    }

    @Nested
    @DisplayName("readDataFromExcel()")
    class ReadDataFromExcel {

        @Test
        @DisplayName("the first sheet is read row by row as strings")
        void firstSheet_isReadRowByRow(@TempDir Path dir) throws Exception {
            Path file =
                    workbook(
                            dir.resolve("t.xls"),
                            sheet -> {
                                fill(sheet.createRow(0), "c1", "c2");
                                fill(sheet.createRow(1), "a", "b");
                            });

            assertThat(CUBRIDIOUtils.readDataFromExcel(file.toString()))
                    .containsExactly(new String[] {"c1", "c2"}, new String[] {"a", "b"});
        }

        @Test
        @DisplayName("a cell that is not text reads as an empty string")
        void nonTextCell_readsAsEmpty(@TempDir Path dir) throws Exception {
            Path file =
                    workbook(
                            dir.resolve("t.xls"),
                            sheet -> {
                                Row row = sheet.createRow(0);
                                row.createCell(0).setCellValue("text");
                                row.createCell(1).setCellValue(3.5);
                                row.createCell(2).setCellValue(true);
                            });

            assertThat(CUBRIDIOUtils.readDataFromExcel(file.toString()))
                    .containsExactly(new String[] {"text", "", ""});
        }

        @Test
        @DisplayName("a gap in a row fails with an NPE")
        void missingCell_throwsNullPointerException(@TempDir Path dir) throws Exception {
            // DEFECT: getCell() returns null for a cell that was never written, and the result
            // is used unchecked, so a row with a gap cannot be read
            // - see CUBRIDIOUtils.readDataFromExcel()
            Path file =
                    workbook(
                            dir.resolve("t.xls"),
                            sheet -> {
                                Row row = sheet.createRow(0);
                                row.createCell(0).setCellValue("a");
                                row.createCell(2).setCellValue("c");
                            });

            assertThatThrownBy(() -> CUBRIDIOUtils.readDataFromExcel(file.toString()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("an empty row between rows fails with an NPE")
        void missingRow_throwsNullPointerException(@TempDir Path dir) throws Exception {
            // DEFECT: getRow() returns null for a row that was never written, and the result is
            // used unchecked, so a sheet with an empty row cannot be read
            // - see CUBRIDIOUtils.readDataFromExcel()
            Path file =
                    workbook(
                            dir.resolve("t.xls"),
                            sheet -> {
                                sheet.createRow(0).createCell(0).setCellValue("a");
                                sheet.createRow(2).createCell(0).setCellValue("c");
                            });

            assertThatThrownBy(() -> CUBRIDIOUtils.readDataFromExcel(file.toString()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("a missing file is rethrown as a RuntimeException")
        void missingFile_throwsRuntimeException(@TempDir Path dir) {
            assertThatThrownBy(
                            () ->
                                    CUBRIDIOUtils.readDataFromExcel(
                                            dir.resolve("none.xls").toString()))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(FileNotFoundException.class);
        }

        private void fill(Row row, String... values) {
            for (int i = 0; i < values.length; i++) {
                row.createCell(i).setCellValue(values[i]);
            }
        }
    }

    @Nested
    @DisplayName("readFile()")
    class ReadFile {

        @Test
        @DisplayName("CRLF and CR line ends become line feeds")
        void crlfAndCr_becomeLineFeeds() throws Exception {
            assertThat(CUBRIDIOUtils.readFile(new StringReader("a\r\nb\rc\n")))
                    .isEqualTo("a\nb\nc\n");
        }

        @Test
        @DisplayName("a last line without a line end gets a line feed")
        void lastLineWithoutLineEnd_getsLineFeed() throws Exception {
            assertThat(CUBRIDIOUtils.readFile(new StringReader("a\nb"))).isEqualTo("a\nb\n");
        }

        @Test
        @DisplayName("an empty reader gives an empty string")
        void emptyReader_returnsEmptyString() throws Exception {
            assertThat(CUBRIDIOUtils.readFile(new StringReader(""))).isEmpty();
        }

        @Test
        @DisplayName("the reader is closed afterwards")
        void reader_isClosed() throws Exception {
            StringReader reader = new StringReader("x");

            CUBRIDIOUtils.readFile(reader);

            assertThatThrownBy(reader::read).isInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("saveTable2Excel()")
    class SaveTable2Excel {

        @Test
        @DisplayName("each tab becomes a sheet with its header row and data rows")
        void eachTab_becomesSheet(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("out/test.xls");

            CUBRIDIOUtils.saveTable2Excel(
                    new String[] {"tab1", "tab2"},
                    Arrays.asList(new String[] {"c11", "c12", "c13"}, new String[] {"c21", "c22"}),
                    Arrays.asList(
                            Arrays.asList(
                                    new String[] {"v11", "v12", "v13"},
                                    new String[] {"w11", "w12", "w13"}),
                            Collections.singletonList(new String[] {"v21", "v22"})),
                    file.toString());

            try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
                assertThat(workbook.getSheetName(0)).isEqualTo("tab1");
                assertThat(workbook.getSheetName(1)).isEqualTo("tab2");
            }
            assertThat(sheetRows(file, 0))
                    .containsExactly(
                            Arrays.asList("c11", "c12", "c13"),
                            Arrays.asList("v11", "v12", "v13"),
                            Arrays.asList("w11", "w12", "w13"));
            assertThat(sheetRows(file, 1))
                    .containsExactly(Arrays.asList("c21", "c22"), Arrays.asList("v21", "v22"));
        }

        @Test
        @DisplayName("an existing file is replaced")
        void existingFile_isReplaced(@TempDir Path dir) throws Exception {
            Path file = write(dir.resolve("t.xls"), "not a workbook");

            CUBRIDIOUtils.saveTable2Excel(
                    new String[] {"s"},
                    Collections.singletonList(new String[] {"c"}),
                    Collections.singletonList(Collections.<String[]>emptyList()),
                    file.toString());

            assertThat(sheetRows(file, 0)).containsExactly(Collections.singletonList("c"));
        }

        @Test
        @DisplayName("a data row shorter than the header fails")
        void shortDataRow_throwsRuntimeException(@TempDir Path dir) {
            // DEFECT: writeTvSheet() walks every row by the header's length, so a shorter data
            // row indexes past its end
            // - see CUBRIDIOUtils.writeTvSheet()
            assertThatThrownBy(
                            () ->
                                    CUBRIDIOUtils.saveTable2Excel(
                                            new String[] {"s"},
                                            Collections.singletonList(new String[] {"c1", "c2"}),
                                            Collections.singletonList(
                                                    Collections.singletonList(
                                                            new String[] {"only"})),
                                            dir.resolve("t.xls").toString()))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(ArrayIndexOutOfBoundsException.class);
        }

        @Test
        @DisplayName("a data row longer than the header loses its extra values")
        void longDataRow_losesExtraValues(@TempDir Path dir) throws Exception {
            // DEFECT: writeTvSheet() writes only as many cells as the header has, so the values
            // beyond it are dropped without notice
            // - see CUBRIDIOUtils.writeTvSheet()
            Path file = dir.resolve("t.xls");

            CUBRIDIOUtils.saveTable2Excel(
                    new String[] {"s"},
                    Collections.singletonList(new String[] {"c1"}),
                    Collections.singletonList(Collections.singletonList(new String[] {"v1", "v2"})),
                    file.toString());

            assertThat(sheetRows(file, 0))
                    .containsExactly(
                            Collections.singletonList("c1"), Collections.singletonList("v1"));
        }

        @Test
        @DisplayName("no tabs means no file")
        void noTabs_writesNothing(@TempDir Path dir) {
            Path none = dir.resolve("none.xls");
            Path empty = dir.resolve("empty.xls");

            CUBRIDIOUtils.saveTable2Excel(null, null, null, none.toString());
            CUBRIDIOUtils.saveTable2Excel(new String[0], null, null, empty.toString());

            assertThat(none).doesNotExist();
            assertThat(empty).doesNotExist();
        }
    }

    @Nested
    @DisplayName("unzip()")
    class Unzip {

        @Test
        @DisplayName("every entry is extracted and the extracted files are listed")
        void entries_areExtractedAndFilesListed(@TempDir Path dir) throws Exception {
            Path output = dir.resolve("out");

            List<File> files = CUBRIDIOUtils.unzip(sampleZip(dir).toString(), output.toString());

            assertThat(files)
                    .containsExactly(
                            output.resolve("d/x.txt").toFile(), output.resolve("top.txt").toFile());
            assertThat(output.resolve("e")).isDirectory();
            assertThat(read(output.resolve("d/x.txt"))).isEqualTo("X");
        }

        @Test
        @DisplayName("an existing file is overwritten")
        void existingFile_isOverwritten(@TempDir Path dir) throws Exception {
            Path output = dir.resolve("out");
            write(output.resolve("top.txt"), "OLD-CONTENT");

            CUBRIDIOUtils.unzip(sampleZip(dir).toString(), output.toString());

            assertThat(read(output.resolve("top.txt"))).isEqualTo("T");
        }

        @Test
        @DisplayName("an output directory that cannot be created fails")
        void uncreatableOutput_throwsIOException(@TempDir Path dir) throws Exception {
            Path blocker = write(dir.resolve("blocker"), "a file, not a directory");

            assertThatThrownBy(
                            () ->
                                    CUBRIDIOUtils.unzip(
                                            sampleZip(dir).toString(),
                                            blocker.resolve("sub").toString()))
                    .isInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("writeLines()")
    class WriteLines {

        @Test
        @DisplayName("without a charset the JVM's file.encoding is used")
        void nullCharset_usesFileEncoding() throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            CUBRIDIOUtils.writeLines(out, new String[] {"a", "\uAC00"}, null);

            assertThat(out.toByteArray())
                    .isEqualTo(
                            ("a" + LINE_SEPARATOR + "\uAC00" + LINE_SEPARATOR)
                                    .getBytes(CUBRIDIOUtils.DEFAULT_CHARSET));
        }

        @Test
        @DisplayName("a named charset is used for the bytes")
        void namedCharset_isUsed() throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            CUBRIDIOUtils.writeLines(out, new String[] {"a"}, "UTF-16BE");

            assertThat(out.toByteArray())
                    .isEqualTo(("a" + LINE_SEPARATOR).getBytes(StandardCharsets.UTF_16BE));
        }

        @Test
        @DisplayName("an unknown charset is rejected")
        void unknownCharset_throwsUnsupportedEncodingException() {
            assertThatThrownBy(
                            () ->
                                    CUBRIDIOUtils.writeLines(
                                            new ByteArrayOutputStream(),
                                            new String[] {"a"},
                                            "NO-SUCH"))
                    .isInstanceOf(UnsupportedEncodingException.class);
        }

        @Test
        @DisplayName("each line is followed by the line separator, the last included")
        void eachLine_getsLineSeparator() throws Exception {
            StringWriter writer = new StringWriter();

            CUBRIDIOUtils.writeLines(writer, new String[] {"a", "b"});

            assertThat(writer).hasToString("a" + LINE_SEPARATOR + "b" + LINE_SEPARATOR);
        }

        @Test
        @DisplayName("the writer is flushed but left open")
        void writer_isFlushedNotClosed() throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

            CUBRIDIOUtils.writeLines(writer, new String[] {"a"});
            String flushed = out.toString(StandardCharsets.UTF_8);
            writer.write("b");
            writer.flush();

            assertThat(flushed).isEqualTo("a" + LINE_SEPARATOR);
            assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("a" + LINE_SEPARATOR + "b");
        }
    }

    @Nested
    @DisplayName("writeToFile()")
    class WriteToFile {

        @Test
        @DisplayName("the reader's text is written in the given charset and the reader closed")
        void reader_isWrittenAndClosed(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("r.txt");
            StringReader reader = new StringReader("\uAC00b");

            CUBRIDIOUtils.writeToFile(reader, file.toString(), "UTF-16BE");

            assertThat(Files.readAllBytes(file)).containsExactly(-84, 0, 0, 98);
            assertThatThrownBy(reader::read).isInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("an existing file is overwritten")
        void existingFile_isOverwritten(@TempDir Path dir) throws Exception {
            Path file = write(dir.resolve("test.txt"), "0123456789abcdef");

            CUBRIDIOUtils.writeToFile(
                    new StringReader("testtesttest"),
                    file.toString(),
                    CUBRIDIOUtils.DEFAULT_CHARSET);

            assertThat(read(file)).isEqualTo("testtesttest");
        }

        @Test
        @DisplayName("an unknown charset is rejected")
        void unknownCharset_throwsUnsupportedEncodingException(@TempDir Path dir) {
            assertThatThrownBy(
                            () ->
                                    CUBRIDIOUtils.writeToFile(
                                            new StringReader("ab"),
                                            dir.resolve("bad.txt").toString(),
                                            "NO-SUCH"))
                    .isInstanceOf(UnsupportedEncodingException.class);
        }

        @Test
        @DisplayName("the stream's bytes are written and the stream closed")
        void inputStream_isWrittenAndClosed(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("s.bin");
            InputStream in =
                    Files.newInputStream(Files.write(dir.resolve("in.bin"), new byte[] {1, 2, 3}));

            CUBRIDIOUtils.writeToFile(file.toString(), in);

            assertThat(Files.readAllBytes(file)).containsExactly(1, 2, 3);
            assertThatThrownBy(in::read).isInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("zip()")
    class Zip {

        @Test
        @DisplayName("a directory's children and a file go in at the root of the archive")
        void inputs_areZippedFromRoot(@TempDir Path dir) throws Exception {
            write(dir.resolve("in/a.txt"), "A");
            write(dir.resolve("in/sub/b.txt"), "B");
            Path file = write(dir.resolve("f1.txt"), "F1");
            Path zip = dir.resolve("one.zip");

            CUBRIDIOUtils.zip(
                    zip.toString(),
                    new String[] {dir.resolve("in").toString(), file.toString()},
                    false);

            assertThat(entryNames(zip)).containsExactly("a.txt", "f1.txt", "sub/", "sub/b.txt");
        }

        @Test
        @DisplayName("zipping into an existing archive hides what it held")
        void existingArchive_isHiddenByAppendedOne(@TempDir Path dir) throws Exception {
            // DEFECT: the archive is opened in append mode, so a second call writes a whole new
            // archive after the first, and readers only see the last one's entries
            // - see CUBRIDIOUtils.zip()
            Path first = write(dir.resolve("first.txt"), "1");
            Path second = write(dir.resolve("second.txt"), "2");
            Path zip = dir.resolve("two.zip");
            CUBRIDIOUtils.zip(zip.toString(), new String[] {first.toString()}, false);
            byte[] firstArchive = Files.readAllBytes(zip);

            CUBRIDIOUtils.zip(zip.toString(), new String[] {second.toString()}, false);

            assertThat(Files.readAllBytes(zip))
                    .startsWith(firstArchive)
                    .hasSizeGreaterThan(firstArchive.length);
            assertThat(entryNames(zip)).containsExactly("second.txt");
        }

        @Test
        @DisplayName("two input directories holding the same file name collide")
        void sameNameInTwoDirectories_throwsZipException(@TempDir Path dir) throws Exception {
            // DEFECT: a directory's own name is dropped and its children go in at the root, so
            // equal names in two input directories become one duplicate entry
            // - see CUBRIDIOUtils.zip()
            write(dir.resolve("in1/a.txt"), "A1");
            write(dir.resolve("in2/a.txt"), "A2");

            assertThatThrownBy(
                            () ->
                                    CUBRIDIOUtils.zip(
                                            dir.resolve("dup.zip").toString(),
                                            new String[] {
                                                dir.resolve("in1").toString(),
                                                dir.resolve("in2").toString()
                                            },
                                            false))
                    .isInstanceOf(ZipException.class);
        }

        @Test
        @DisplayName("removing the inputs deletes files but leaves a directory that has content")
        void removeFlag_leavesNonEmptyDirectory(@TempDir Path dir) throws Exception {
            // DEFECT: each input is removed with File.delete(), which cannot delete a directory
            // that still has files, so a zipped directory stays where it was
            // - see CUBRIDIOUtils.zip()
            Path folder = dir.resolve("rm");
            write(folder.resolve("r.txt"), "R");
            Path file = write(dir.resolve("rmfile.txt"), "X");

            CUBRIDIOUtils.zip(
                    dir.resolve("rm.zip").toString(),
                    new String[] {folder.toString(), file.toString()},
                    true);

            assertThat(file).doesNotExist();
            assertThat(folder.resolve("r.txt")).exists();
        }
    }
}
