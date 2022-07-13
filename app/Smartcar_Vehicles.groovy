/**
 *
 *  Smartcar Integration
 *
 *  Copyright 2022 Jonathan Fields
 */

 import groovy.transform.Field
 
definition(
	name: "Smartcar Vehicles",
	author: "Jonathan Fields",
	description: "Integrate Smartcar into Hubitat.",
	category: "My Apps",
	parent: "Smartcar Integration"
	iconUrl: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo.png",
	iconX2Url: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo@2x.png",
	iconX3Url: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo@3x.png",
	documentationLink: "https://github.com/fieldsjm/Hubitat-Smartcar/blob/main/README.md")
	

preferences {
    page(name: "prefMain")
	page(name: "prefOAuth")
	page(name: "prefDevices")
}

@Field static def measurementSystems = [
	imperial: 1,
	metric: 2
]

def prefMain() {
	return dynamicPage(name: "prefMain", title: "Smartcar Integration", nextPage: "prefOAuth", uninstall:false, install: false) {
		section {
			input "vehicleName", "text", title: "The Vehicle Bame associated with this app", required: true
		}
	}
}
def prefOAuth() {
	return dynamicPage(name: "prefOAuth", title: "Withings OAuth", nextPage: "prefDevices", uninstall:false, install: false) {
		section {	
			def desc = ""
			if (!state.authToken) {
				showHideNextButton(false)
				desc = "To continue you will need to connect your Smartcar and Hubitat accounts"
			}
			else {
				showHideNextButton(true)
				desc = "Your Hubitat and Smartcar accounts are connected"
			}
			href url: oauthInitialize(), style: "external", required: true, title: "Smartcar Account Authorization", description: desc
		}
	}
}

def prefDevices() {
	app.updateLabel("${vehicleName}")
	state.devices = getSmartcarDevices() 
	return dynamicPage(name: "prefDevices", title: "Smartcar Vehicles", uninstall:true, install: true) {
		section {
			if (state.devices?.vehicles?.size() > 0)
				input "vehicles", "enum", title: "Vehicles", options: state.devices.vehicles, multiple: true}
		}
	}
}

mappings {
	path("/oauth/initialize") {
		action: [
			GET: "oauthInitialize"
		]
	}
	path("/callback") {
		action: [
			GET: "oauthCallback"
		]
	}
	path("/oauth/callback") {
		action: [
			GET: "oauthCallback"
		]
	}
}

// OAuth Routines
def oauthInitialize() {
	def oauthInfo = parent.getOAuthDetails()
	
	if (state.accessToken == null)
		createAccessToken()

	state.oauthState = "${getHubUID()}/apps/${app.id}/callback?access_token=${state.accessToken}"
	
	/*Compare to Postman*/
	return "https://account.withings.com/oauth2_user/authorize2?response_type=code&client_id=${oauthInfo.clientID}&scope=${URLEncoder.encode("user.info,user.activity,user.sleepevents,user.metrics")}&redirect_uri=${URLEncoder.encode("https://cloud.hubitat.com/oauth/stateredirect")}&state=${URLEncoder.encode(state.oauthState)}"
}

def oauthCallback() {
	if (params.state == state.oauthState) {
		def oauthInfo = parent.getOAuthDetails()
        try { 
            httpPost([
				uri: "https://wbsapi.withings.net",
				path: "/v2/oauth2",
				body: [
					"action": "requesttoken",
					"grant_type": "authorization_code",
					code: params.code,
					client_id : oauthInfo.clientID,
					client_secret: oauthInfo.clientSecret,
					redirect_uri: "https://cloud.hubitat.com/oauth/stateredirect"
				]
			]) { resp ->
    			if (resp && resp.data && resp.success) {
                    state.refreshToken = resp.data.body.refresh_token
                    state.authToken = resp.data.body.access_token
                    state.authTokenExpires = (now() + (resp.data.body.expires_in * 1000)) - 60000
					state.userid = resp.data.body.userid
                }
            }
		} 
		catch (e) {
            log.error "OAuth error: ${e}"
        }
	} 
	else {
		log.error "OAuth state does not match, possible spoofing?"
	}
	if (state.authToken) 
		oauthSuccess()
	else
		oauthFailure()
}

