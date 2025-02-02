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
package com.axelor.studio.web;

import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.axelor.studio.db.WsAuthenticator;
import com.axelor.studio.db.WsConnector;
import com.axelor.studio.db.repo.WsAuthenticatorRepository;
import com.axelor.studio.db.repo.WsConnectorRepository;
import com.axelor.studio.service.ws.WsConnectorService;
import com.axelor.utils.ExceptionTool;
import com.axelor.utils.StringTool;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class WsConnectorController {

  @Inject protected WsConnectorService wsConnectorService;

  @SuppressWarnings({"rawtypes", "unchecked"})
  public void callConnector(ActionRequest request, ActionResponse response) {
    try {
      Context context = request.getContext();

      Object recordId = context.get("_recordId");
      Object recordModel = context.get("_recordModel");
      if (recordId == null || recordModel == null) {
        return;
      }
      WsAuthenticator authenticator = null;

      Long connectorId = Long.parseLong(((Map) context.get("connector")).get("id").toString());
      WsConnector wsConnector = Beans.get(WsConnectorRepository.class).find(connectorId);
      if (context.get("authenticator") != null) {
        Long authenticatorId =
            Long.parseLong(((Map) context.get("authenticator")).get("id").toString());
        authenticator = Beans.get(WsAuthenticatorRepository.class).find(authenticatorId);
      } else {
        authenticator = null;
      }

      Map<String, Object> ctx = new HashMap<>();
      Class<? extends Model> recordClass =
          (Class<? extends Model>) Class.forName(recordModel.toString());
      Model model = JPA.find(recordClass, Long.parseLong(recordId.toString()));
      ctx.put(StringTool.toFirstLower(model.getClass().getSimpleName()), model);
      ctx.put("_beans", Beans.class);
      Map<String, Object> res = wsConnectorService.callConnector(wsConnector, authenticator, ctx);
      StringBuilder result = new StringBuilder();
      for (var entry : res.entrySet()) {
        var key = entry.getKey();
        var value = entry.getValue();
        if (!key.startsWith("_") || key.equals("_beans")) continue;
        result.append(key).append(":\n");
        if (value instanceof byte[]) {
          result.append(new String((byte[]) value)).append("\n\n");
        } else {
          result.append(value).append("\n\n");
        }
      }
      response.setValue("$result", result.toString());
    } catch (Exception e) {
      ExceptionTool.trace(response, e);
    }
  }
}
