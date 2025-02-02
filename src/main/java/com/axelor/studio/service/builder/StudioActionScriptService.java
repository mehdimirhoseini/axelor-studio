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

import static com.axelor.utils.MetaJsonFieldType.JSON_MANY_TO_MANY;
import static com.axelor.utils.MetaJsonFieldType.JSON_MANY_TO_ONE;
import static com.axelor.utils.MetaJsonFieldType.JSON_ONE_TO_MANY;
import static com.axelor.utils.MetaJsonFieldType.MANY_TO_MANY;
import static com.axelor.utils.MetaJsonFieldType.MANY_TO_ONE;
import static com.axelor.utils.MetaJsonFieldType.ONE_TO_MANY;
import static com.axelor.utils.MetaJsonFieldType.ONE_TO_ONE;

import com.axelor.common.Inflector;
import com.axelor.meta.db.MetaAction;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.MetaJsonRecord;
import com.axelor.studio.db.StudioAction;
import com.axelor.studio.db.StudioActionLine;
import com.axelor.studio.db.repo.StudioActionLineRepository;
import com.axelor.studio.db.repo.StudioActionRepository;
import com.axelor.studio.service.StudioMetaService;
import com.axelor.studio.service.filter.FilterSqlService;
import com.axelor.utils.ExceptionTool;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StudioActionScriptService {

  private static final String INDENT = "\t";

  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Inflector inflector = Inflector.getInstance();

  private List<StringBuilder> fbuilder = null;

  private int varCount = 0;

  private boolean isCreate = false;

  private boolean isObjToJson = false;

  @Inject private StudioActionLineRepository studioActionLineRepo;

  @Inject private StudioMetaService metaService;

  @Inject private FilterSqlService filterSqlService;

  public MetaAction build(StudioAction studioAction) {

    String name = studioAction.getName();
    String code = null;
    String lang = "js";
    String transactional = "true";

    if (studioAction.getTypeSelect() == StudioActionRepository.TYPE_SELECT_SCRIPT) {
      code = "\n" + studioAction.getScriptText();
      if (studioAction.getScriptType() == 1) {
        lang = "groovy";
      }
      if (studioAction.getTransactional()) {
        transactional = "false";
      }
    } else {
      code = generateScriptCode(studioAction);
    }

    String xml =
        "<action-script name=\""
            + name
            + "\" "
            + "id=\""
            + studioAction.getXmlId()
            + "\" model=\""
            + MetaJsonRecord.class.getName()
            + "\">\n\t"
            + "<script language=\""
            + lang
            + "\" transactional=\""
            + transactional
            + "\">\n\t<![CDATA["
            + code
            + "\n\t]]>\n\t</script>\n</action-script>";

    return metaService.updateMetaAction(
        studioAction.getName(), "action-script", xml, null, studioAction.getXmlId());
  }

  private String generateScriptCode(StudioAction studioAction) {

    StringBuilder stb = new StringBuilder();
    fbuilder = new ArrayList<>();
    varCount = 1;
    int level = 1;
    String condition = studioAction.getConditionText();

    stb.append(
        condition == null
            ? format("var ctx = $request.context;", level)
            : format("var ctx = $request.context;", level)
                + "\n"
                + format("if(" + condition.replaceAll("\\$.", "ctx.") + "){", level));

    String targetModel;
    if (studioAction.getTypeSelect() == StudioActionRepository.TYPE_SELECT_CREATE) {
      targetModel = studioAction.getTargetModel();
      isCreate = true;
      addCreateCode(studioAction.getIsJson(), stb, level, targetModel);
      if (studioAction.getOpenRecord()) {
        addOpenRecord(studioAction.getIsJson(), stb, level, targetModel);
      }
      if (!Strings.isNullOrEmpty(studioAction.getDisplayMsg())) {
        stb.append("\n");
        stb.append(format("$response.setInfo('" + studioAction.getDisplayMsg() + "')", level));
      }
    } else {
      targetModel = studioAction.getModel();
      isCreate = false;
      addUpdateCode(studioAction.getIsJson(), stb, level, targetModel);
    }

    stb.append(condition == null ? "\n" : format("}", level) + "\n");

    addRootFunction(studioAction, stb, level);

    stb.append(Joiner.on("").join(fbuilder));

    return stb.toString();
  }

  private void addCreateCode(boolean isJson, StringBuilder stb, int level, String targetModel) {

    if (isJson) {
      stb.append(format("var target = $json.create('" + targetModel + "');", level));
      stb.append(format("target = setVar0(null, ctx, {});", level));
      stb.append(format("target = $json.save(target);", level));
    } else {
      stb.append(format("var target = new " + targetModel + "();", level));
      stb.append(format("target = setVar0(null, ctx, {});", level));
      stb.append(format("$em.persist(target);", level));
    }
  }

  private void addOpenRecord(boolean isJson, StringBuilder stb, int level, String targetModel) {

    stb.append("\n");

    if (isJson) {
      String title = inflector.humanize(targetModel);
      stb.append(
          format(
              "$response.setView(com.axelor.meta.schema.actions.ActionView.define('" + title + "')",
              level));
      stb.append(format(".model('com.axelor.meta.db.MetaJsonRecord')", level + 1));
      stb.append(format(".add('grid','custom-model-" + targetModel + "-grid')", level + 1));
      stb.append(format(".add('form','custom-model-" + targetModel + "-form')", level + 1));
      stb.append(format(".domain('self.jsonModel = :jsonModel')", level + 1));
      stb.append(format(".context('jsonModel', '" + targetModel + "')", level + 1));
      stb.append(format(".context('_showRecord', target.id)", level + 1));
      stb.append(format(".map())", level + 1));
    } else {
      String title = inflector.humanize(targetModel.substring(targetModel.lastIndexOf('.') + 1));
      stb.append(
          format(
              "$response.setView(com.axelor.meta.schema.actions.ActionView.define('" + title + "')",
              level));
      stb.append(format(".model('" + targetModel + "')", level + 1));
      stb.append(format(".add('grid')", level + 1));
      stb.append(format(".add('form')", level + 1));
      stb.append(format(".context('_showRecord', target.id)", level + 1));
      stb.append(format(".map())", level + 1));
    }
  }

  private void addUpdateCode(boolean isJson, StringBuilder stb, int level, String targetModel) {

    if (isJson) {
      stb.append(format("var target = {};", level));
    } else {
      stb.append(format("var target = ctx.asType(" + targetModel + ".class)", level));
    }

    stb.append(format("target = setVar0(null, ctx, {});", level));
    stb.append(format("$response.setValues(target);", level));
  }

  private void addRootFunction(StudioAction studioAction, StringBuilder stb, int level) {
    List<StudioActionLine> lines = studioAction.getLines();
    boolean isJsonField = lines.stream().anyMatch(l -> l.getIsTargetJson());

    stb.append(format("function setVar0($$, $, _$){", level));
    String bindings = addFieldsBinding("target", studioAction.getLines(), level + 1, isJsonField);
    stb.append(bindings);
    stb.append(format("return target;", level + 1));
    stb.append(format("}", level));
  }

  private String format(String line, int level) {

    return "\n" + Strings.repeat(INDENT, level) + line;
  }

  private String addFieldsBinding(
      String target, List<StudioActionLine> lines, int level, boolean json) {

    StringBuilder stb = new StringBuilder();

    lines.sort(
        (l1, l2) -> {
          if (l1.getDummy() && !l2.getDummy()) {
            return -1;
          }
          if (!l1.getDummy() && l2.getDummy()) {
            return 1;
          }
          return 0;
        });

    if (json) {
      computeAttrsField(target, level, lines, stb);
    }

    for (StudioActionLine line : lines) {

      String name = line.getName();
      String value = line.getValue();
      if (value != null && value.contains(".sum(")) {
        value = getSum(value, line.getFilter());
      }
      if (line.getDummy()) {
        stb.append(format("_$." + name + " = " + value + ";", level));
        continue;
      }

      MetaJsonField jsonField = line.getMetaJsonField();
      MetaField metaField = line.getMetaField();

      if (line.getIsTargetJson()
          && jsonField != null
          && (jsonField.getTargetJsonModel() != null || jsonField.getTargetModel() != null)) {
        value = addRelationalBinding(line, target, true);
      } else if (!line.getIsTargetJson()
          && metaField != null
          && metaField.getRelationship() != null) {
        value = addRelationalBinding(line, target, false);
      }
      // else {
      // MetaJsonField valueJson = line.getValueJson();
      // if (valueJson != null && valueJson.getType().contentEquals("many-to-one")) {
      // value = value.replace("$." + valueJson.getName(),"$json.create($json.find($."
      // +
      // valueJson.getName() + ".id))");
      // }
      // }

      if (value != null
          && metaField != null
          && metaField.getTypeName().equals(BigDecimal.class.getSimpleName())) {
        value = "new BigDecimal(" + value + ")";
      }

      String condition = line.getConditionText();
      if (condition != null) {
        if (jsonField != null && json) {
          String attrsField = jsonField.getModelField();
          stb.append(
              format(
                  "if(" + condition + "){" + attrsField + "." + name + " = " + value + ";}",
                  level));

        } else {
          stb.append(
              format("if(" + condition + "){" + target + "." + name + " = " + value + ";}", level));
        }

      } else {
        if (jsonField != null && json) {
          String attrsField = jsonField.getModelField();
          stb.append(format(attrsField + "." + name + " = " + value + ";", level));

        } else {
          stb.append(format(target + "." + name + " = " + value + ";", level));
        }
      }
    }

    if (json) {
      Set<String> attrsFields = getAttrsFields(lines);
      attrsFields.forEach(
          attrsField -> {
            stb.append(
                format(
                    target + "." + attrsField + " = JSON.stringify(" + attrsField + ");", level));
          });
    }

    return stb.toString();
  }

  private void computeAttrsField(
      String target, int level, List<StudioActionLine> lines, StringBuilder stb) {

    Set<String> attrsFields = getAttrsFields(lines);
    attrsFields.forEach(
        attrsField -> {
          stb.append(format("if (" + target + "." + attrsField + " == null) {", level));
          stb.append(format(target + "." + attrsField + " = '{}';", level + 1));
          stb.append(format("}", level));
          stb.append(
              format(
                  "var " + attrsField + " = JSON.parse(" + target + "." + attrsField + ");",
                  level));
        });
  }

  private Set<String> getAttrsFields(List<StudioActionLine> lines) {
    return lines.stream()
        .filter(l -> l.getIsTargetJson())
        .map(l -> l.getMetaJsonField().getModelField())
        .collect(Collectors.toSet());
  }

  private String addRelationalBinding(StudioActionLine line, String target, boolean json) {

    line = studioActionLineRepo.find(line.getId());
    String subCode = null;

    String type =
        json
            ? line.getMetaJsonField().getType()
            : inflector.dasherize(line.getMetaField().getRelationship());

    switch (type) {
      case MANY_TO_ONE:
        subCode = addM2OBinding(line, true, true, json);
        break;
      case MANY_TO_MANY:
        subCode = addM2MBinding(line, json);
        break;
      case ONE_TO_MANY:
        subCode = addO2MBinding(line, target, json);
        break;
      case ONE_TO_ONE:
        subCode = addM2OBinding(line, true, true, json);
        break;
      case JSON_MANY_TO_ONE:
        subCode = addJsonM2OBinding(line, true, true, json);
        break;
      case JSON_MANY_TO_MANY:
        subCode = addJsonM2MBinding(line);
        break;
      case JSON_ONE_TO_MANY:
        subCode = addJsonO2MBinding(line);
        break;
      default:
        throw new IllegalArgumentException("Unknown type");
    }

    return subCode + "($," + line.getValue() + ", _$)";
  }

  private String getTargetModel(StudioActionLine line) {

    MetaJsonField jsonField = line.getMetaJsonField();

    String targetModel = "";
    if (jsonField != null && jsonField.getTargetModel() != null) {
      targetModel = jsonField.getTargetModel();
    }

    MetaField field = line.getMetaField();
    if (field != null && field.getTypeName() != null) {
      targetModel = field.getTypeName();
    }

    return targetModel;
  }

  private String getTargetJsonModel(StudioActionLine line) {

    MetaJsonField jsonField = line.getMetaJsonField();

    if (jsonField != null) {
      return jsonField.getTargetJsonModel().getName();
    }

    return "";
  }

  private String getRootSourceModel(StudioActionLine line) {

    if (line.getStudioAction() != null) {
      return line.getStudioAction().getModel();
    }

    return null;
  }

  private String getSourceModel(StudioActionLine line) {

    MetaJsonField jsonField = line.getValueJson();

    String sourceModel = null;
    Object targetObject = null;

    try {
      if (jsonField != null && jsonField.getTargetModel() != null) {
        if (line.getValue() != null && !line.getValue().contentEquals("$." + jsonField.getName())) {
          targetObject =
              filterSqlService.parseJsonField(
                  jsonField, line.getValue().replace("$.", ""), null, null);
        } else {
          sourceModel = jsonField.getTargetModel();
        }
      }

      MetaField field = line.getValueField();
      if (field != null && field.getTypeName() != null) {
        if (line.getValue() != null && !line.getValue().contentEquals("$." + field.getName())) {
          targetObject =
              filterSqlService.parseMetaField(
                  field, line.getValue().replace("$.", ""), null, null, false);
        } else {
          sourceModel = field.getTypeName();
        }
      }
    } catch (Exception e) {
      ExceptionTool.trace(e);
    }

    if (sourceModel == null && line.getValue() != null && line.getValue().equals("$")) {
      sourceModel = getRootSourceModel(line);
    }

    if (sourceModel == null && line.getValue() != null && line.getValue().equals("$$")) {
      sourceModel = getRootSourceModel(line);
    }

    if (targetObject != null) {
      if (targetObject instanceof MetaJsonField) {
        sourceModel = ((MetaJsonField) targetObject).getTargetModel();
      } else if (targetObject instanceof MetaField) {
        sourceModel = ((MetaField) targetObject).getTypeName();
      }
    }

    return sourceModel;
  }

  private void addObjToJson() {
    if (isObjToJson) {
      return;
    }
    isObjToJson = true;
    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    stb.append(format("", 1));
    stb.append(format("function objToJson($){", 1));
    stb.append(format("var obj = {};", 2));
    stb.append(format("var map = com.axelor.db.mapper.Mapper.toMap($);", 2));
    stb.append(format("map.forEach(function(key, value){", 2));
    stb.append(format("obj[key] = value;", 3));
    stb.append(format("});", 2));
    stb.append(format("return obj;", 2));
    stb.append(format("}", 1));
  }

  private String addM2OBinding(
      StudioActionLine line, boolean search, boolean filter, boolean json) {

    String fname = "setVar" + varCount;
    varCount += 1;

    String tModel = getTargetModel(line);
    String srcModel = getSourceModel(line);

    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    if (tModel.contains(".")) {
      tModel = tModel.substring(tModel.lastIndexOf('.') + 1);
    }
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val = null;", 2));
    if (srcModel != null) {
      stb.append(format("if ($ != null && $.id != null){", 2));
      srcModel = srcModel.substring(srcModel.lastIndexOf('.') + 1);
      stb.append(format("$ = $em.find(" + srcModel + ".class, $.id);", 3));
      log.debug("src model: {}, Target model: {}", srcModel, tModel);
      if (srcModel.contentEquals(tModel)) {
        stb.append(format("val = $", 3));
      }
      stb.append(format("}", 2));
    }

    if (filter && line.getFilter() != null) {
      if (line.getValue() != null) {
        stb.append(format("var map = com.axelor.db.mapper.Mapper.toMap($);", 2));
      } else {
        stb.append(format("var map = com.axelor.db.mapper.Mapper.toMap($$);", 2));
      }
      stb.append(format("val = " + getQuery(tModel, line.getFilter(), false, false), 2));
    } else if (srcModel == null) {
      stb.append(format("val = $;", 2));
    }

    List<StudioActionLine> lines = line.getSubLines();
    if (lines != null && !lines.isEmpty()) {
      boolean isJsonField = lines.stream().anyMatch(l -> l.getIsTargetJson());

      stb.append(format("if (!val) {", 2));
      stb.append(format("val = new " + tModel + "();", 3));
      stb.append(format("}", 2));
      stb.append(addFieldsBinding("val", lines, 2, isJsonField));
      // stb.append(format("$em.persist(val);", 2));
    }
    if (json) {
      addObjToJson();
      stb.append(format("return objToJson(val);", 2));
    } else {
      stb.append(format("return val;", 2));
    }
    stb.append(format("}", 1));

    return fname;
  }

  private String addM2MBinding(StudioActionLine line, boolean json) {

    String fname = "setVar" + varCount;
    varCount += 1;
    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val = " + (json ? "[];" : "new HashSet();"), 2));
    if (line.getFilter() != null) {
      String model = getTargetModel(line);
      stb.append(format("var map = com.axelor.db.mapper.Mapper.toMap($$);", 2));
      stb.append(format("val.addAll(" + getQuery(model, line.getFilter(), false, true) + ");", 2));
      stb.append(format("if(!val.empty){return val;}", 2));
    }

    stb.append(format("if(!$){return val;}", 2));
    stb.append(format("$.forEach(function(v){", 2));
    stb.append(format("v = " + addM2OBinding(line, true, false, json) + "($$, v, _$);", 3));
    stb.append(format(json ? "val.push(v);" : "val.add(v);", 3));
    stb.append(format("});", 2));
    stb.append(format("return val;", 2));
    stb.append(format("}", 1));

    return fname;
  }

  private String addO2MBinding(StudioActionLine line, String target, boolean json) {

    String fname = "setVar" + varCount;
    varCount += 1;
    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val = " + (json ? "[];" : "new ArrayList();"), 2));
    stb.append(format("if(!$){return val;}", 2));
    stb.append(format("$.forEach(function(v){", 2));
    stb.append(format("var item = " + addM2OBinding(line, false, false, json) + "($$, v, _$);", 3));
    if (isCreate && line.getMetaField() != null && line.getMetaField().getMappedBy() != null) {
      stb.append(format("item." + line.getMetaField().getMappedBy() + " = " + target, 3));
    }
    stb.append(format(json ? "val.push(item);" : "val.add(item);", 3));
    stb.append(format("});", 2));
    stb.append(format("return val;", 2));
    stb.append(format("}", 1));

    return fname;
  }

  private String addJsonM2OBinding(
      StudioActionLine line, boolean search, boolean filter, boolean json) {

    String fname = "setVar" + varCount;
    varCount += 1;

    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    String model = getTargetJsonModel(line);
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val = null;", 2));
    // stb.append(format("if ($ != null && $.id != null){", 2));
    // stb.append(format("$ = $json.find($.id);", 3));
    if (search) {
      stb.append(format("if ($ != null && $.id != null) {", 2));
      stb.append(format("val = $json.find($.id);", 3));
      stb.append(format("if (val.jsonModel != '" + model + "'){val = null;} ", 3));
      stb.append(format("}", 2));
    }
    // stb.append(format("}",2));
    if (filter && line.getFilter() != null) {
      String query = getQuery(model, line.getFilter(), true, false);
      stb.append(format("val = " + query, 2));
    }
    List<StudioActionLine> lines = line.getSubLines();
    if (lines != null && !lines.isEmpty()) {
      stb.append(format("if (!val) {", 2));
      stb.append(format("val = $json.create('" + model + "');", 3));
      stb.append(format("}", 2));
      stb.append(format("else {", 2));
      stb.append(format("val = $json.create(val);", 3));
      stb.append(format("}", 2));
      stb.append(addFieldsBinding("val", lines, 2, false));
      stb.append(format("val = $json.save(val);", 2));
    }
    if (json) {
      addObjToJson();
      stb.append(format("return objToJson(val);", 2));
    } else {
      stb.append(format("return val;", 2));
    }
    stb.append(format("}", 1));

    return fname;
  }

  private String addJsonM2MBinding(StudioActionLine line) {

    String fname = "setVar" + varCount;
    varCount += 1;
    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val = [];", 2));
    if (line.getFilter() != null) {
      String model = getTargetJsonModel(line);
      stb.append(format("val.addAll(" + getQuery(model, line.getFilter(), true, true) + ");", 2));
      stb.append(format("if(!val.empty){return val;}", 2));
    }
    stb.append(format("if(!$){return val;}", 2));
    stb.append(format("$.forEach(function(v){", 2));
    stb.append(format("v = " + addJsonM2OBinding(line, true, false, true) + "($$, v, _$);", 3));
    stb.append(format("val.push(v);", 3));
    stb.append(format("});", 2));
    stb.append(format("return val;", 2));
    stb.append(format("}", 1));

    return fname;
  }

  private String addJsonO2MBinding(StudioActionLine line) {

    String fname = "setVar" + varCount;
    varCount += 1;
    StringBuilder stb = new StringBuilder();
    fbuilder.add(stb);
    stb.append(format("", 1));
    stb.append(format("function " + fname + "($$, $, _$){", 1));
    stb.append(format("var val = [];", 2));
    stb.append(format("if(!$){return val;}", 2));
    stb.append(format("$.forEach(function(v){", 2));
    stb.append(format("v = " + addJsonM2OBinding(line, false, false, true) + "($$, v, _$);", 3));
    stb.append(format("val.push(v);", 3));
    stb.append(format("});", 2));
    stb.append(format("return val;", 2));
    stb.append(format("}", 1));

    return fname;
  }

  private String getQuery(String model, String filter, boolean json, boolean all) {

    if (model.contains(".")) {
      model = model.substring(model.lastIndexOf('.') + 1);
    }

    String nRecords = "fetchOne()";
    if (all) {
      nRecords = "fetch()";
    }

    String query = null;

    if (json) {
      query = "$json.all('" + model + "').by(" + filter + ")." + nRecords;
    } else {
      query =
          "__repo__("
              + model
              + ".class).all().filter(\""
              + filter
              + "\").bind(map).bind(_$)."
              + nRecords;
    }

    return query;
  }

  private String getSum(String value, String filter) {

    value = value.substring(0, value.length() - 1);
    String[] expr = value.split("\\.sum\\(");

    String fname = "setVar" + varCount;
    varCount += 1;

    StringBuilder stb = new StringBuilder();
    stb.append(format("", 1));
    stb.append(format("function " + fname + "(sumOf$, $$, filter){", 1));
    stb.append(format("var val  = 0", 2));
    stb.append(format("if (sumOf$ == null){ return val;}", 2));
    stb.append(format("sumOf$.forEach(function($){", 2));
    // stb.append(format("if ($ instanceof MetaJsonRecord){ $ =
    // $json.create($json.find($.id)); }",
    // 3));
    String val = "val += " + expr[1] + ";";
    if (filter != null) {
      val = "if(filter){" + val + "}";
    }
    stb.append(format(val, 3));
    stb.append(format("})", 2));
    stb.append(format("return new BigDecimal(val);", 2));
    stb.append(format("}", 1));

    fbuilder.add(stb);
    return fname + "(" + expr[0] + ",$," + filter + ")";
  }
}