def oauthSuccess() {
	render contentType: 'text/html', data: """
	<p>Your Smartcar Account is now connected to Hubitat</p>
	<p>Close this window to continue setup.</p>
	"""
}

def oauthFailure() {
	render contentType: 'text/html', data: """
		<p>The connection could not be established!</p>
		<p>Close this window to try again.</p>
	"""
}

def refreshToken() {
	def result = false
	try {
		def oauthInfo = parent.getOAuthDetails()
		def params = [
			uri: "https://wbsapi.withings.net",
			path: "/v2/oauth2",
			body: [
				"action": "requesttoken",
				grant_type: "refresh_token",
				client_id: oauthInfo.clientID,
				client_secret: oauthInfo.clientSecret,
				refresh_token: state.refreshToken
			]
		]
		httpPost(params) { resp -> 
			if (resp && resp.data && resp.success) {
				state.refreshToken = resp.data.body.refresh_token
                state.authToken = resp.data.body.access_token
                state.authTokenExpires = now() + (resp.data.body.expires_in * 1000) - 60000
				result = true
			}
			else {
				state.authToken = null
				result = false
			}
		}
	}
	catch (e) {
		log.error "Failed to refresh token: ${e}"
		state.authToken = null
		result = false
	}
	return result
}

def getSmartcarDevices() {
	def vehicles = [:]
	def body = apiGet("vehicles", null)
	for (device in body.vehicles) {
		vehicle[device.deviceid] = device.model
		else if (device.type == "Sleep Monitor")
			sleepMonitors[device.deviceid] = device.model
		else if (device.type == "Activity Tracker")
			activityTrackers[device.deviceid] = device.model ?: "Unnamed Activity Tracker"
		else if (device.type == "Blood Pressure Monitor")
			bloodPressure[device.deviceid] = device.model
		else if (device.type == "Smart Connected Thermometer")
			thermometers[device.deviceid] = device.model
	}
	return [scales: scales, sleepMonitors: sleepMonitors, activityTrackers: activityTrackers, bloodPressure: bloodPressure, thermometers: thermometers]
}

def callbackUrl(type) {
	// This looks insecure but it's really not. The Withings API apparently requires HTTP (what???)
	// But, on the HE side HSTS is supported so it redirects right to HTTPS.
	return "${getFullApiServerUrl()}/notification/${type}?access_token=${state.accessToken}".replace("https://", "http://")
}

def processActivity(date) {
	def data = apiGet("v2/measure", "getactivity", [startdateymd: date, enddateymd: date, data_fields: "steps,distance,elevation,soft,moderate,intense,active,calories,totalcalories,hr_average,hr_min,hr_max,hr_zone_0,hr_zone_1,hr_zone_2,hr_zone_3"])?.activities

	for (item in data) {
		def dev = null

		if (item.deviceid != null)
			dev = getChildDevice(buildDNI(item.deviceid))
		else if (item.is_tracker)
			dev = getChildByCapability("StepSensor")

		if (!dev)
			continue

		dev.sendEvent(name: "steps", value: item.steps, isStateChange: true)
		dev.sendEvent(name: "distance", value: item.distance, isStateChange: true)
		dev.sendEvent(name: "elevation", value: item.elevation, isStateChange: true)
		dev.sendEvent(name: "soft", value: item.soft, isStateChange: true)
		dev.sendEvent(name: "moderate", value: item.moderate, isStateChange: true)
		dev.sendEvent(name: "intense", value: item.intense, isStateChange: true)
		dev.sendEvent(name: "active", value: item.active, isStateChange: true)
		dev.sendEvent(name: "calories", value: item.calories, isStateChange: true)
		dev.sendEvent(name: "totalCalories", value: item.totalcalories, isStateChange: true)
		dev.sendEvent(name: "heartRateAverage", value: item.hr_average, isStateChange: true)
		dev.sendEvent(name: "heartRateMin", value: item.hr_min, isStateChange: true)
		dev.sendEvent(name: "heartRateMax", value: item.hr_max, isStateChange: true)
		dev.sendEvent(name: "heartRateZone0", value: item.hr_zone_0, isStateChange: true)
		dev.sendEvent(name: "heartRateZone1", value: item.hr_zone_1, isStateChange: true)
		dev.sendEvent(name: "heartRateZone2", value: item.hr_zone_2, isStateChange: true)
		dev.sendEvent(name: "heartRateZone3", value: item.hr_zone_3, isStateChange: true)
	}
}

