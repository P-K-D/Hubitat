/**
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/GE%20Z-Wave%20Plus%20Motion%20Dimmer%20Component/GE%20Z-Wave%20Plus%20Motion%20Dimmer%20Component.groovy
 *
 *  GE Z-Wave Plus Motion Dimmer Component
 *  Driver that exposes the dimmer and motion sensor part of a GE Motion Dimmer device asseparate child components
 *
 *  1.0.0 (08/25/2020) - First attempt at parent/child structure
 *  1.1.0 (08/27/2020) - Added more options to Debug Logging command and added startLevelChange and stopLevelChange commands
 *  1.1.1 (08/28/2020) - Missed setting type on one of the on/off events
 *  1.2.0 (08/30/2020)  - Made some states attributes, added refresh capability to parent
 *  1.2.1 (08/30/2020)  - Fixed Updated() not working correctly
 *  1.2.2 (08/31/2020)  - Fixed attributes not populating correctly on install
 *  1.2.3 (09/01/2020)  - Fixed redundant on/off events
 *  1.2.4 (10/17/2020)  - Added actuator capability so custom commands can be used in rule machine
 *  1.2.5 (10/27/2020)  - Fixed motion reset time parameter setting not working
 *  1.3.0 (02/17/2021)  - Removed erroneous duplicate event recording. Added new preference "Wait for device report before updating status."
 *  1.3.1 (02/17/2021)  - Added blank selection option to commands to reduce confusion
 *  1.3.2 (02/17/2021)  - Forgot to add the "Wait for device report" to everything other than on/off/level
 *  1.3.3 (03/31/2021)  - Fixed small issue where on/off states weren't made in rare situations
 *  1.3.4 (04/15/2021)  - Fixed defaultDimmerLevel state not populating
 *  1.3.5 (05/24/2021) - Fixed error when setting light timeout to disabled
 *  20210526 - removed special characters from occupancy status
*/

