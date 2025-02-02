/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2022 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.studio.service.loader;

import com.axelor.common.FileUtils;
import com.axelor.common.ResourceUtils;
import com.axelor.data.Listener;
import com.axelor.data.xml.XMLImporter;
import com.axelor.db.Model;
import com.axelor.meta.MetaFiles;
import com.axelor.studio.db.AppLoader;
import com.axelor.studio.db.repo.AppLoaderRepository;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.IOUtils;

public class AppLoaderImportServiceImpl implements AppLoaderImportService {

  private static final String[] IMPORT_FILES =
      new String[] {
        "studio-app.xml",
        "studio-selection.xml",
        "json-model.xml",
        "json-field.xml",
        "json-model-call.xml",
        "studio-chart.xml",
        "studio-dashboard.xml",
        "studio-dashlet.xml",
        "studio-dashboard-call.xml",
        "studio-action.xml",
        "studio-menu.xml",
        "ws-request.xml",
        "ws-authenticator.xml",
        "ws-connector.xml",
        "meta-view.xml"
      };

  @Inject protected AppLoaderRepository appLoaderRepository;

  @Inject protected MetaFiles metaFiles;

  @Inject protected AppLoaderExportService appLoaderExportService;

  @Override
  public void importApps(AppLoader appLoader) throws FileNotFoundException, IOException {

    if (appLoader.getImportMetaFile() == null) {
      return;
    }

    File dataDir = Files.createTempDirectory("").toFile();
    extractImportZip(appLoader, dataDir);

    File logFile = importApp(appLoader, dataDir);

    addLogFile(appLoader, logFile);

    FileUtils.deleteDirectory(dataDir);
  }

  @Transactional
  protected void addLogFile(AppLoader appLoader, File logFile) throws IOException {

    appLoader = appLoaderRepository.find(appLoader.getId());
    if (appLoader.getImportLog() == null) {
      appLoader.setImportLog(metaFiles.upload(logFile));
    }

    appLoader.setImportedOn(LocalDateTime.now());
    appLoaderRepository.save(appLoader);
  }

  protected void extractImportZip(AppLoader appLoader, File dataDir)
      throws FileNotFoundException, IOException {

    FileInputStream fin =
        new FileInputStream(MetaFiles.getPath(appLoader.getImportMetaFile()).toFile());
    try (ZipInputStream zipInputStream = new ZipInputStream(fin)) {
      ZipEntry zipEntry = zipInputStream.getNextEntry();

      while (zipEntry != null) {
        FileOutputStream fout = new FileOutputStream(new File(dataDir, zipEntry.getName()));
        IOUtils.copy(zipInputStream, fout);
        fout.close();
        zipEntry = zipInputStream.getNextEntry();
      }
    }
  }

  protected File importApp(AppLoader appLoader, File dataDir)
      throws IOException, FileNotFoundException {

    File logFile =
        appLoader.getImportLog() != null
            ? MetaFiles.getPath(appLoader.getImportLog()).toFile()
            : MetaFiles.createTempFile("import-", "log").toFile();

    try (PrintWriter pw = new PrintWriter(logFile)) {
      for (File configFile : getAppImportConfigFiles(dataDir)) {
        XMLImporter xmlImporter =
            new XMLImporter(configFile.getAbsolutePath(), dataDir.getAbsolutePath());
        xmlImporter.setContext(getImportContext(appLoader));

        xmlImporter.addListener(
            new Listener() {

              @Override
              public void imported(Integer total, Integer success) {}

              @Override
              public void imported(Model bean) {}

              @Override
              public void handle(Model bean, Exception e) {
                pw.write("Error Importing: " + bean);
                e.printStackTrace(pw);
              }
            });

        xmlImporter.run();
      }
    }

    return logFile;
  }

  @Override
  public List<File> getAppImportConfigFiles(File dataDir)
      throws FileNotFoundException, IOException {

    List<File> configFiles = new ArrayList<File>();

    for (String fileName : IMPORT_FILES) {
      String dataFileName = fileName.replace("-call.xml", ".xml");
      if (!(new File(dataDir, dataFileName)).exists()) {
        continue;
      }
      File configFile = new File(dataDir, fileName.replace(".xml", "-config.xml"));
      FileOutputStream fout = new FileOutputStream(configFile);
      InputStream inStream = ResourceUtils.getResourceStream("data-import/" + fileName);
      IOUtils.copy(inStream, fout);
      inStream.close();
      fout.close();
      configFiles.add(configFile);
    }

    File dataConfigFile = new File(dataDir, "data-config.xml");
    if (dataConfigFile.exists()) {
      configFiles.add(dataConfigFile);
    }

    return configFiles;
  }

  protected Map<String, Object> getImportContext(AppLoader appLoader) {
    return ImmutableMap.of("appLoaderId", appLoader.getId());
  }
}
