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
package com.axelor.studio.bpm.service.dashboard;

import com.axelor.auth.db.User;
import com.axelor.common.Inflector;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaJsonModel;
import com.axelor.meta.db.MetaJsonRecord;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaJsonModelRepository;
import com.axelor.meta.db.repo.MetaJsonRecordRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.studio.bpm.service.execution.WkfInstanceService;
import com.axelor.studio.db.WkfModel;
import com.axelor.studio.db.WkfProcess;
import com.axelor.studio.db.WkfProcessConfig;
import com.axelor.studio.db.WkfTaskConfig;
import com.axelor.studio.db.repo.WkfProcessRepository;
import com.axelor.studio.db.repo.WkfTaskConfigRepository;
import com.axelor.utils.ExceptionTool;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

public class WkfDashboardCommonServiceImpl implements WkfDashboardCommonService {

  protected WkfProcessRepository wkfProcessRepo;
  protected WkfTaskConfigRepository wkfTaskConfigRepo;
  protected WkfInstanceService wkfInstanceService;
  protected MetaJsonRecordRepository metaJsonRecordRepo;
  protected MetaModelRepository metaModelRepo;

  @Inject
  public WkfDashboardCommonServiceImpl(
      WkfProcessRepository wkfProcessRepo,
      WkfTaskConfigRepository wkfTaskConfigRepo,
      WkfInstanceService wkfInstanceService,
      MetaJsonRecordRepository metaJsonRecordRepo,
      MetaModelRepository metaModelRepo) {
    this.wkfProcessRepo = wkfProcessRepo;
    this.wkfTaskConfigRepo = wkfTaskConfigRepo;
    this.wkfInstanceService = wkfInstanceService;
    this.metaJsonRecordRepo = metaJsonRecordRepo;
    this.metaModelRepo = metaModelRepo;
  }

  @Override
  public List<WkfProcess> findProcesses(WkfModel wkfModel, String processName) {
    if (processName == null) {
      return wkfProcessRepo
          .all()
          .filter("self.wkfModel.id = ?", wkfModel.getId())
          .order("-id")
          .fetch();
    }

    return wkfProcessRepo
        .all()
        .filter(
            "(self.name = ?1 OR self.description = ?1) AND self.wkfModel.id = ?2",
            processName,
            wkfModel.getId())
        .order("-id")
        .fetch();
  }

  @Override
  public boolean isAdmin(WkfModel wkfModel, User user) {
    boolean isAdminUser = wkfModel.getAdminUserSet().contains(user);
    boolean isAdminRole = wkfModel.getAdminRoleSet().stream().anyMatch(user.getRoles()::contains);

    return isAdminUser || isAdminRole;
  }

  @Override
  public boolean isManager(WkfModel wkfModel, User user) {
    boolean isManagerUser = wkfModel.getManagerUserSet().contains(user);
    boolean isManagerRole =
        wkfModel.getManagerRoleSet().stream().anyMatch(user.getRoles()::contains);

    return isManagerUser || isManagerRole;
  }

  @Override
  public boolean isUser(WkfModel wkfModel, User user) {
    boolean isUser = wkfModel.getUserSet().contains(user);
    boolean isUserRole = wkfModel.getRoleSet().stream().anyMatch(user.getRoles()::contains);

    return isUser || isUserRole;
  }

  @Override
  public ActionViewBuilder computeActionView(String status, String modelName, boolean isMetaModel) {
    ActionViewBuilder actionViewBuilder;

    if (isMetaModel) {
      MetaModel metaModel = Beans.get(MetaModelRepository.class).findByName(modelName);
      actionViewBuilder = this.createActionBuilder(status, metaModel);

    } else {
      MetaJsonModel metaJsonModel = Beans.get(MetaJsonModelRepository.class).findByName(modelName);
      actionViewBuilder = this.createActionBuilder(status, metaJsonModel);
    }

    return actionViewBuilder;
  }

  @Override
  public ActionViewBuilder createActionBuilder(String status, MetaJsonModel metaJsonModel) {

    String title = metaJsonModel.getTitle();
    if (Strings.isNullOrEmpty(status)) {
      title += "-" + status;
    }

    return ActionView.define(I18n.get(title))
        .model(MetaJsonRecord.class.getName())
        .add("grid", metaJsonModel.getGridView().getName())
        .add("form", metaJsonModel.getFormView().getName())
        .domain("self.jsonModel = :jsonModel AND self.id IN (:ids)")
        .context("jsonModel", metaJsonModel.getName());
  }

