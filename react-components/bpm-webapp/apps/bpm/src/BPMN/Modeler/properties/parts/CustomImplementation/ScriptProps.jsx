import React, { useEffect, useState } from "react";
import classnames from "classnames";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  DialogContentText,
  Button,
  Tooltip,
} from "@material-ui/core";
import { makeStyles } from "@material-ui/core/styles";
import { is, getBusinessObject } from "bpmn-js/lib/util/ModelUtil";
import { Edit, NotInterested } from "@material-ui/icons";

import Select from "../../../../../components/Select";
import QueryBuilder from "../../../../../components/QueryBuilder";
import ConnectorBuilder from "../../../connector-builder";
import Mapper from "../../../../../components/Mapper";
import {
  getCustomModels,
  getMetaModels,
  getAllModels,
  getViews,
  fetchModels,
} from "../../../../../services/api";
import {
  TextField,
  Textbox,
  Checkbox,
} from "../../../../../components/properties/components";
import { translate, getBool } from "../../../../../utils";

const useStyles = makeStyles((theme) => ({
  groupLabel: {
    fontWeight: "bolder",
    display: "inline-block",
    verticalAlign: "middle",
    color: "#666",
    fontSize: "120%",
    margin: "10px 0px",
    transition: "margin 0.218s linear",
    fontStyle: "italic",
  },
  divider: {
    marginTop: 15,
    borderTop: "1px dotted #ccc",
  },
  label: {
    fontWeight: "bolder",
    display: "inline-block",
    verticalAlign: "middle",
    color: "#666",
    margin: "3px 0px",
  },
  expressionBuilder: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
  },
  newIcon: {
    color: "#58B423",
    marginLeft: 5,
  },
  new: {
    cursor: "pointer",
    marginTop: 18.6,
    display: "flex",
  },
  textbox: {
    width: "100%",
  },
  dialog: {
    minWidth: 300,
  },
  title: {
    fontSize: 18,
    fontWeight: "bold",
  },
  save: {
    margin: theme.spacing(1),
    backgroundColor: "#0275d8",
    borderColor: "#0267bf",
    textTransform: "none",
    color: "white",
    "&:hover": {
      backgroundColor: "#025aa5",
      borderColor: "#014682",
      color: "white",
    },
  },
}));