metadata {
	definition (name: "GE Z-Wave Plus Motion Dimmer Component", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Configuration"
		capability "Refresh"
		capability "Actuator"
		
		command "setDefaultDimmerLevel", [[name:"Default Dimmer Level",type:"NUMBER", description:"Default Dimmer Level Used when Turning ON. (0=Last Dimmer Value)", range: "0..99"]]
		command "setLightTimeout", [[name:"Light Timeout",type:"ENUM", description:"Time before light turns OFF on no motion - only applies in Occupancy and Vacancy modes.", constraints: ["", "5 seconds", "1 minute", "5 minutes (default)", "15 minutes", "30 minutes", "disabled"]]]
		command "Occupancy"
		command "Vacancy"
		command "Manual"
		command "DebugLogging", [[name:"Debug Logging",type:"ENUM", description:"Turn Debug Logging OFF/ON", constraints:["", "OFF", "30m", "1h", "3h", "6h", "12h", "24h", "ON"]]]        

		attribute "operatingMode", "string"
		attribute "defaultDimmerLevel", "number"
		attribute "lightTimeout", "string"
	
	}

	preferences {
		input "paramInverted", "enum", title: "Switch Buttons Direction", multiple: false, options: ["0" : "Normal (default)", "1" : "Inverted"], required: false, displayDuringSetup: true
		input "paramMotionEnabled", "enum", title: "Motion Sensor", description: "Enable/Disable Motion Sensor.", options: ["0" : "Disable","1" : "Enable (default)"], required: false
		input "paramMotionSensitivity", "enum", title: "Motion Sensitivity", options: ["1" : "High", "2" : "Medium (default)", "3" : "Low"], required: false, displayDuringSetup: true
		input "paramLightSense", "enum", title: "Light Sensing", description: "If enabled, Occupancy mode will only turn light on if it is dark", options: ["0" : "Disabled","1" : "Enabled (default)"], required: false, displayDuringSetup: true
		input "paramMotionResetTimer", "enum", title: "Motion Detection Reset Time", options: ["0" : "Disabled", "1" : "10 sec", "2" : "20 sec (default)", "3" : "30 sec", "4" : "45 sec", "110" : "27 mins"], required: false
		//
		input "paramZSteps", "number", title: "Z-Wave Dimming % Per Step", multiple: false, defaultValue: "1", range: "1..99", required: false, displayDuringSetup: true
		input "paramZDuration", "number", title: "Z-Wave Dimming Interval Between Steps (in 10ms increments)", multiple: false, defaultValue: "3", range: "1..255", required: false, displayDuringSetup: true
		input "paramPSteps", "number", title: "Physical Dimming % Per Step", multiple: false, defaultValue: "1", range: "1..99", required: false, displayDuringSetup: true
		input "paramPDuration", "number", title: "Physical Dimming Interval Between Steps (in 10ms increments)", multiple: false, defaultValue: "3", range: "1..255", required: false, displayDuringSetup: true
		//
		input "paramSwitchMode", "enum", title: "Switch Mode Enable", description: "Physical switch buttons only do ON/OFF - no dimming", multiple: false, options: ["0" : "Disable (default)", "1" : "Enable"], required: false, displayDuringSetup: true
		input "paramDimUpRate", "enum", title: "Speed to Dim up the light to the default level", multiple: false, options: ["0" : "Quickly (Default)", "1" : "Slowly"], required: false, displayDuringSetup: true
		//	 
		input (
			name: "requestedGroup2",
			title: "Association Group 2 Members (Max of 5):",
			type: "text",
			required: false
			)

		input (
			name: "requestedGroup3",
			title: "Association Group 3 Members (Max of 4):",
			type: "text",
			required: false
			)
		input name: "paramWait4Report", type: "bool", title: "Wait for device report before updating status. Can help prevent status sync issues, but will make hub initiated command status slightly slower.", defaultValue: false
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "logDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true	
	}
}
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Parse
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def parse(String description) {
	def result = null
	if (description != "updated") {
		if (logEnable) log.debug "parse() >> zwave.parse($description)"
		def cmd = zwave.parse(description) //, [0x20: 1, 0x25: 1, 0x56: 1, 0x70: 2, 0x72: 2, 0x85: 2])

		if (logEnable) log.debug "cmd: $cmd"
		
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	if (!result) {
		if (logEnable) log.debug "Parse returned ${result} for $description"
	} else {
		if (logEnable) log.debug "Parse returned ${result}"
	}
	
	return result
}
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Z-Wave Messages
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	if (logEnable) log.debug "zwaveEvent(): CRC-16 Encapsulation Command received: ${cmd}"

	def newVersion = 1
	
	// SwitchMultilevel = 38 decimal
	// Configuration = 112 decimal
	// Notification = 113 decimal
	// Manufacturer Specific = 114 decimal
	// Association = 133 decimal
	if (cmd.commandClass == 38) {newVersion = 3}
	if (cmd.commandClass == 112) {newVersion = 2}
	if (cmd.commandClass == 113) {newVersion = 3}
	if (cmd.commandClass == 114) {newVersion = 2}								 
	if (cmd.commandClass == 133) {newVersion = 2}		
	
	def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data, newVersion)
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	} else {
		log.warn "Unable to extract CRC16 command from ${cmd}"
	}
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
	log.debug "---BASIC REPORT V1--- ${device.displayName} sent ${cmd}"
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	if (logEnable) log.debug "---BASIC SET V1--- ${device.displayName} sent ${cmd}"

	/*
	def cd = fetchChild("Dimmer")
	String cv = ""

	if (cd) {
		cv = cd.currentValue("switch")
	} else {
		log.warn "In BasicSet no dimmer child found with fetchChild"
		return
	}
	
	List<Map> evts = []

	if (cmd.value == 255) {
		if (cv == "off") {
			evts.add([name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on", type: "physical"])
		}
	} else if (cmd.value == 0) {
		if (cv == "on") {
			evts.add([name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off", type: "physical"])
		}
	}
    		
	// Send events to child
	cd.parse(evts)
	*/
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	if (logEnable) log.debug "---ASSOCIATION REPORT V2--- ${device.displayName} sent groupingIdentifier: ${cmd.groupingIdentifier} maxNodesSupported: ${cmd.maxNodesSupported} nodeId: ${cmd.nodeId} reportsToFollow: ${cmd.reportsToFollow}"
	if (cmd.groupingIdentifier == 3) {
		if (cmd.nodeId.contains(zwaveHubNodeId)) {
			sendEvent(name: "numberOfButtons", value: 2, displayed: false)
		} else {
			sendEvent(name: "numberOfButtons", value: 0, displayed: false)
			delayBetween([
				zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: zwaveHubNodeId).format()
				,zwave.associationV2.associationGet(groupingIdentifier: 3).format()]
				,500)
		}
	}
}