  @Override
  public ActionViewBuilder createActionBuilder(String status, MetaModel metaModel) {

    String name = metaModel.getName();
    String viewPrefix = Inflector.getInstance().dasherize(name);

    String title = Strings.isNullOrEmpty(status) ? name : name + "-" + status;

    return ActionView.define(title)
        .model(metaModel.getFullName())
        .add("grid", viewPrefix + "-grid")
        .add("form", viewPrefix + "-form")
        .domain("self.id IN (:ids)");
  }

  @Override
  public void sortProcessConfig(List<WkfProcessConfig> processConfigs) {
    processConfigs.sort(Comparator.comparing(WkfProcessConfig::getId));
  }

  @Override
  public Map<String, Object> computeStatus(
      boolean isMetaModel, String modelName, WkfProcess process, User user, String assignedType) {

    List<WkfTaskConfig> taskConfigs = findTaskConfigs(process, modelName, isMetaModel, user, true);

    Object[] obj = computeTaskConfig(taskConfigs, modelName, isMetaModel, user, true, assignedType);

    HashMap<String, Object> map = new HashMap<>();
    map.put("recordIdsPerModel", obj[0]);
    map.put("statuses", obj[1]);
    map.put("tasks", obj[2]);
    return map;
  }

  @Override
  public List<WkfTaskConfig> findTaskConfigs(
      WkfProcess process, String modelName, boolean isMetaModel, User user, boolean withTask) {

    String filter = "self.processId = :processId";

    if (user != null) {
      filter += " AND self.userPath IS NOT NULL";
    } else if (!withTask) {
      filter += " AND self.userPath IS NULL";
    }

    if (user != null) {
      filter +=
          isMetaModel
              ? " AND self.modelName = '" + modelName + "'"
              : " AND self.jsonModelName = '" + modelName + "'";
    }

    return wkfTaskConfigRepo.all().filter(filter).bind("processId", process.getProcessId()).fetch();
  }

  @Override
  public Object[] computeTaskConfig(
      List<WkfTaskConfig> taskConfigs,
      String modelName,
      boolean isMetaModel,
      User user,
      boolean withTask,
      String assignedType) {

    List<Map<String, Object>> statusList = new ArrayList<>();
    List<Long> recordIdsPerModel = new ArrayList<>();
    List<Map<String, Object>> taskCntMapList = new ArrayList<>();
    Map<String, Object> taskMap = new HashMap<>();

    taskConfigs.forEach(
        config -> {
          List<String> processInstanceIds =
              wkfInstanceService.findProcessInstanceByNode(
                  config.getName(), config.getProcessId(), config.getType(), false);

          List<Long> recordStatusIds =
              getStatusRecordIds(
                  config, processInstanceIds, modelName, isMetaModel, user, assignedType);

          if (withTask) {
            getTasks(
                config,
                processInstanceIds,
                modelName,
                isMetaModel,
                user,
                taskMap,
                taskCntMapList,
                assignedType);
          }

          if (CollectionUtils.isNotEmpty(recordStatusIds)) {
            recordIdsPerModel.addAll(recordStatusIds);
            Map<String, Object> map = new HashMap<>();
            String title =
                !StringUtils.isBlank(config.getDescription())
                    ? config.getDescription()
                    : config.getName();
            map.put("title", title);
            map.put("isMetaModel", isMetaModel);
            map.put("modelName", modelName);
            map.put("statusCount", recordStatusIds.size());
            map.put("statusRecordIds", recordStatusIds);
            statusList.add(map);
          }
        });

    if (withTask) {
      taskMap.put("isMetaModel", isMetaModel);
      taskMap.put("modelName", modelName);
      taskMap.put("taskCntMapList", taskCntMapList);
    }

    return new Object[] {recordIdsPerModel, statusList, taskMap};
  }

  @Override
  public List<Long> getStatusRecordIds(
      WkfTaskConfig config,
      List<String> processInstanceIds,
      String modelName,
      boolean isMetaModel,
      User user,
      String assignedType) {

    List<Long> recordIds = new ArrayList<>();

    if (!isMetaModel) {
      List<MetaJsonRecord> jsonModelrecords =
          getMetaJsonRecords(
              config, processInstanceIds, modelName, user, null, assignedType, LocalDate.now());

      recordIds.addAll(jsonModelrecords.stream().map(Model::getId).collect(Collectors.toList()));

    } else {
      List<Model> metaModelRecords =
          getMetaModelRecords(
              config, processInstanceIds, modelName, user, null, assignedType, LocalDate.now());

      recordIds.addAll(metaModelRecords.stream().map(Model::getId).collect(Collectors.toList()));
    }

    return recordIds;
  }

