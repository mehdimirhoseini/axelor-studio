import React from "react";
import TextField from "@material-ui/core/TextField";
import AutoComplete from "@material-ui/lab/Autocomplete";
import { makeStyles } from "@material-ui/core/styles";
import { camleCaseString, translate, getProperty } from "../../utils";
import { typeReplacer } from "../../constants";
import { TYPE } from "../../constants";
import { MODEL_TYPE } from "../../constants";

const useStyles = makeStyles({
	selection: {
		color: "#ffffff",
		backgroundColor: "#293846",
		".modern-dark &": {
			backgroundColor: "#323232",
		},
	},
	autoComplete: {
		width: "100%",
		marginTop: 15,
		backgroundColor: "#293846",
		"& > div > label": {
			color: "#ffffff !important",
			fontSize: 13,
		},
		".modern-dark &": {
			backgroundColor: "#323232",
		},
	},
	autoCompleteInput: {
		color: "#ffffff",
		fontSize: 13,
	},
	autoCompleteOption: {
		fontSize: 13,
		"& ul, .MuiAutocomplete-noOptions": {
			backgroundColor: "rgb(41, 56, 70) !important",
			".modern-dark &": {
				backgroundColor: "#1b1b1b !important",
			},
		},
		"& li, .MuiAutocomplete-noOptions": {
			color: "#ffffff !important",
			backgroundColor: "rgb(41, 56, 70) !important",
			".modern-dark &": {
				backgroundColor: "#1b1b1b !important",
			},
		},
		"& li:hover, .MuiAutocomplete-noOptions": {
			color: "#ffffff !important",
			backgroundColor: "#2f4050 !important",
			".modern-dark &": {
				backgroundColor: "#323232 !important",
			},
		},
	},

	disabled: {
		color: "#a3a3a3 !important",
		fontSize: 12,
	},
});

export default function StaticSelection(_props) {
	const { index } = _props;
	let {
		name,
		required,
		data,
		helper,
		title,
		color,
		disableClearable = false,
		parentField,
		parentPanel,
		getOptionLabel,
		...rest
	} = _props.field;
	const { props } = _props;
	const {
		propertyList,
		setPropertyList,
		type,
		onChange,
		modelType,
		metaFieldStore,
		editWidgetType,
	} = props || {};
	const classes = useStyles();

	if (typeof data === "object" && !Array.isArray(data)) {
		data = data[(typeReplacer[type] || type).toLowerCase()] || [];
	}
	title = translate(camleCaseString(title || name));

	const ColorTag = ({ color }) => (
		<div style={{ width: 15, height: 15, backgroundColor: color }}></div>
	);
	let value = propertyList[name] || "";
	if (
		(modelType === rest.modelType || props.editWidgetType === "customField") &&
		parentField
	) {
		const field = propertyList[parentField]
			? JSON.parse(propertyList[parentField])
			: {};
		value = field[name] || "";
	}
	let disabled = false;
	if (rest.isDisabled) {
		disabled = rest.isDisabled({
			properties: propertyList,
			metaFieldStore,
			editWidgetType,
			modelType,
		});
	}

	if (
		type === TYPE.panel &&
		modelType === MODEL_TYPE.CUSTOM &&
		name === "colSpan"
	) {
		return null;
	}
	return (
		<AutoComplete
			options={data}
			classes={{
				root: classes.selection,
				inputFocused: disabled ? classes.disabled : classes.autoCompleteInput,
				clearIndicator: classes.autoCompleteInput,
				popupIndicator: disabled ? classes.disabled : classes.autoCompleteInput,
				option: classes.autoCompleteOption,
				groupLabel: classes.autoCompleteOption,
				popper: classes.autoCompleteOption,
				noOptions: classes.autoCompleteOption,
			}}
			disableClearable={disableClearable}
			size="small"
			className={classes.autoComplete}
			autoHighlight
			disabled={disabled}
			onChange={(e, _value) => {
				const value = {
					...propertyList,
					...getProperty(
						name,
						_value,
						parentField,
						propertyList[parentField],
						modelType === rest.modelType ||
							props.editWidgetType === "customField"
					),
				};
				if (name === "colSpan") {
					value.colSpan = _value;
				}
				setPropertyList(value);
				onChange(value, name);
			}}
			getOptionLabel={(option) =>
				getOptionLabel
					? getOptionLabel(option)
					: typeof option === "object"
					? option.text
					: option
			}
			renderOption={(option) =>
				typeof option === "object" ? (
					<div key={index} value={option.value}>
						<i className={`fa ${option}`} style={{ marginRight: 4 }} />
						{translate(option.text)}
					</div>
				) : (
					<div key={index} value={option}>
						{color && <ColorTag color={option} />}
						<i className={`fa ${option}`} style={{ marginRight: 4 }} />
						<span>{translate(option)}</span>
					</div>
				)
			}
			value={value}
			renderInput={(params) => (
				<TextField
					{...params}
					label={translate(camleCaseString(title || name))}
					variant="outlined"
					classes={{
						root: classes.selection,
					}}
					autoComplete="off"
					inputProps={{
						...params.inputProps,
						autoComplete: "new-password", // disable autocomplete and autofill
					}}
				/>
			)}
		/>
	);
}