def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	if (logEnable) log.debug "---CONFIGURATION REPORT V2--- ${device.displayName} sent ${cmd}"
	def config = cmd.scaledConfigurationValue.toInteger()
	def result = []
	def name = ""
	def value = ""
	def reportValue = config // cmd.configurationValue[0]
	switch (cmd.parameterNumber) {
		case 1:
			name = "lightTimeout"
			value = reportValue == 0 ? "5 seconds" : reportValue == 1 ? "1 minute" : reportValue == 5 ? "5 minutes (default)" : reportValue == 15 ? "15 minutes" : reportValue == 30 ? "30 minutes" : reportValue == -1 ? "disabled" : "error"
		    break
		case 3:
			name = "operatingMode"
			value = reportValue == 1 ? "Manual" : reportValue == 2 ? "Vacancy" : reportValue == 3 ? "Occupancy": "error"
			break
		case 5:
			name = "Invert Buttons"
			value = reportValue == 0 ? "Disabled (default)" : reportValue == 1 ? "Enabled" : "error"
			break
		case 6:
			name = "Motion Sensor"
			value = reportValue == 0 ? "Disabled" : reportValue == 1 ? "Enabled (default)" : "error"
			break
		case 7:
			name = "Z-Wave Dimming Number of Steps"
			value = reportValue
			break
		case 8:
			name = "Z-Wave Dimming Step Duration"
			value = reportValue
			break
		case 9:
			name = "Physical Dimming Number of Steps"
			value = reportValue
			break
		case 10:
			name = "Physical Dimming Step Duration"
			value = reportValue
			break
		case 13:
			name = "Motion Sensitivity"
			value = reportValue == 1 ? "High" : reportValue == 2 ? "Medium (default)" :  reportValue == 3 ? "Low" : "error"
			break
		case 14:
			name = "Light Sensing"
			value = reportValue == 0 ? "Disabled" : reportValue == 1 ? "Enabled (default)" : "error"
			break
		case 15:
			name = "Motion Reset Timer"
			value = reportValue == 0 ? "Disabled" : reportValue == 1 ? "10 seconds" : reportValue == 2 ? "20 seconds (default)" : reportValue == 3 ? "30 seconds" : reportValue == 4 ? "45 seconds" : reportValue == 110 ? "27 minutes" : "error"
			break
		case 16:
			name = "Switch Mode"
			value = reportValue == 0 ? "Disabled (default)" : reportValue == 1 ? "Enabled" : "error"
			break
		case 17:
			name = "defaultDimmerLevel"
			value = reportValue
			break
		case 18:
			name = "Dimming Rate"
			value = reportValue == 0 ? "Quickly (default)" : reportValue == 1 ? "Slowly" : "error"
			break
		default:
			break
	}
    
	sendEvent([name: name, value: value])
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	log.warn "*********** SwitchBinaryReport ************"
	log.warn "***** Please report to driver author ******"
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	log.debug "---MANUFACTURER SPECIFIC REPORT V2--- ${device.displayName} sent ${cmd}"
	log.debug "manufacturerId:   ${cmd.manufacturerId}"
	log.debug "manufacturerName: ${cmd.manufacturerName}"
	state.manufacturer=cmd.manufacturerName
	log.debug "productId:        ${cmd.productId}"
	log.debug "productTypeId:    ${cmd.productTypeId}"
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	updateDataValue("MSR", msr)	
	sendEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	updateDataValue("fw", fw)
	if (logEnable) log.debug "---VERSION REPORT V1--- ${device.displayName} is running firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
}

def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
	log.warn "Hail command received..."
}