  @Override
  public List<MetaJsonRecord> getMetaJsonRecords(
      WkfTaskConfig config,
      List<String> processInstanceIds,
      String modelName,
      User user,
      String type,
      String assignedType,
      LocalDate toDate) {

    String filter =
        "self.processInstanceId IN (:processInstanceIds) AND self.jsonModel = :jsonModel";

    String userPath = config.getUserPath();
    if (user != null && assignedType != null) {
      if (Strings.isNullOrEmpty(userPath)) {
        return new ArrayList<>();
      }
      if (assignedType.equals(ASSIGNED_ME)) {
        filter += " AND self.attrs." + userPath + ".id = '" + user.getId() + "'";
      } else if (assignedType.equals(ASSIGNED_OTHER)) {
        filter +=
            " AND (self.attrs."
                + userPath
                + ".id = null OR self.attrs."
                + userPath
                + ".id != '"
                + user.getId()
                + "')";
      }
    }

    if (type != null) {
      String deadLinePath = config.getDeadlineFieldPath();
      if (Strings.isNullOrEmpty(deadLinePath)) {
        return new ArrayList<>();
      }
      filter += " AND self.attrs." + deadLinePath;
      switch (type) {
        case TASK_TODAY:
          filter += " = '" + toDate + "'";
          break;

        case TASK_NEXT:
          filter +=
              " > '"
                  + toDate
                  + "' AND self.attrs."
                  + deadLinePath
                  + " < '"
                  + toDate.plusDays(7)
                  + "'";
          break;

        case LATE_TASK:
          filter += " < '" + toDate + "'";
          break;
      }
    }

    return metaJsonRecordRepo
        .all()
        .filter(filter)
        .bind("processInstanceIds", processInstanceIds)
        .bind("jsonModel", modelName)
        .fetch();
  }

