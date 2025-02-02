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
package com.axelor.studio.bpm.listener;

import com.axelor.db.EntityHelper;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.event.Observes;
import com.axelor.events.PostAction;
import com.axelor.events.PostRequest;
import com.axelor.events.RequestEvent;
import com.axelor.events.internal.BeforeTransactionComplete;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaJsonRecord;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.axelor.studio.baml.tools.BpmTools;
import com.axelor.studio.bpm.context.WkfCache;
import com.axelor.studio.bpm.service.WkfDisplayService;
import com.axelor.studio.bpm.service.execution.WkfInstanceService;
import com.axelor.studio.db.WkfInstance;
import com.axelor.studio.db.repo.WkfInstanceRepository;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.persist.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.MultiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class WkfRequestListener {

  protected static final Logger log = LoggerFactory.getLogger(WkfRequestListener.class);

  public void onBeforeTransactionComplete(@Observes BeforeTransactionComplete event)
      throws Exception {

    String tenantId = BpmTools.getCurentTenant();
    if (!WkfCache.WKF_MODEL_CACHE.containsKey(tenantId)) {
      WkfCache.initWkfModelCache();
    }

    processUpdated(event, tenantId);
    processDeleted(event, tenantId);
  }

  private void processUpdated(BeforeTransactionComplete event, String tenantId) throws Exception {

    Set<? extends Model> updated = new HashSet<Model>(event.getUpdated());

    for (Model model : updated) {
      String modelName = EntityHelper.getEntityClass(model).getName();
      if (model instanceof MetaJsonRecord) {
        modelName = ((MetaJsonRecord) model).getJsonModel();
      }

      if (WkfCache.WKF_MODEL_CACHE.get(tenantId).containsValue(modelName)) {
        log.trace("Eval workflow from updated model: {}, id: {}", modelName, model.getId());
        Beans.get(WkfInstanceService.class).evalInstance(model, null);
      }
    }
  }

  @SuppressWarnings("unchecked")
  public void onRequest(@Observes PostAction postAction) throws Exception {

    Context context = postAction.getContext();

    if (context == null
        || postAction.getName().equals("com.axelor.meta.web.MetaController:moreAttrs")) {
      return;
    }

    String signal = (String) context.get("_signal");
    if (signal == null) {
      return;
    }

    Boolean wkfEvaluated = (Boolean) context.get("_wkfEvaluated");

    if (wkfEvaluated != null && wkfEvaluated) {
      return;
    }

    String tenantId = BpmTools.getCurentTenant();

    if (!WkfCache.WKF_MODEL_CACHE.containsKey(tenantId)) {
      WkfCache.initWkfModelCache();
    }

    Map<Long, String> modelMap = WkfCache.WKF_MODEL_CACHE.get(tenantId);

    Class<? extends Model> model = (Class<? extends Model>) context.getContextClass();

    String modelName = model.getName();

    if (model.equals(MetaJsonRecord.class)) {
      modelName = (String) context.get("jsonModel");
    }

    if (modelMap != null && modelMap.containsValue(modelName)) {
      Long id = (Long) context.get("id");
      if (!WkfCache.WKF_BUTTON_CACHE.containsKey(tenantId)) {
        WkfCache.initWkfButttonCache();
      }
      MultiMap multiMap = WkfCache.WKF_BUTTON_CACHE.get(tenantId);

      if (multiMap != null && multiMap.containsValue(signal) && id != null) {
        Object res = postAction.getResult();
        log.trace("Wkf button cache: {}", WkfCache.WKF_BUTTON_CACHE);
        log.trace("Eval wkf from button model: {}, id: {}", model.getName(), id);
        String helpText =
            Beans.get(WkfInstanceService.class).evalInstance(JPA.find(model, id), signal);

        if (res instanceof ActionResponse && helpText != null) {
          ((ActionResponse) res).setAlert(helpText);
        }
      }
    }
    context.put("_wkfEvaluated", true);
  }

  @SuppressWarnings("all")
  public void onFetch(@Observes @Named(RequestEvent.FETCH) PostRequest event) {

    Object obj = event.getResponse().getItem(0);

    if (obj instanceof Map) {
      Map values = (Map) obj;
      if (values != null && values.get("id") != null) {

        List<Map<String, Object>> wkfStatus =
            Beans.get(WkfDisplayService.class)
                .getWkfStatus(
                    event.getRequest().getBeanClass(), Long.parseLong(values.get("id").toString()));
        if (wkfStatus.isEmpty()) {
          wkfStatus = null;
        }
        values.put("$wkfStatus", wkfStatus);
      }
    }
  }

  @Transactional
  public void processDeleted(BeforeTransactionComplete event, String tenantId) {

    Set<? extends Model> deleted = new HashSet<Model>(event.getDeleted());

    WkfInstanceRepository wkfInstanceRepository = Beans.get(WkfInstanceRepository.class);

    for (Model model : deleted) {
      String modelName = EntityHelper.getEntityClass(model).getName();
      if (model instanceof MetaJsonRecord) {
        modelName = ((MetaJsonRecord) model).getJsonModel();
      }

      if (WkfCache.WKF_MODEL_CACHE.get(tenantId).containsValue(modelName)) {
        log.trace("Remove wkf instance of deleted model: {}, id: {}", modelName, model.getId());
        WkfInstance wkfInstance =
            wkfInstanceRepository.findByInstanceId(model.getProcessInstanceId());
        if (wkfInstance != null
            && wkfInstance.getWkfProcess().getWkfProcessConfigList().size() == 1) {
          wkfInstanceRepository.remove(wkfInstance);
        }
      }
    }
  }
}