def zwaveEvent(hubitat.zwave.Command cmd) {
	log.warn "${device.displayName} received unhandled command: ${cmd}"
}
def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd) {
	if (logEnable) log.debug "---NOTIFICATION REPORT V3--- ${device.displayName} sent ${cmd}"

	def cd = fetchChild("Motion Sensor")

	if (!cd) {
		log.warn "In NotificationReport no Motion Sensor child found with fetchChild"
		return
	}		

	List<Map> evts = []
		
	if (cmd.notificationType == 0x07) {
		if ((cmd.event == 0x00)) {
			//if (logDesc) log.info "$device.displayName motion has stopped"
			evts.add([name:"motion", value:"inactive", descriptionText:"${cd.displayName} motion inactive", type: "physical"])
			cd.parse(evts)
		} else if (cmd.event == 0x08) {
			//if (logDesc) log.info "$device.displayName detected motion"
			evts.add([name:"motion", value:"active", descriptionText:"${cd.displayName} motion active", type: "physical"])
			cd.parse(evts)  
		} 
	}
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
	if (logEnable) log.debug "SwitchMultilevelReport v3"
	
	def cd = fetchChild("Dimmer")
	String cv = ""

	if (cd) {
		cv = cd.currentValue("switch")
	} else {
		log.warn "In SwitchMultilevelReport no dimmer child found with fetchChild"
		return
	}
	
	List<Map> evts = []

    if (cmd.value) {
		if (cv == "off" || !cv) {
			//log.warn "In multilevel turning on"
			//log.warn "eventType: " + state.eventType
			evts.add([name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on", type: state.eventType ? "digital" : "physical"])
		}
		evts.add([name:"level", value: cmd.value, descriptionText:"${cd.displayName} level was set to ${cmd.value}%", unit: "%", type: state.eventType ? "digital" : "physical"])
		// Send events to child
		cd.parse(evts)
	} else {
		if (cv == "on" || !cv) {
			//log.warn "In multilevel turning off"
			//log.warn "eventType: " + state.eventType
			evts.add([name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off", type: state.eventType ? "digital" : "physical"])
			// Send events to child
			cd.parse(evts)
		}
	}
	//log.warn "setting state.eventType to zero in multilevel"
	state.eventType = 0
	//log.warn "eventType: " + state.eventType
}

def zwaveEvent(hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelSet cmd) {
	log.warn "SwitchMultilevelSet Called. This doesn't do anything right now in this driver."
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Component Child
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void componentRefresh(cd){
	if (logEnable) log.info "received refresh request from ${cd.displayName}"
	refresh()
}

void componentOn(cd){
	if (logEnable) log.info "received on request from ${cd.displayName}"
	//log.warn "componentOn setting state.eventType to -1"
	state.eventType = -1
	on(cd)
}

void componentOff(cd){
	if (logEnable) log.info "received off request from ${cd.displayName}"
	//log.warn "componentOff setting state.eventType to -1"
	state.eventType = -1
	off(cd)
}

void componentSetLevel(cd, level,transitionTime = null) {
	if (logEnable) log.info "received setLevel(${level}, ${transitionTime}) request from ${cd.displayName}"
	//log.warn "componentSetLevel setting state.eventType to -1"
	state.eventType = -1
	
	if (transitionTime == null) {
		setLevel(cd,level,0)
	} else {
		setLevel(cd,level,transitionTime)
	}       
}

void componentStartLevelChange(cd, direction) {
	if (logEnable) log.info "received startLevelChange(${direction}) request from ${cd.displayName}"
}

void componentStopLevelChange(cd) {
	if (logEnable) log.info "received stopLevelChange request from ${cd.displayName}"
}

def fetchChild(String type){
	String thisId = device.id
	def cd = getChildDevice("${thisId}-${type}")

	if (!cd) {
		log.warn "fetchChild - no child found for ${type}"
	}

	return cd 
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Driver Commands / Functions
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
void on(cd) {
	if (logEnable) log.debug "Turn device ON"

	List<Map> evts = []
	if (!paramWait4Report) {
		// Make child events
		evts.add([name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on", type: "digital"])

		// Send events to child
		getChildDevice(cd.deviceNetworkId).parse(evts)
	}
	
	def cmds = []
	cmds << zwave.basicV1.basicSet(value: 0xFF).format()
	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 3000), hubitat.device.Protocol.ZWAVE))
}

void off(cd) {
	if (logEnable) log.debug "Turn device OFF"

	List<Map> evts = []
	
	if (!paramWait4Report) {
		// Make child events
		evts.add([name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off", type: "digital"])

		// Send events to child
		getChildDevice(cd.deviceNetworkId).parse(evts)
	}
	
	def cmds = []
	cmds << zwave.basicV1.basicSet(value: 0x00).format()
	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 3000), hubitat.device.Protocol.ZWAVE))
}

void setLevel(cd, value, duration=null) {
	if (logEnable) log.debug "setLevel($value, $duration)"
	
	if (duration==null) {
		duration=0
	}

	def getStatusDelay = (duration * 1000 + 1000).toInteger()	

	value = Math.max(Math.min(value.toInteger(), 99), 0)

	String cv = cd.currentValue("switch")

	// Update getStatusDelay calc for OFF - it takes longer to report.
	if (value == 0 && cv == "on") {
		getStatusDelay += 2000
	}
	
	List<Map> evts = []
	if (!paramWait4Report) {
		// Create child events
		if (value) {
			evts.add([name:"level", value: value, descriptionText:"${cd.displayName} level was set to ${value}%", unit: "%", type: "digital"])
			if (cv == "off") {
				evts.add([name:"switch", value:"on", descriptionText:"${cd.displayName} was turned on", type: "digital"])
			}
		} else if (value == 0 && cv == "on") {
			evts.add([name:"switch", value:"off", descriptionText:"${cd.displayName} was turned off", type: "digital"])
		}

		// Send events to child
		getChildDevice(cd.deviceNetworkId).parse(evts)
	}
	
	if (logEnable) log.debug "setLevel(value, duration) >> value: ${value}, duration: ${duration}, delay: ${getStatusDelay}"
	
	def cmds = []
	cmds << zwave.switchMultilevelV2.switchMultilevelSet(value: value, dimmingDuration: duration).format()
	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, getStatusDelay), hubitat.device.Protocol.ZWAVE))
}

void setDefaultDimmerLevel(value) {
	if (logEnable) log.debug "Setting default dimmer level: ${value}"
	
	value = Math.max(Math.min(value.toInteger(), 99), 0)
	state.defaultDimmerLevel = value
	if (!paramWait4Report) {
		sendEvent([name:"defaultDimmerLevel", value: value, displayed:true])
	}
	
	def cmds = []
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: value , parameterNumber: 17, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 17).format()
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

def startLevelChange(direction) {
    def upDownVal = direction == "down" ? true : false
	def cd = fetchChild("Dimmer")

	if (!cd) {
		log.warn "In startLevelChange no dimmer child found with fetchChild"
		return
	}
	
	def cmds = []
	cmds << zwave.switchMultilevelV2.switchMultilevelStartLevelChange(ignoreStartLevel: true, startLevel: cd.currentValue("level"), upDown: upDownVal)
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

def stopLevelChange() {
    def cmds = []
	cmds << zwave.switchMultilevelV2.switchMultilevelStopLevelChange()
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

void setLightTimeout(value) {
	if (logEnable) log.debug "Setting light timeout value: ${value}"
	def cmds = []        
    
	// "5 seconds", "1 minute", "5 minutes (default)", "15 minutes", "30 minutes", "disabled"
	switch (value) {
		case "5 seconds":
			state.lightTimeout = "5 seconds"
			if (!paramWait4Report) {
					sendEvent([name:"lightTimeout", value: "5 seconds"])
			}
			cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 0 , parameterNumber: 1, size: 1).format()
			break
		case "1 minute":
			state.lightTimeout = "1 minute"
			if (!paramWait4Report) {
				sendEvent([name:"lightTimeout", value: "1 minute"])
			}
			cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 1 , parameterNumber: 1, size: 1).format()
			break
		case "5 minutes (default)":
			state.lightTimeout = "5 minutes (default)"
			if (!paramWait4Report) {
				sendEvent([name:"lightTimeout", value: "5 minutes (default)"])
			}
			cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 5 , parameterNumber: 1, size: 1).format()
			break
		case "15 minutes":
			state.lightTimeout = "15 minutes"
			if (!paramWait4Report) {
				sendEvent([name:"lightTimeout", value: "15 minutes"])
			}
			cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 15 , parameterNumber: 1, size: 1).format()
			break
		case "30 minutes":
			state.lightTimeout = "30 minutes"
			if (!paramWait4Report) {
				sendEvent([name:"lightTimeout", value: "30 minutes"])
			}
			cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 30 , parameterNumber: 1, size: 1).format()
			break
		case "disabled":
			state.lightTimeout = "disabled"
			if (!paramWait4Report) {
				sendEvent([name:"lightTimeout", value: "disabled"])
			}
			cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: 255 , parameterNumber: 1, size: 1).format()
			break
		default:
			return
	}
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 1).format()
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