  @SuppressWarnings("unchecked")
  protected Object[] getMetaModelRecordFilter(
      WkfTaskConfig config, String modelName, User user, String assignedType) {

    MetaModel metaModel = metaModelRepo.findByName(modelName);
    String model = metaModel.getFullName();
    String filter = "self.processInstanceId IN (:processInstanceIds)";
    Class<Model> klass = null;
    try {
      klass = (Class<Model>) Class.forName(model);
    } catch (ClassNotFoundException e) {
      ExceptionTool.trace(e);
    }

    if (user != null && assignedType != null) {
      String path = config.getUserPath();
      Property property = Mapper.of(klass).getProperty(path.split("\\.")[0]);
      if (property == null) {
        if (assignedType.equals(ASSIGNED_ME)) {
          filter += " AND self.attrs." + path + ".id = '" + user.getId() + "'";
        } else if (assignedType.equals(ASSIGNED_OTHER)) {
          filter +=
              " AND (self.attrs."
                  + path
                  + ".id = null OR self.attrs."
                  + path
                  + ".id != '"
                  + user.getId()
                  + "')";
        }
      } else {
        if (assignedType.equals(ASSIGNED_ME)) {
          filter += " AND self." + path + ".id = " + user.getId();
        } else if (assignedType.equals(ASSIGNED_OTHER)) {
          filter +=
              " AND (self." + path + " IS NULL OR self." + path + ".id != " + user.getId() + ")";
        }
      }
    }
    return new Object[] {klass, filter};
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Model> getMetaModelRecords(
      WkfTaskConfig config,
      List<String> processInstanceIds,
      String modelName,
      User user,
      String type,
      String assignedType,
      LocalDate toDate) {

    Object[] obj = this.getMetaModelRecordFilter(config, modelName, user, assignedType);
    Class<Model> klass = (Class<Model>) obj[0];
    String filter = (String) obj[1];

    if (type != null) {
      String deadLinePath = config.getDeadlineFieldPath();
      if (Strings.isNullOrEmpty(deadLinePath)) {
        return new ArrayList<>();
      }
      Property property = Mapper.of(klass).getProperty(deadLinePath.split("\\.")[0]);
      if (property == null) {
        filter += " AND self.attrs." + deadLinePath;
        if (type.equals(TASK_NEXT)) {
          filter +=
              " > '"
                  + toDate
                  + "' AND self.attrs."
                  + deadLinePath
                  + " < '"
                  + toDate.plusDays(7)
                  + "'";
        }
      } else {
        filter += " AND self." + deadLinePath;
        if (type.equals(TASK_NEXT)) {
          filter +=
              " > '" + toDate + "' AND self." + deadLinePath + " < '" + toDate.plusDays(7) + "'";
        }
      }

      if (type.equals(TASK_TODAY)) {
        filter += " = '" + toDate + "'";
      } else if (type.equals(LATE_TASK)) {
        filter += " < '" + toDate + "'";
      }
    }

    return JPA.all(klass).filter(filter).bind("processInstanceIds", processInstanceIds).fetch();
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public void getTasks(
      WkfTaskConfig config,
      List<String> processInstanceIds,
      String modelName,
      boolean isMetaModel,
      User user,
      Map<String, Object> taskMap,
      List<Map<String, Object>> taskCntMapList,
      String assignedType) {

    List<Long> taskTodayIds = new ArrayList<>();
    List<Long> taskNextIds = new ArrayList<>();
    List<Long> lateTaskIds = new ArrayList<>();
    Map<String, Object> taskCntMap = new HashMap<>();

    if (!isMetaModel) {
      List<MetaJsonRecord> taskTodayList =
          getMetaJsonRecords(
              config,
              processInstanceIds,
              modelName,
              user,
              TASK_TODAY,
              assignedType,
              LocalDate.now());
      taskTodayIds.addAll(taskTodayList.stream().map(Model::getId).collect(Collectors.toList()));

      List<MetaJsonRecord> taskNextList =
          getMetaJsonRecords(
              config,
              processInstanceIds,
              modelName,
              user,
              TASK_NEXT,
              assignedType,
              LocalDate.now());
      taskNextIds.addAll(taskNextList.stream().map(Model::getId).collect(Collectors.toList()));

      List<MetaJsonRecord> lateTaskList =
          getMetaJsonRecords(
              config,
              processInstanceIds,
              modelName,
              user,
              LATE_TASK,
              assignedType,
              LocalDate.now());
      lateTaskIds.addAll(lateTaskList.stream().map(Model::getId).collect(Collectors.toList()));

      taskCntMap.put("otherTaskCnt", taskTodayList.size() + taskNextList.size());
      taskCntMap.put("lateTaskCnt", lateTaskList.size());

    } else {
      List<Model> taskTodayList =
          getMetaModelRecords(
              config,
              processInstanceIds,
              modelName,
              user,
              TASK_TODAY,
              assignedType,
              LocalDate.now());
      taskTodayIds.addAll(taskTodayList.stream().map(Model::getId).collect(Collectors.toList()));

      List<Model> taskNextList =
          getMetaModelRecords(
              config,
              processInstanceIds,
              modelName,
              user,
              TASK_NEXT,
              assignedType,
              LocalDate.now());
      taskNextIds.addAll(taskNextList.stream().map(Model::getId).collect(Collectors.toList()));

      List<Model> lateTaskList =
          getMetaModelRecords(
              config,
              processInstanceIds,
              modelName,
              user,
              LATE_TASK,
              assignedType,
              LocalDate.now());
      lateTaskIds.addAll(lateTaskList.stream().map(Model::getId).collect(Collectors.toList()));

      taskCntMap.put("otherTaskCnt", taskTodayList.size() + taskNextList.size());
      taskCntMap.put("lateTaskCnt", lateTaskList.size());
    }

    if ((!taskTodayIds.isEmpty() || !taskNextIds.isEmpty() || !lateTaskIds.isEmpty())
        && taskCntMapList != null) {
      taskCntMapList.add(taskCntMap);
    }

    if (taskMap.containsKey("taskTodayIds")) {
      taskTodayIds.addAll((List<Long>) taskMap.get("taskTodayIds"));
    }
    if (taskMap.containsKey("taskNextIds")) {
      taskNextIds.addAll((List<Long>) taskMap.get("taskNextIds"));
    }
    if (taskMap.containsKey("lateTaskIds")) {
      lateTaskIds.addAll((List<Long>) taskMap.get("lateTaskIds"));
    }

    taskMap.put("taskTodayIds", taskTodayIds);
    taskMap.put("taskNextIds", taskNextIds);
    taskMap.put("lateTaskIds", lateTaskIds);
  }
}
