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

import com.axelor.i18n.I18n;
import com.axelor.message.db.Message;
import com.axelor.message.db.Template;
import com.axelor.message.db.repo.TemplateRepository;
import com.axelor.message.exception.MessageExceptionMessage;
import com.axelor.message.service.MessageService;
import com.axelor.message.service.TemplateMessageService;
import com.axelor.meta.CallMethod;
import com.axelor.meta.db.MetaAction;
import com.axelor.meta.db.MetaJsonModel;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaJsonModelRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionResponse;
import com.axelor.studio.db.StudioAction;
import com.axelor.studio.service.StudioMetaService;
import com.google.inject.Inject;
import javax.mail.MessagingException;

public class StudioActionEmailService {

  @Inject private MetaModelRepository metaModelRepo;

  @Inject private MetaJsonModelRepository metaJsonModelRepo;

  @Inject private StudioMetaService studioMetaService;

  @Inject private TemplateRepository templateRepo;

  @Inject private TemplateMessageService templateMessageService;

  @Inject private MessageService messageService;

  public MetaAction build(StudioAction studioAction) {
    String name = studioAction.getName();
    Object model =
        studioAction.getIsJson()
            ? metaJsonModelRepo.all().filter("self.name = ?", studioAction.getModel()).fetchOne()
            : metaModelRepo.all().filter("self.fullName = ?", studioAction.getModel()).fetchOne();

    int sendOption = studioAction.getEmailSendOptionSelect();
    Template template = studioAction.getEmailTemplate();

    String xml =
        "<action-method name=\""
            + name
            + "\" id=\""
            + studioAction.getXmlId()
            + "\">\n\t"
            + "<call class=\"com.axelor.studio.service.builder.StudioActionEmailService\" method=\"sendEmail(id, '"
            + (studioAction.getIsJson()
                ? ((MetaJsonModel) model).getName()
                : ((MetaModel) model).getFullName())
            + "', '"
            + (studioAction.getIsJson()
                ? ((MetaJsonModel) model).getName()
                : ((MetaModel) model).getName())
            + "', '"
            + template.getId()
            + "', '"
            + sendOption
            + "')\" "
            + "if=\"id != null\"/>\n"
            + "</action-method>";

    return studioMetaService.updateMetaAction(
        name, "action-method", xml, null, studioAction.getXmlId());
  }

  @CallMethod
  public ActionResponse sendEmail(
      Long objectId, String model, String tag, Long templateId, int sendOption)
      throws ClassNotFoundException, MessagingException {

    Template template = templateRepo.find(templateId);
    Message message = templateMessageService.generateMessage(objectId, model, tag, template);
    ActionResponse response = new ActionResponse();

    if (sendOption == 0) {
      messageService.sendByEmail(message);
    } else {
      response.setView(
          ActionView.define(I18n.get(MessageExceptionMessage.MESSAGE_3))
              .model(Message.class.getName())
              .add("form", "message-form")
              .param("forceEdit", "true")
              .context("_showRecord", message.getId().toString())
              .map());
    }
    return response;
  }
}