void Occupancy() {
	state.operatingMode = "Occupancy"
	if (!paramWait4Report) {
		sendEvent([name:"operatingMode", value: "Occupancy", displayed:true])
	}
	def cmds = []
	cmds << zwave.configurationV2.configurationSet(configurationValue: [3] , parameterNumber: 3, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

void Vacancy() {
	state.operatingMode = "Vacancy"
	if (!paramWait4Report) {
		sendEvent([name:"operatingMode", value: "Vacancy", displayed:true])
	}
	def cmds = []
	cmds << zwave.configurationV2.configurationSet(configurationValue: [2] , parameterNumber: 3, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

void Manual() {
	state.operatingMode = "Manual"
	if (!paramWait4Report) {
		sendEvent([name:"operatingMode", value: "Manual", displayed:true])
	}
	def cmds = []
	cmds << zwave.configurationV2.configurationSet(configurationValue: [1] , parameterNumber: 3, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

void refresh() {
	log.info "refresh() is called"
	
	def cmds = []
	cmds << zwave.switchBinaryV1.switchBinaryGet().format()
	cmds << zwave.switchMultilevelV2.switchMultilevelGet().format()
	cmds << zwave.notificationV3.notificationGet(notificationType: 7).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 5).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 6).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 7).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 8).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 9).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 10).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 13).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 14).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 15).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 16).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 17).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 18).format()
	if (getDataValue("MSR") == null) {
		cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	}
	
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

