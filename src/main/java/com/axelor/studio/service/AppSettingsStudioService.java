package com.axelor.studio.service;

import com.axelor.message.service.AppSettingsMessageService;

public interface AppSettingsStudioService extends AppSettingsMessageService {

  public String appsToInstall();

  boolean importDemoData();

  String applicationLocale();

  String dataExportDir();

  String baseUrl();

  boolean multiTenancy();

  String surveyPublicUser();

  String surveyPublicPassword();
}
