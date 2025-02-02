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
package com.axelor.studio.service.builder;

import com.axelor.common.Inflector;
import com.axelor.common.ObjectUtils;
import com.axelor.data.Listener;
import com.axelor.data.xml.XMLBind;
import com.axelor.data.xml.XMLBindJson;
import com.axelor.data.xml.XMLConfig;
import com.axelor.data.xml.XMLImporter;
import com.axelor.data.xml.XMLInput;
import com.axelor.db.JpaSecurity;
import com.axelor.db.Model;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.MetaJsonModel;
import com.axelor.meta.db.MetaJsonRecord;
import com.axelor.meta.db.repo.MetaJsonModelRepository;
import com.axelor.studio.db.App;
import com.axelor.studio.db.StudioApp;
import com.axelor.studio.db.repo.AppRepository;
import com.axelor.studio.db.repo.StudioAppRepository;
import com.axelor.studio.exception.StudioExceptionMessage;
import com.axelor.studio.service.loader.AppLoaderExportService;
import com.axelor.studio.service.loader.AppLoaderImportService;
import com.axelor.text.GroovyTemplates;
import com.axelor.utils.ExceptionTool;
import com.axelor.utils.context.FullContext;
import com.axelor.utils.context.FullContextHelper;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StudioAppService {

  @Inject protected JpaSecurity jpaSecurity;

  @Inject protected AppLoaderExportService appLoaderExportService;
  @Inject protected AppLoaderImportService appLoaderImportService;

  @Inject protected StudioAppRepository studioAppRepo;
  @Inject private AppRepository appRepo;

  public static final String STUDIO_APP_CODE = "code";
  public static final String STUDIO_APP_NAME = "name";
  public static final String STUDIO_APP_DESC = "description";
  public static final String STUDIO_APP_SEQ = "sequence";
  public static final String STUDIO_APP_IMAGE = "image";
  public static final String STUDIO_APP_MODULES = "modules";
  public static final String STUDIO_APP_DEPENDS_ON = "dependsOn";

  private final Logger log = LoggerFactory.getLogger(StudioAppService.class);

  public StudioApp build(StudioApp studioApp) {

    checkCode(studioApp);

    App app = studioApp.getGeneratedApp();

    if (app == null) {
      app = new App(studioApp.getName(), studioApp.getCode());
    } else {
      app.setCode(studioApp.getCode());
      app.setName(studioApp.getName());
    }

    app.setIsCustom(true);
    app.setTypeSelect(AppRepository.TYPE_CUSTOM);
    app.setIsInAppView(studioApp.getIsInAppView());
    app.setImage(studioApp.getImage());
    app.setDescription(studioApp.getDescription());
    Set<App> depends = new HashSet<App>();
    if (studioApp.getDependsOnSet() != null) {
      depends.addAll(studioApp.getDependsOnSet());
      app.setDependsOnSet(depends);
    }
    app.setSequence(studioApp.getSequence());
    app.setModules(studioApp.getModules());

    studioApp.setGeneratedApp(appRepo.save(app));

    return studioApp;
  }

  private void checkCode(StudioApp studioApp) {

    App app = appRepo.findByCode(studioApp.getCode());

    if (app != null && app != studioApp.getGeneratedApp()) {
      throw new IllegalStateException(
          String.format(I18n.get(StudioExceptionMessage.STUDIO_APP_1), studioApp.getCode()));
    }
  }

  @Transactional
  public void clean(StudioApp studioApp) {

    if (studioApp.getGeneratedApp() != null) {
      appRepo.remove(studioApp.getGeneratedApp());
      studioApp.setGeneratedApp(null);
    }
  }

  @Transactional
  public void deleteApp(StudioApp studioApp) {
    Beans.get(StudioAppRepository.class).remove(studioApp);
  }

  public MetaFile importApp(Map<String, Object> dataFileMap) {

    File dataDir = null;
    File zipFile = null;
    try {
      dataDir = Files.createTempDirectory("").toFile();
      zipFile = MetaFiles.getPath((String) dataFileMap.get("filePath")).toFile();
      extractImportZip(dataDir, zipFile);
      StringBuilder logSB = new StringBuilder();

      for (File confiFile : appLoaderImportService.getAppImportConfigFiles(dataDir)) {
        XMLImporter xmlImporter =
            new XMLImporter(confiFile.getAbsolutePath(), dataDir.getAbsolutePath());
        xmlImporter.addListener(
            new Listener() {

              @Override
              public void imported(Integer total, Integer success) {}

              @Override
              public void imported(Model bean) {}

              @Override
              public void handle(Model bean, Exception e) {
                logSB.append(String.format("Error Importing: %s\n", bean));
                ExceptionTool.trace(e);
              }
            });
        xmlImporter.run();
      }

      if (logSB.length() > 0) {
        File logFile = MetaFiles.createTempFile("import-", "log").toFile();
        org.apache.commons.io.FileUtils.writeStringToFile(
            logFile, logSB.toString(), Charset.forName("UTF-8"));
        return Beans.get(MetaFiles.class).upload(logFile);
      }
    } catch (IOException e) {
      ExceptionTool.trace(e);
    } finally {
      try {
        if (zipFile != null) Files.deleteIfExists(zipFile.toPath());
        if (dataDir != null) Files.deleteIfExists(dataDir.toPath());
      } catch (Exception e) {
      }
    }
    return null;
  }

  protected void extractImportZip(File dataDir, File zipFile) {

    if (zipFile == null) {
      return;
    }

    try {
      FileInputStream fin = new FileInputStream(zipFile);
      ZipInputStream zipInputStream = new ZipInputStream(fin);
      ZipEntry zipEntry = zipInputStream.getNextEntry();

      while (zipEntry != null) {
        FileOutputStream fout = new FileOutputStream(new File(dataDir, zipEntry.getName()));
        IOUtils.copy(zipInputStream, fout);
        fout.close();
        zipEntry = zipInputStream.getNextEntry();
      }

      zipInputStream.close();
    } catch (Exception e) {
    }
  }

  public MetaFile exportApps(List<Integer> studioAppIds, boolean isExportData) {

    MetaFile exportFile = null;
    File exportDir = null;
    try {
      exportDir = Files.createTempDirectory("").toFile();
      int[] studioAppIdArr = studioAppIds.stream().mapToInt(id -> id).toArray();
      generateExportFile(exportDir, isExportData, studioAppIdArr);
      File zipFile = appLoaderExportService.createExportZip(exportDir);
      exportFile = Beans.get(MetaFiles.class).upload(zipFile);
    } catch (Exception e) {
      ExceptionTool.trace(e);
    } finally {
      try {
        if (exportDir != null) Files.deleteIfExists(exportDir.toPath());
      } catch (Exception e) {
      }
    }

    return exportFile;
  }

  public MetaFile exportApp(StudioApp studioApp, boolean isExportData) {

    MetaFile exportFile = null;
    File exportDir = null;
    try {
      exportDir = Files.createTempDirectory("").toFile();
      generateExportFile(exportDir, isExportData, Integer.parseInt(studioApp.getId().toString()));
      File zipFile = appLoaderExportService.createExportZip(exportDir);
      exportFile = Beans.get(MetaFiles.class).upload(zipFile);
    } catch (Exception e) {
      ExceptionTool.trace(e);
    } finally {
      try {
        if (exportDir != null) Files.deleteIfExists(exportDir.toPath());
      } catch (Exception e) {
      }
    }

    return exportFile;
  }

  protected void generateExportFile(File exportDir, boolean isExportData, int... studioAppIds) {

    try {
      generateMetaDataFile(exportDir, studioAppIds);

      for (int studioAppId : studioAppIds) {
        if (!isExportData) {
          continue;
        }
        List<MetaJsonModel> jsonModels =
            Beans.get(MetaJsonModelRepository.class)
                .all()
                .filter("self.studioApp.id = :studioAppId")
                .bind("studioAppId", studioAppId)
                .fetch();
        if (ObjectUtils.notEmpty(jsonModels)) {
          XMLConfig xmlConfig = new XMLConfig();
          for (MetaJsonModel jsonModel : jsonModels) {
            List<FullContext> records = FullContextHelper.filter(jsonModel.getName(), null);
            if (ObjectUtils.isEmpty(records)) {
              continue;
            }

            Map<String, Object> jsonFieldMap = MetaStore.findJsonFields(jsonModel.getName());
            appLoaderExportService.fixTargetName(jsonFieldMap);
            xmlConfig.getInputs().add(createXMLInput(jsonModel, jsonFieldMap, false));
            xmlConfig.getInputs().add(createXMLInput(jsonModel, jsonFieldMap, true));
            generateModelDataFiles(jsonModel, exportDir, jsonFieldMap, records);
          }

          if (ObjectUtils.notEmpty(xmlConfig.getInputs())) {
            File configFile = new File(exportDir, "data-config.xml");
            appLoaderExportService.writeXmlConfig(configFile, xmlConfig);
          }
        }
      }
    } catch (Exception e) {
      ExceptionTool.trace(e);
    }
  }

  protected void generateMetaDataFile(File parentFile, int... studioAppIds) {

    Map<String, InputStream> templateISmap = appLoaderExportService.getExportTemplateResources();
    GroovyTemplates templates = new GroovyTemplates();
    Map<String, Object> ctx = new HashMap<>();
    List<Long> ids =
        Arrays.stream(studioAppIds)
            .boxed()
            .map(id -> Long.parseLong(id + ""))
            .collect(Collectors.toList());
    ctx.put("__ids__", ids);

    for (Entry<String, InputStream> mapEntry : templateISmap.entrySet()) {
      log.debug("Exporting file: {}", mapEntry.getKey());
      File file = null;
      FileWriter writer = null;
      try {
        file = new File(parentFile, mapEntry.getKey());
        writer = new FileWriter(file);
        templates.from(new InputStreamReader(mapEntry.getValue())).make(ctx).render(writer);
      } catch (Exception e) {
        ExceptionTool.trace(e);
      } finally {
        try {
          if (writer != null) writer.close();
          deleteEmptyFile(file);
        } catch (IOException e) {
        }
      }
    }
  }

  protected void deleteEmptyFile(File file) {

    try {
      if (file == null) return;

      if (file.length() == 0) {
        file.delete();
      } else {
        long lines = java.nio.file.Files.lines(file.toPath()).count();
        if (lines == 1) {
          file.delete();
        }
      }
    } catch (Exception e) {
      ExceptionTool.trace(e);
    }
  }

  protected XMLInput createXMLInput(
      MetaJsonModel jsonModel, Map<String, Object> jsonFieldMap, boolean relationalInput) {

    XMLInput xmlInput = new XMLInput();
    String modelName = jsonModel.getName();
    xmlInput.setFileName(String.format("%s.xml", modelName));
    String dasherizeModel = Inflector.getInstance().dasherize(modelName);
    xmlInput.setRoot(String.format("%ss", dasherizeModel));

    XMLBindJson xmlBindJson = new XMLBindJson();
    xmlBindJson.setNode(dasherizeModel);
    xmlBindJson.setJsonModel(modelName);
    xmlBindJson.setSearch("self.name = :name");
    xmlBindJson.setUpdate(true);

    if (relationalInput) {
      xmlBindJson.setCreate(false);
    }

    xmlBindJson.setBindings(getFieldBinding(jsonModel, jsonFieldMap, relationalInput));
    List<XMLBind> rootBindings = new ArrayList<XMLBind>();
    rootBindings.add(xmlBindJson);
    xmlInput.setBindings(rootBindings);

    return xmlInput;
  }

  protected List<XMLBind> getFieldBinding(
      MetaJsonModel jsonModel, Map<String, Object> jsonFieldMap, boolean relationalInput) {

    List<XMLBind> fieldBindings = new ArrayList<>();
    for (MetaJsonField jsonField : jsonModel.getFields()) {
      String fieldName = jsonField.getName();
      XMLBind dummyBind = new XMLBind();
      dummyBind.setNode(fieldName);
      dummyBind.setField("_" + fieldName);
      fieldBindings.add(dummyBind);

      if (relationalInput
          && jsonField.getTargetJsonModel() == null
          && jsonField.getTargetModel() == null) {
        continue;
      }

      XMLBind xmlBind = new XMLBind();
      xmlBind.setNode(jsonField.getName());
      xmlBind.setField("$attrs." + jsonField.getName());
      if (jsonField.getTargetJsonModel() != null || jsonField.getTargetModel() != null) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldAttrs = (Map<String, Object>) jsonFieldMap.get(fieldName);
        if (ObjectUtils.notEmpty(fieldAttrs)) {
          log.debug("Json Field name: {}, Field attrs: {}", fieldName, fieldAttrs);
          appLoaderExportService.addRelationaJsonFieldBind(jsonField, fieldAttrs, xmlBind);
        }
      } else if (jsonField.getType().equals("boolean")) {
        xmlBind.setAdapter("Boolean");
      }

      fieldBindings.add(xmlBind);
    }

    return fieldBindings;
  }

  @SuppressWarnings("unchecked")
  protected void generateModelDataFiles(
      MetaJsonModel jsonModel,
      File parentDir,
      Map<String, Object> jsonFieldMap,
      List<FullContext> records) {

    try {
      if (!jpaSecurity.isPermitted(JpaSecurity.CAN_READ, MetaJsonRecord.class)) {
        return;
      }

      if (ObjectUtils.notEmpty(records)) {
        String modelName = jsonModel.getName();
        String dasherizeModel = Inflector.getInstance().dasherize(modelName);

        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        stringBuffer.append(
            String.format(
                "<%ss xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                    + "  xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n\n",
                dasherizeModel));
        for (FullContext record : records) {
          if (!jpaSecurity.isPermitted(
              JpaSecurity.CAN_READ, MetaJsonRecord.class, (Long) record.get("id"))) {
            continue;
          }
          stringBuffer.append(String.format("<%s>\n", dasherizeModel));

          for (MetaJsonField jsonField : jsonModel.getFields()) {
            String field = jsonField.getName();
            Map<String, Object> fieldAttrs = (Map<String, Object>) jsonFieldMap.get(field);
            stringBuffer.append(
                String.format(
                    "\t<%s>%s</%s>\n",
                    field,
                    appLoaderExportService.extractJsonFieldValue(record, fieldAttrs),
                    field));
          }
          stringBuffer.append(String.format("</%s>\n\n", dasherizeModel));
        }
        stringBuffer.append(String.format("</%ss>\n", dasherizeModel));
        File dataFile = new File(parentDir, modelName + ".xml");
        org.apache.commons.io.FileUtils.writeStringToFile(
            dataFile, stringBuffer.toString(), Charset.forName("UTF-8"));
      }
    } catch (IOException e) {
      ExceptionTool.trace(e);
    }
  }
}