void installed() {
	device.updateSetting("logEnable", [value: "true", type: "bool"])
	runIn(1800,logsOff)
	configure()
}

void updated() {
	log.info "updated..."
	log.warn "debug logging is: ${logEnable == true}"
	log.warn "description logging is: ${logDesc == true}"
	if (logEnable) runIn(1800,logsOff)

	if (state.lastUpdated && now() <= state.lastUpdated + 3000) return
	state.lastUpdated = now()

	// Set Wait preference
	if (paramWait4Report==null) {
		paramWait4Report = false
	}
	
	def cmds = []

	// See if Child Devices are Created, if not then create them
	String thisId = device.id
	
	def cd = getChildDevice("${thisId}-Dimmer")
	if (!cd) {
		cd = addChildDevice("hubitat", "Generic Component Dimmer", "${thisId}-Dimmer", [name: "${device.displayName} Dimmer", isComponent: true])
	}
	
	cd = getChildDevice("${thisId}-Motion Sensor")
	if (!cd) {
		cd = addChildDevice("hubitat", "Generic Component Motion Sensor", "${thisId}-Motion Sensor", [name: "${device.displayName} Motion Sensor", isComponent: true])
	}
	
    // Get lihgt timeout paraemter
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 1).format()
    
    // Get mode praameter
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
    
	// Set Inverted param
	if (paramInverted==null) {
		paramInverted = 0
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramInverted.toInteger(), parameterNumber: 5, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 5).format()

	// Set Motion Enabled param
	if (paramMotionEnabled==null) {
		paramMotionEnabled = 1
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramMotionEnabled.toInteger(), parameterNumber: 6, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 6).format()

	// Set Z Steps
	if (paramZSteps==null) {
		paramZSteps = 1
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramZSteps.toInteger(), parameterNumber: 7, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 7).format()
	
	// Set Z Duration
	if (paramZDuration==null) {
		paramZDuration = 3
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramZDuration.toInteger(), parameterNumber: 8, size: 2).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 8).format()
    
	// Set P Steps
	if (paramPSteps==null) {
		paramPSteps = 1
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramPSteps.toInteger(), parameterNumber: 9, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 9).format()
	
	// Set P Duration
	if (paramPDuration==null) {
		paramPDuration = 3
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramPDuration.toInteger(), parameterNumber: 10, size: 2).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 10).format()

	// Set Motion Sensitivity param
	if (paramMotionSensitivity==null) {
		paramMotionSensitivity = 2
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramMotionSensitivity.toInteger(), parameterNumber: 13, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 13).format()

	// Set Light Sense param
	if (paramLightSense==null) {
		paramLightSense = 1
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramLightSense.toInteger(), parameterNumber: 14, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 14).format()

	// Set Motion Reset Timer param
	if (paramMotionResetTimer==null) {
		paramMotionResetTimer = 2
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramMotionResetTimer.toInteger(), parameterNumber: 15, size: 2).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 15).format()

	// Set Switch Mode
	if (paramSwitchMode==null) {
		paramSwitchMode = 0
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramSwitchMode.toInteger(), parameterNumber: 16, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 16).format()

	// Set Dim Up Rate
	if (paramDimUpRate==null) {
		paramDimUpRate = 0
	}
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramDimUpRate.toInteger(), parameterNumber: 18, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 18).format()

	// Association groups
	cmds << zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format()
	cmds << zwave.associationV2.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId).format()
	cmds << zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId).format()
	
	// Add endpoints to groups 2 and 3
	def nodes = []
	if (settings.requestedGroup2 != state.currentGroup2) {
		nodes = parseAssocGroupList(settings.requestedGroup2, 2)
		cmds << zwave.associationV2.associationRemove(groupingIdentifier: 2, nodeId: []).format()
		cmds << zwave.associationV2.associationSet(groupingIdentifier: 2, nodeId: nodes).format()
		cmds << zwave.associationV2.associationGet(groupingIdentifier: 2).format()
		state.currentGroup2 = settings.requestedGroup2
	}
	
	if (settings.requestedGroup3 != state.currentGroup3) {
		nodes = parseAssocGroupList(settings.requestedGroup3, 3)
		cmds << zwave.associationV2.associationSetRemove(groupingIdentifier: 3, nodeId: []).format()
		cmds << zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: nodes).format()
		cmds << zwave.associationV2.associationGet(groupingIdentifier: 3).format()
		state.currentGroup3 = settings.requestedGroup3
	}    

	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
}