export default function ScriptProps({ element, index, label, bpmnModeler }) {
  const [isVisible, setVisible] = useState(false);
  const [open, setOpen] = React.useState(false);
  const [isQuery, setQuery] = useState(false);
  const [metaModel, setMetaModel] = useState(null);
  const [metaJsonModel, setMetaJsonModel] = useState(null);
  const [models, setModels] = useState([]);
  const [displayStatus, setDisplayStatus] = useState(true);
  const [defaultForm, setDefaultForm] = useState(null);
  const [formViews, setFormViews] = useState(null);
  const [isDefaultFormVisible, setDefaultFormVisible] = useState(false);
  const [isReadOnly, setReadOnly] = useState(false);
  const [openAlert, setAlert] = useState(false);
  const [openMapper, setMapper] = useState(false);
  const [alertTitle, setAlertTitle] = useState(null);
  const [alertMessage, setAlertMessage] = useState(null);
  const [isCustom, setIsCustom] = useState(false);
  const [isConnector, setConnector] = useState(false);
  const [openConnector, setOpenConnector] = useState(false);
  const classes = useStyles();

  const handleClickOpen = () => {
    setAlertMessage("Add all values");
    setMapper(false);
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
  };

  const handleMapperOpen = () => {
    setOpen(false);
    setMapper(true);
  };

  const handleCloseMapper = () => {
    setMapper(false);
  };

  const updateScript = ({ expr, exprMeta } = {}) => {
    element.businessObject.script = expr;
    element.businessObject.scriptFormat = "axelor";
    setProperty("scriptValue", exprMeta ? exprMeta : undefined);
  };

  const onSave = (expr) => {
    const { resultField, resultMetaField } = expr || {};
    element.businessObject.script = resultField;
    element.businessObject.scriptFormat = "axelor";
    setProperty("scriptValue", resultField ? resultMetaField : undefined);
    handleCloseMapper();
  };

  const getter = () => {
    const value = getProperty("scriptValue");
    const combinator = getBool(getProperty("scriptOperatorType") || false);
    const checked = getBool(getProperty("checked"));
    let values;
    if (value !== undefined) {
      try {
        values = JSON.parse(value);
        if (!values.length) {
          values = null;
        }
      } catch (errror) {}
    }
    let obj = { values: values, combinator, checked };
    return obj;
  };

  const setter = (val) => {
    const { expression, value, combinator, checked } = val;
    element.businessObject.script = expression;
    element.businessObject.scriptFormat = "axelor";
    if (expression === "" || expression === null || expression === undefined) {
      setProperty("scriptValue", undefined);
      setProperty("scriptOperatorType", undefined);
    }
    if (value) {
      (value || "").replace(/[\u200B-\u200D\uFEFF]/g, "");
      setProperty("scriptValue", value);
      setReadOnly(true);
    }
    setProperty("scriptOperatorType", combinator);
    setProperty("checked", checked);
  };

  const getExpression = () => {
    return {
      resultField:
        element && element.businessObject && element.businessObject.script,
      resultMetaField: getProperty("scriptValue"),
    };
  };

  function getBO(element) {
    if (
      element &&
      element.$parent &&
      element.$parent.$type !== "bpmn:Process"
    ) {
      return getBO(element.$parent);
    } else {
      return element.$parent;
    }
  }

  function getProcessConfig(type) {
    let bo = getBO(element && element.businessObject);
    if (element.type === "bpmn:Process") {
      bo = element.businessObject;
    }
    if (
      (element && element.businessObject && element.businessObject.$type) ===
      "bpmn:Participant"
    ) {
      bo =
        element && element.businessObject && element.businessObject.processRef;
    }
    const noOptions = {
      criteria: [
        {
          fieldName: "name",
          operator: "IN",
          value: [""],
        },
      ],
    };
    const extensionElements = bo && bo.extensionElements;
    if (!extensionElements || !extensionElements.values) return noOptions;
    const processConfigurations = extensionElements.values.find(
      (e) => e.$type === "camunda:ProcessConfiguration"
    );
    const metaModels = [],
      metaJsonModels = [];
    if (
      !processConfigurations &&
      !processConfigurations.processConfigurationParameters
    )
      return noOptions;
    processConfigurations.processConfigurationParameters.forEach((config) => {
      if (config.metaModel) {
        metaModels.push(config.metaModel);
      } else if (config.metaJsonModel) {
        metaJsonModels.push(config.metaJsonModel);
      }
    });

    let value = [];
    if (type === "metaModel") {
      value = [...metaModels];
    } else if (type === "metaJsonModel") {
      value = [...metaJsonModels];
    } else {
      value = [...metaModels, ...metaJsonModels];
    }
    const data = {
      criteria: [
        {
          fieldName: "name",
          operator: "IN",
          value: value && value.length > 0 ? value : [""],
        },
      ],
      operator: "or",
    };
    return data;
  }

  const setProperty = React.useCallback(
    (name, value) => {
      let bo = getBusinessObject(element);
      if ((element && element.type) === "bpmn:Participant") {
        bo = getBusinessObject(bo.processRef);
      }
      let propertyName = `camunda:${name}`;
      if (!bo) return;
      if (bo.$attrs) {
        bo.$attrs[propertyName] = value;
      } else {
        bo.$attrs = { [propertyName]: value };
      }
      if (value === undefined || value === "") {
        delete bo.$attrs[propertyName];
      }
    },
    [element]
  );

  const getProperty = React.useCallback(
    (name) => {
      let propertyName = `camunda:${name}`;
      let bo = getBusinessObject(element);
      if (is(element, "bpmn:Participant")) {
        bo = getBusinessObject(element).get("processRef");
      }
      return (bo && bo.$attrs && bo.$attrs[propertyName]) || "";
    },
    [element]
  );

  const getFormViews = React.useCallback(
    async (value, name) => {
      if (!value) return;
      const formViews = await getViews(
        name === "metaModel"
          ? { ...(value || {}), type: "metaModel" }
          : {
              ...(value || {}),
              type: "metaJsonModel",
            },
        []
      );
      setFormViews(formViews);
      if (formViews && (formViews.length === 1 || formViews.length === 0)) {
        setDefaultFormVisible(false);
        setProperty("defaultForm", formViews[0] && formViews[0]["name"]);
        return;
      }
      setDefaultFormVisible(true);
    },
    [setProperty]
  );

  const updateValue = (name, value, optionLabel = "name") => {
    if (!value) {
      setProperty(name, undefined);
      setProperty(`${name}ModelName`, undefined);
      setProperty("defaultForm", undefined);
      setDefaultForm(null);
      setDefaultFormVisible(false);
      return;
    }
    setProperty(name, value[optionLabel]);
    setProperty(`${name}ModelName`, value["fullName"] || value["name"]);
    getFormViews(value, name);
  };

  const updateSelectValue = (name, value, label, optionLabel = "name") => {
    updateValue(name, value, optionLabel);
    if (!value) {
      setProperty(`${name}Label`, undefined);
    }
    setProperty(`${name}Label`, label);
  };

  const addModels = (values) => {
    const displayOnModels = [],
      modelLabels = [];

    if (Array.isArray(values)) {
      if (values && values.length === 0) {
        setProperty("displayOnModels", undefined);
        setProperty(`displayOnModelLabels`, undefined);
        return;
      }
      values &&
        values.forEach((value) => {
          if (!value) {
            setProperty("displayOnModels", undefined);
            setProperty(`displayOnModelLabels`, undefined);
            return;
          }
          displayOnModels.push(value.name);
          modelLabels.push(value.title);
        });
    }
    if (displayOnModels.length > 0) {
      setProperty("displayOnModels", displayOnModels.toString());
      setProperty(`displayOnModelLabels`, modelLabels.toString());
    }
  };

  const getSelectValue = React.useCallback(
    (name) => {
      let label = getProperty(`${name}Label`);
      let newName = getProperty(name);
      if (newName) {
        let value = { name: newName };
        if (label) {
          value.title = label;
        }
        return value;
      } else {
        return null;
      }
    },
    [getProperty]
  );

  useEffect(() => {
    const metaModel = getSelectValue("metaModel");
    const metaModelName = getSelectValue("metaModelModelName");
    const metaJsonModel = getSelectValue("metaJsonModel");
    const displayOnModels = getProperty("displayOnModels");
    const displayOnModelLabels = getProperty("displayOnModelLabels");
    const displayStatus = getProperty("displayStatus");
    const isCustom = getProperty("isCustom");
    const defaultForm = getSelectValue("defaultForm");
    const isConnector = getProperty("connector");
    setDisplayStatus(getBool(displayStatus));
    setConnector(getBool(isConnector));
    setIsCustom(
      isCustom === undefined || isCustom === ""
        ? metaJsonModel
          ? true
          : false
        : getBool(isCustom)
    );
    setMetaModel(metaModel);
    setMetaJsonModel(metaJsonModel);
    setDefaultForm(defaultForm);
    const model = metaModel ? "metaModel" : "metaJsonModel";
    const value = metaModel
      ? { ...(metaModel || {}), fullName: metaModelName && metaModelName.name }
      : { ...(metaJsonModel || {}) };
    getFormViews(value, model);
    const models = [];
    if (displayOnModels) {
      const names = displayOnModels.split(",");
      const labels = displayOnModelLabels && displayOnModelLabels.split(",");
      names &&
        names.forEach((name, i) => {
          models.push({
            name: name,
            title: labels && labels[i],
          });
        });
      setModels(models);
    } else {
      setModels([]);
    }
  }, [getProperty, getSelectValue, getFormViews]);

  useEffect(() => {
    const query = getProperty("query") || false;
    const scriptValue = getProperty("scriptValue");
    const isConnector = getProperty("connector");

    setQuery(getBool(query));
    setReadOnly(
      (getBool(query) || scriptValue) && !getBool(isConnector) ? true : false
    );
  }, [getProperty]);

  useEffect(() => {
    if (is(element, "bpmn:ScriptTask")) {
      const bo = getBusinessObject(element);
      if (bo) {
        setVisible(true);
        element.businessObject.scriptFormat = "axelor";
      } else {
        setVisible(false);
      }
    } else {
      setVisible(false);
    }
  }, [element]);

  return (
    isVisible && (
      <div>
        <React.Fragment>
          {index > 0 && <div className={classes.divider} />}
        </React.Fragment>
        <div className={classes.groupLabel}>{translate(label)}</div>
        <Checkbox
          element={element}
          entry={{
            id: "query",
            label: translate("Query"),
            modelProperty: "query",
            widget: "checkbox",
            get: function () {
              return {
                query: isQuery,
              };
            },
            set: function (e, values) {
              let query = !values.query;
              element.businessObject.scriptFormat = "axelor";
              if (!query) {
                updateValue("metaModel", undefined);
                updateValue("metaJsonModel", undefined);
                setProperty("defaultForm", undefined);
                setProperty("displayStatus", undefined);
                setProperty("displayOnModels", undefined);
              }
              setProperty("query", query);
              setQuery(query);
              if (!query && getProperty("scriptValue")) {
                setReadOnly(true);
              } else {
                setReadOnly(query);
              }
            },
          }}
        />
        <Checkbox
          element={element}
          entry={{
            id: "connector",
            label: translate("Connector"),
            modelProperty: "connector",
            widget: "checkbox",
            get: function () {
              return {
                connector: isConnector,
              };
            },
            set: function (e, values) {
              let connector = !values.connector;
              if (connector === true) {
                setReadOnly(false);
              } else if (isQuery || getProperty("scriptValue")) {
                setReadOnly(true);
              }
              element.businessObject.scriptFormat = "axelor";
              setProperty("connector", connector);
              setConnector(connector);
            },
          }}
        />
        <div className={classes.expressionBuilder}>
          <Textbox
            element={element}
            className={classes.textbox}
            rows={3}
            readOnly={isReadOnly ? true : false}
            entry={{
              id: "script",
              label: translate("Script"),
              modelProperty: "script",
              get: function () {
                let bo = getBusinessObject(element);
                return {
                  script: ((bo && bo.get("script")) || "").replace(
                    /[\u200B-\u200D\uFEFF]/g,
                    ""
                  ),
                };
              },
              set: function (e, values) {
                if (element.businessObject) {
                  element.businessObject.script = values.script;
                  element.businessObject.scriptFormat = "axelor";
                  element.businessObject.resource = undefined;
                  setProperty("scriptOperatorType", undefined);
                  setProperty("scriptValue", undefined);
                }
              },
              validate: function (e, values) {
                if (!values.script) {
                  return { script: translate("Must provide a value") };
                }
              },
            }}
          />
          <div className={classes.new}>
            <Tooltip title="Enable" aria-label="enable">
              <NotInterested
                className={classes.newIcon}
                onClick={() => {
                  if (isReadOnly) {
                    setAlertMessage(
                      "Script can't be managed using builder once changed manually."
                    );
                    setAlertTitle("Warning");
                    setAlert(true);
                  }
                }}
              />
            </Tooltip>
            <Edit
              className={classes.newIcon}
              onClick={() => {
                isConnector
                  ? setOpenConnector(true)
                  : isQuery
                  ? handleClickOpen()
                  : handleMapperOpen();
              }}
            />
            {isConnector
              ? openConnector && (
                  <ConnectorBuilder
                    open={openConnector}
                    handleClose={() => {
                      setOpenConnector(false);
                      setReadOnly(false);
                    }}
                    updateScript={(val) => updateScript(val)}
                    getDefaultValues={() => getProperty("scriptValue")}
                  />
                )
              : isQuery
              ? open && (
                  <QueryBuilder
                    open={open}
                    close={handleClose}
                    type="bpmQuery"
                    title="Add query"
                    setProperty={setter}
                    getExpression={getter}
                    fetchModels={() => fetchModels(element)}
                  />
                )
              : openMapper && (
                  <Mapper
                    open={openMapper}
                    handleClose={handleCloseMapper}
                    onSave={(expr) => {
                      onSave(expr);
                      if (expr && expr.resultField) {
                        setReadOnly(true);
                      } else {
                        setReadOnly(false);
                      }
                    }}
                    params={() => getExpression()}
                    bpmnModeler={bpmnModeler}
                    element={element}
                  />
                )}
            {openAlert && (
              <Dialog
                open={openAlert}
                onClose={(event, reason) => {
                  if (reason !== "backdropClick") {
                    setAlert(false);
                  }
                }}
                aria-labelledby="alert-dialog-title"
                aria-describedby="alert-dialog-description"
                classes={{
                  paper: classes.dialog,
                }}
              >
                <DialogTitle id="alert-dialog-title">
                  <label className={classes.title}>
                    {translate(alertTitle)}
                  </label>
                </DialogTitle>
                <DialogContent>
                  <DialogContentText id="alert-dialog-description">
                    {translate(alertMessage)}
                  </DialogContentText>
                </DialogContent>
                <DialogActions>
                  <Button
                    onClick={() => {
                      setAlert(false);
                      setAlertMessage(null);
                      setAlertTitle(null);
                      setReadOnly(false);
                      if (element.businessObject) {
                        setProperty("scriptOperatorType", undefined);
                        setProperty("scriptValue", undefined);
                      }
                    }}
                    color="primary"
                    className={classes.save}
                  >
                    {translate("OK")}
                  </Button>
                  <Button
                    onClick={() => {
                      setAlert(false);
                    }}
                    color="primary"
                    className={classes.save}
                  >
                    {translate("Cancel")}
                  </Button>
                </DialogActions>
              </Dialog>
            )}
          </div>
        </div>
        {!isConnector && (
          <TextField
            element={element}
            entry={{
              id: "scriptResultVariable",
              label: translate("Result variable"),
              modelProperty: "scriptResultVariable",
              get: function () {
                let bo = getBusinessObject(element);
                let boResultVariable = bo && bo.get("camunda:resultVariable");
                return { scriptResultVariable: boResultVariable };
              },
              set: function (e, values) {
                if (element.businessObject) {
                  element.businessObject.resultVariable =
                    values.scriptResultVariable || undefined;
                }
              },
              validate: function (e, values) {
                if (!values.scriptResultVariable && isQuery) {
                  return {
                    scriptResultVariable: translate("Must provide a value"),
                  };
                }
              },
            }}
            canRemove={true}
          />
        )}

        {!isQuery && (
          <React.Fragment>
            <label className={classes.label}>{translate("Model")}</label>
            <Checkbox
              className={classes.checkbox}
              entry={{
                id: `custom-model`,
                modelProperty: "isCustom",
                label: translate("Custom"),
                get: function () {
                  return {
                    isCustom: isCustom,
                  };
                },
                set: function (e, values) {
                  const isCustom = !values.isCustom;
                  setIsCustom(isCustom);
                  setProperty("isCustom", isCustom);
                  setMetaJsonModel(undefined);
                  updateSelectValue("metaJsonModel", undefined);
                  setMetaModel(undefined);
                  updateSelectValue("metaModel", undefined);
                },
              }}
              element={element}
            />
            {isCustom ? (
              <Select
                className={classnames(classes.select, classes.metajsonModel)}
                fetchMethod={() =>
                  getCustomModels(getProcessConfig("metaJsonModel"))
                }
                update={(value, label) => {
                  setMetaJsonModel(value);
                  updateSelectValue("metaJsonModel", value, label);
                }}
                name="metaJsonModel"
                value={metaJsonModel}
                placeholder={translate("Custom model")}
                isLabel={false}
                optionLabel="name"
                optionLabelSecondary="title"
              />
            ) : (
              <Select
                className={classes.select}
                fetchMethod={() => getMetaModels(getProcessConfig("metaModel"))}
                update={(value, label) => {
                  setMetaModel(value);
                  updateSelectValue("metaModel", value, label);
                }}
                name="metaModel"
                optionLabel="name"
                optionLabelSecondary="title"
                value={metaModel}
                isLabel={false}
                placeholder={translate("Model")}
              />
            )}
            {isDefaultFormVisible && (
              <React.Fragment>
                <label className={classes.label}>
                  {translate("Default form")}
                </label>
                <Select
                  className={classes.select}
                  update={(value, label) => {
                    setDefaultForm(value);
                    setProperty("defaultForm", value ? value.name : undefined);
                    if (!value) {
                      setProperty(`defaultFormLabel`, undefined);
                    }
                    setProperty(`defaultFormLabel`, label);
                  }}
                  options={formViews}
                  name="defaultForm"
                  value={defaultForm}
                  label={translate("Default form")}
                  isLabel={false}
                />
              </React.Fragment>
            )}

            <div className={classes.container}>
              <Checkbox
                element={element}
                entry={{
                  id: "displayStatus",
                  label: translate("Display status"),
                  modelProperty: "displayStatus",
                  get: function () {
                    return {
                      displayStatus: displayStatus,
                    };
                  },
                  set: function (e, value) {
                    const displayStatus = !value.displayStatus;
                    setDisplayStatus(displayStatus);
                    setProperty("defaultForm", value ? value.name : undefined);
                    setProperty("displayStatus", displayStatus);
                    if (displayStatus === false) {
                      setModels([]);
                      addModels([]);
                    }
                  },
                }}
              />
              {displayStatus && (
                <React.Fragment>
                  <div className={classes.allModels}>
                    <label className={classes.label}>
                      {translate("Display on models")}
                    </label>
                    <Select
                      className={classes.select}
                      update={(value) => {
                        setModels(value);
                        addModels(value);
                      }}
                      fetchMethod={() => getAllModels(getProcessConfig())}
                      name="models"
                      value={models || []}
                      multiple={true}
                      isLabel={false}
                      optionLabel="name"
                      optionLabelSecondary="title"
                    />
                  </div>
                </React.Fragment>
              )}
            </div>
          </React.Fragment>
        )}
      </div>
    )
  );
}
