/*
 * Copyright (C) 2008 Search Solution Corporation.
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
 * - Neither the name of the <ORGANIZATION> nor the names of its contributors
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
package com.cubrid.cubridmigration.core.engine.task;

import com.cubrid.common.log.LogUtil;
import com.cubrid.cubridmigration.core.common.CUBRIDIOUtils;
import com.cubrid.cubridmigration.core.common.PathUtils;

import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merge template files to output files in thread.
 *
 * @author Kevin Cao
 * @version 1.0 - 2011-10-19 created by Kevin Cao
 */
public class FileMergeRunnable implements Runnable, IMigrationTask {
    private static final Logger LOG = LogUtil.getLogger(FileMergeRunnable.class);
    private static final Map<String, SXSSFWorkbook> openXLSX =
            new ConcurrentHashMap<String, SXSSFWorkbook>();

    private final String sourceFile;
    private final String targetFile;
    private final String targetCharset;
    private final boolean deleteFile;
    private final boolean isTextFile;
    private final RunnableResultHandler listener;

    public FileMergeRunnable(
            String sourceFile,
            String targetFile,
            String targetCharset,
            RunnableResultHandler listener,
            boolean deleteFile,
            boolean isTextFile) {
        this.sourceFile = sourceFile;
        this.listener = listener;
        this.targetFile = targetFile;
        this.deleteFile = deleteFile;
        this.isTextFile = isTextFile;
        this.targetCharset = targetCharset;
    }

    /** Get port and start transport file to server. */
    public void run() {
        try {
            if (isTextFile) {
                CUBRIDIOUtils.mergeFile(sourceFile, targetFile);
            } else {
                if (targetFile.endsWith(".xlsx")) {
                    mergeXLSXFile();
                } else {
                    mergeXLSFile();
                }
            }
            if (listener != null) {
                listener.success();
            }
        } catch (Throwable ex) {
            if (listener != null) {
                listener.failed(ex.getMessage());
            }
        }
        if (deleteFile) {
            PathUtils.deleteFile(new File(sourceFile));
        }
    }

    /**
     * Merge XLS files
     *
     * @throws Exception ex0
     */
    private void mergeXLSFile() throws Exception {
        Workbook srcWB = null;
        WritableWorkbook tarWW = null;
        Workbook tarWB = null;
        try {
            srcWB = Workbook.getWorkbook(new File(sourceFile));
            jxl.Sheet sheet = srcWB.getSheet(0);

            File targetXLSFile = new File(targetFile);
            if (!targetXLSFile.exists()) {
                PathUtils.createFile(targetXLSFile);
            }

            WorkbookSettings tarWS = new WorkbookSettings();
            tarWS.setEncoding(targetCharset);
            final File tarFile = new File(targetFile);
            WritableSheet tarSheet;
            if (tarFile.exists() && tarFile.length() > 0) {
                tarWB = Workbook.getWorkbook(tarFile, tarWS);
                tarWW = Workbook.createWorkbook(tarFile, tarWB);
                tarSheet = tarWW.getSheet(0);
            } else {
                tarWW = Workbook.createWorkbook(tarFile, tarWS);
                tarSheet = tarWW.createSheet(sheet.getName(), 0);
            }
            int total = tarSheet.getRows();
            for (int i = 0; i < sheet.getRows(); i++) {
                jxl.Cell[] values = sheet.getRow(i);
                for (int j = 0; j < values.length; j++) {
                    tarSheet.addCell(new jxl.write.Label(j, total, values[j].getContents()));
                }
                total++;
            }
            tarWW.write();
        } finally {
            if (srcWB != null) {
                srcWB.close();
            }
            if (tarWW != null) {
                tarWW.close();
            }
            if (tarWB != null) {
                tarWB.close();
            }
        }
    }

    /** Merge XLSX files */
    private void mergeXLSXFile() throws Exception {
        SXSSFWorkbook tarWB = openXLSX.get(targetFile);
        if (tarWB == null) {
            tarWB = new SXSSFWorkbook(100);
            openXLSX.put(targetFile, tarWB);
        }

        File srcFile = new File(sourceFile);
        try (FileInputStream srcFis = new FileInputStream(srcFile);
                XSSFWorkbook srcWB = new XSSFWorkbook(srcFis)) {

            Sheet srcSheet = srcWB.getSheetAt(0);
            Sheet tarSheet = tarWB.getSheet(srcSheet.getSheetName());
            if (tarSheet == null) {
                tarSheet = tarWB.createSheet(srcSheet.getSheetName());
            }

            int total = tarSheet.getPhysicalNumberOfRows() > 0 ? tarSheet.getLastRowNum() + 1 : 0;
            for (int i = 0; i <= srcSheet.getLastRowNum(); i++) {
                Row srcRow = srcSheet.getRow(i);
                if (srcRow == null) {
                    continue;
                }
                Row tarRow = tarSheet.createRow(total++);
                for (int j = 0; j < srcRow.getLastCellNum(); j++) {
                    Cell srcCell = srcRow.getCell(j);
                    Cell tarCell = tarRow.createCell(j);
                    if (srcCell != null) {
                        tarCell.setCellValue(srcCell.toString());
                    } else {
                        tarCell.setCellValue("");
                    }
                }
            }
        }
    }

    /** Flush, write to disk, and close target XLSX workbook */
    public static void flushAndCloseXLSX(String targetFile) {
        if (targetFile == null) {
            return;
        }
        SXSSFWorkbook wb = openXLSX.remove(targetFile);
        if (wb == null) {
            return;
        }
        File file = new File(targetFile);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            wb.write(fos);
            fos.flush();
        } catch (IOException e) {
            LOG.error("Failed to write XLSX file: " + targetFile, e);
        } finally {
            try {
                wb.close();
            } catch (IOException e) {
                LOG.error("Failed to close XLSX workbook: " + targetFile, e);
            }
        }
    }
}
