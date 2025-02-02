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
package com.axelor.studio.db.repo;

import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaMenu;
import com.axelor.studio.db.StudioAction;
import com.axelor.studio.db.StudioMenu;
import com.axelor.studio.service.StudioMetaService;
import com.axelor.studio.service.builder.StudioMenuService;
import com.google.inject.Inject;

public class StudioMenuRepo extends StudioMenuRepository {

  @Inject private StudioMenuService studioMenuService;

  @Inject private StudioActionRepo studioActionRepo;

  @Inject private StudioMetaService metaService;

  @Override
  public StudioMenu save(StudioMenu studioMenu) {
    if (studioMenu.getStudioAction() != null) {
      studioMenu.getStudioAction().setMenuAction(true);
    }
    studioMenu = super.save(studioMenu);
    studioMenu.setMetaMenu(studioMenuService.build(studioMenu));
    return studioMenu;
  }

  @Override
  public StudioMenu copy(StudioMenu studioMenu, boolean deep) {

    StudioAction studioAction = studioMenu.getStudioAction();
    studioMenu.setStudioAction(null);

    studioMenu = super.copy(studioMenu, deep);

    if (studioAction != null) {
      studioMenu.setStudioAction(studioActionRepo.copy(studioAction, deep));
    }
    studioMenu.setMetaMenu(null);
    return studioMenu;
  }

  @Override
  public void remove(StudioMenu studioMenu) {
    MetaMenu metaMenu = studioMenu.getMetaMenu();
    studioMenu.setMetaMenu(null);

    if (metaMenu != null) {
      metaService.removeMetaMenu(metaMenu);
    }

    StudioAction studioAction = studioMenu.getStudioAction();

    studioMenu.setStudioAction(null);
    if (studioAction != null) {
      try {
        studioActionRepo.remove(studioAction);
      } catch (RuntimeException e) {
        throw e;
      }
    }

    MetaStore.clear();

    super.remove(studioMenu);
  }
}