void configure() {
	log.info "configure triggered"
	
	def cmds = []
	cmds << zwave.associationV2.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format()
	cmds << zwave.associationV2.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId).format()
	cmds << zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId).format()
    
	sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 500), hubitat.device.Protocol.ZWAVE))
    
    refresh()
}

private parseAssocGroupList(list, group) {
	def nodes = group == 2 ? [] : [zwaveHubNodeId]
	if (list) {
		def nodeList = list.split(',')
		def max = group == 2 ? 5 : 4
		def count = 0

		nodeList.each { node ->
		    node = node.trim()
		
		    if ( count >= max) {
			    log.warn "Association Group ${group}: Number of members is greater than ${max}! The following member was discarded: ${node}"
		    } else if (node.matches("\\p{XDigit}+")) {
			    def nodeId = Integer.parseInt(node,16)
			    if (nodeId == zwaveHubNodeId) {
				    log.warn "Association Group ${group}: Adding the hub as an association is not allowed (it would break double-tap)."
			    } else if ( (nodeId > 0) & (nodeId < 256) ) {
				    nodes << nodeId
				    count++
			    } else {
				    log.warn "Association Group ${group}: Invalid member: ${node}"
			    }
		    } else {
			    log.warn "Association Group ${group}: Invalid member: ${node}"
		    }
		}
	}
	return nodes
}

void DebugLogging(value) {
	if (value=="OFF") {
		unschedule(logsOff)
		logsOff()
	} else

	if (value=="30m") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(1800,logsOff)		
	} else

	if (value=="1h") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(3600,logsOff)		
	} else

	if (value=="3h") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(10800,logsOff)		
	} else

	if (value=="6h") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(21699,logsOff)		
	} else

	if (value=="12h") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(43200,logsOff)		
	} else

	if (value=="24h") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
		runIn(86400,logsOff)		
	} else
		
	if (value=="ON") {
		unschedule(logsOff)
		log.debug "debug logging is enabled."
		device.updateSetting("logEnable",[value:"true",type:"bool"])
	}
}

void logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable",[value:"false",type:"bool"])
}
