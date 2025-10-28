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
package com.cubrid.cubridmigration.core.engine.template.reader;

import com.cubrid.cubridmigration.core.common.PathUtils;
import com.cubrid.cubridmigration.core.engine.config.MigrationConfiguration;
import com.cubrid.cubridmigration.core.engine.exception.ErrorMigrationTemplateException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.commons.lang3.StringUtils;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Utility class for reading and parsing migration template files.
 *
 * <p>This class provides static methods to parse migration configuration XML files into {@link
 * MigrationConfiguration} objects using a SAX-based parser.
 *
 * <p>It supports parsing from both file paths and input streams, and automatically assigns a
 * configuration name if not specified in the template.
 */
public final class MigrationTemplateReader {

    private static final String DEFAULT_MIGRATION_SCRIPT_NAME = "migration_script";

    private MigrationTemplateReader() {}

    public static MigrationConfiguration parse(String fileName) {
        try {
            final FileInputStream fis = new FileInputStream(fileName);
            try {
                final MigrationConfiguration config = parse(fis);
                autoNameConfiguration(fileName, config);
                return config;
            } finally {
                fis.close();
            }
        } catch (FileNotFoundException e) {
            throw new ErrorMigrationTemplateException(e);
        } catch (IOException ex) {
            throw new ErrorMigrationTemplateException(ex);
        }
    }

    public static MigrationConfiguration parse(InputStream configInputStream) {
        MigrationTemplateHandler reader = new MigrationTemplateHandler();
        parse(configInputStream, reader);
        MigrationConfiguration config = reader.getResult();
        if (!config.hasObjects2Export()) {
            throw new ErrorMigrationTemplateException("Invalid Configuration file.");
        }
        return config;
    }

    public static void parse(InputStream file, DefaultHandler handler) {
        try {
            SAXParserFactory sf = SAXParserFactory.newInstance();
            sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            sf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            sf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            sf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            sf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            sf.setValidating(false);

            SAXParser sp = sf.newSAXParser();
            InputSource is = new InputSource(new InputStreamReader(file, StandardCharsets.UTF_8));
            sp.parse(is, handler);
        } catch (Exception e) {
            throw new ErrorMigrationTemplateException(e);
        }
    }

    private static void autoNameConfiguration(
            String fileName, final MigrationConfiguration config) {
        if (StringUtils.isBlank(config.getName())) {
            String cfgName = PathUtils.getFileNameWithoutExtendName(new File(fileName).getName());
            if (StringUtils.isBlank(cfgName)) {
                cfgName = DEFAULT_MIGRATION_SCRIPT_NAME;
            }
            config.setName(cfgName);
        }
    }
}
