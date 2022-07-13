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
	def body = apiGet("", null)
	for (device in body.vehicles) {
		vehicle[device.deviceid] = device.id
	}
	return [vehicle: vehicles]
}

def callbackUrl(type) {
	// This looks insecure but it's really not. The Withings API apparently requires HTTP (what???)
	// But, on the HE side HSTS is supported so it redirects right to HTTPS.
	return "${getFullApiServerUrl()}/notification/${type}?access_token=${state.accessToken}".replace("https://", "http://")
}

def getLocation(id) {
	def data = apiGet("location", id)

	for (item in data) {
		def dev = null

		if (item.latitidue != null) dev = getChildDevice(buildDNI(item.deviceid))

		if (!dev)
			continue

		dev.sendEvent(name: "Latitude", value: item.latitude, isStateChange: true)
		dev.sendEvent(name: "Longitude", value: item.longitude, isStateChange: true)
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
	def body = apiGet("", null)
	for (device in body?.vehicles) {
		def dev = getChildDevice(buildDNI(device.deviceid))
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