def sendEventsForMeasurements(dev, measurements, types) {
	for (measurement in measurements) {
		if (types.contains(measurement.type)) {
			def attrib = measures[measurement.type].attribute
			def displayAttrib = measures[measurement.type].displayAttribute
			def result = measures[measurement.type].converter.call(measurement.value, measurement.unit)
			dev.sendEvent(name: attrib, value: result.value, unit: result.unit, isStateChange: true)
			if (displayAttrib != null)
				dev.sendEvent(name: displayAttrib, value: result.displayValue, isStateChange: true)
		}
	}
}

def getChildByCapability(capability) {
	def childDevices = getChildDevices()
	for (dev in childDevices) {
		if (dev.hasCapability(capability))
			return dev
	}
	return null
}
// API call methods

def apiGet(endpoint, id) {
	logDebug "${endpoint}---${id}"
	def path = null
	if (id == null) {
		path = ""
	} else {
		path = "/" + id + "/" + endpoint
	}
		
	if (state.authTokenExpires <= now()) {
		if (!refreshToken())
			return null
	}
	def result = null
	try {
		def params = [
			uri: "https://api.smartcar.com/v2.0/vechicles",
			path: path,
			contentType: "application/json",
			headers: [
				"Authorization": "Bearer " + state.authToken
				"SC-Unit-System": + parent.getMeasurementSystem()
			]
		]
	}
	catch (e) {
		log.error "Error getting API data ${endpoint}---${id}: ${e}"
		result = null
	}
	return result
}

def installed() {
	initialize()
}

def uninstalled() {
	logDebug "uninstalling app"
	removeChildDevices(getChildDevices())
}

def updated() {	
    logDebug "Updated with settings: ${settings}"
	unschedule()
	initialize()
}

def initialize() {
	cleanupChildDevices()
	createChildDevices()
	schedule("0 */30 * * * ? *", refreshDevices)
}

def buildDNI(deviceid) {
	return "smartcar:${state.userid}:${deviceid}"
}

def createChildDevices() {
	for (vehicle in vehicles) {
            addChildDevice( "Smartcar Vehicle", buildDNI(scale), 1234, ["name": "${vehicle} ${state.devices.vehicle[vehicle]}", isComponent: false])
	}	
}

def cleanupChildDevices()
{
	for (device in getChildDevices())
	{
		def deviceId = device.deviceNetworkId.replace("smartcar:","")
		def allDevices = (vehicles ?: [])
		def deviceFound = false
		for (dev in allDevices)
		{
			if (state.userid + ":" + dev == deviceId)
			{
				deviceFound = true
				break
			}
		}

		if (deviceFound == true)
			continue
			
		deleteChildDevice(device.deviceNetworkId)
	}
}

private removeChildDevices(devices) {
	devices.each {
		deleteChildDevice(it.deviceNetworkId) // 'it' is default
	}
}

def refreshDevices() {
	def body = apiGet("v2/user", "getdevice")
	for (device in body?.devices) {
		def dev = getChildDevice(buildDNI(device.deviceid))
		if (dev != null) {
			if (device.type != "Sleep Monitor") {
				def intBattery = 30
				if (device.battery == "high")
					intBattery = 80
				else if (device.battery == "medium")
					intBattery = 50
				else if (device.battery == "low")
					intBattery = 20
				dev.sendEvent(name: "battery", value: intBattery)
			}
		}
	}
}

def showHideNextButton(show) {
	if(show) paragraph "<script>\$('button[name=\"_action_next\"]').show()</script>"  
	else paragraph "<script>\$('button[name=\"_action_next\"]').hide()</script>"
}

def logDebug(msg) {
    if (parent.getDebugLogging()) {
		log.debug msg
	}
}
