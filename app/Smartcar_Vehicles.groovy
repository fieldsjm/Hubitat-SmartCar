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
	namespace: "jmf.smartcar",
	description: "Integrate Smartcar into Hubitat.",
	category: "My Apps",
	parent: "Smartcar Integration"
	iconUrl: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo.png",
	iconX2Url: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo@2x.png",
	iconX3Url: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo@3x.png",
	documentationLink: "https://github.com/fieldsjm/Hubitat-Smartcar/blob/main/README.md")

preferences {
	page(name: "prefOAuth")
	page(name: "prefDevices")
}

@Field static def measurementSystems = [
	imperial: 1,
	metric: 2
]
def prefOAuth() {
    return dynamicPage(name: "prefOAuth", title: "Smartcar OAuth", nextPage: "prefDevices", uninstall:false, install: false) {
        section {
            def desc = ""
            if (!state.authToken) {
                showHideNextButton(false)
                desc = "To continue you will need to connect your Smartcar and Hubitat accounts"
            } else {
                showHideNextButton(true)
                desc = "Your Hubitat and Smartcar accounts are connected"
            }
            href url: oauthInitialize(), style: "external", required: true, title: "Smartcar Account Authorization", description: desc
        }
    }
}

def prefDevices() {
	state.devices = getSmartcars() 
	return dynamicPage(name: "prefDevices", title: "Smartcar Vehicles", uninstall:true, install: true) {
        section {
            if (state.devices?.vehicles?.size() > 0) input "vehicles", "enum", title: "Vehicles", options: state.devices.vehicles, multiple: true
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
	path("/notification/:type") {
		action: [
			GET: "withingsNotification",
			POST: "withingsNotification"
		]
	}
}

// OAuth Routines
def oauthInitialize() {
	def oauthInfo = parent.getOAuthDetails()
	
	if (state.accessToken == null)
		createAccessToken()

	state.oauthState = "${getHubUID()}/apps/${app.id}/callback?access_token=${state.accessToken}"
		
	return "https://account.withings.com/oauth2_user/authorize2?response_type=code&client_id=${oauthInfo.clientID}&scope=${URLEncoder.encode("user.info,user.activity,user.sleepevents,user.metrics")}&redirect_uri=${URLEncoder.encode("https://cloud.hubitat.com/oauth/stateredirect")}&state=${URLEncoder.encode(state.oauthState)}"
}

def oauthCallback() {
	if (params.state == state.oauthState) {
		def oauthInfo = parent.getOAuthDetails()
try { 
            httpPost([
				uri: "https://auth.smartcar.com",
				path: "/oauth/token",
		    
				body: [
					"grant_type": "authorization_code",
					code: params.code,
					client_id : clientID,
					client_secret: clientSecret,
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
	} else {
        log.error "OAuth state does not match, possible spoofing?"
    }
    if (state.authToken) {
        oauthSuccess()
    } else {
        oauthFailure()
    }
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
		def params = [
			uri: "https://auth.smartcar.com/",
			path: "/oauth/token",
			body: [
				grant_type: "refresh_token",
				client_id: clientID,
				client_secret: clientSecret,
				refresh_token: state.refreshToken
			]
		]
		httpPost(params) { resp -> 
			if (resp && resp.data && resp.success) {
				state.refreshToken = resp.data.body.refresh_token
                state.authToken = resp.data.body.access_token
                state.authTokenExpires = now() + (resp.data.body.expires_in * 1000) - 60000
				result = true
			} else {
                state.authToken = null
                result = false
			}
		}
	} catch (e) {
		log.error "Failed to refresh token: ${e}"
		state.authToken = null
		result = false
    }
    return result
}

def getSmartcars() {
	def vehicles = apiGetVehicles()
	log.info vehicles
}

def apiGetVehicles() {
    if (state.authTokenExpires <= now()) {
        if (!refreshToken())
        return null
    }
    def result = null
    try {
        def params = [
			uri: "https://api.smartcar.com",
			path: "/v2.0/vehicles",
			contentType: "application/json",
			headers: [
				"Authorization": "Bearer " + state.authToken
			]
		]
		if (query != null)
			params.query << query
		httpGet(params) { resp ->
            if (resp.data.status == 0) {
                result = resp.data.body
            } else if (resp.data.status == 401) {
                refreshToken()
            }
		}
	} catch (e) {
        log.error "Error getting API data for vehicles: ${e}"
        result = null
    }
    return result
}

def installed() {
	initialize()
}

def uninstalled() {
	logDebug "uninstalling app"
	unsubscribeWithingsNotifications()
	removeChildDevices(getChildDevices())
}

def updated() {	
    logDebug "Updated with settings: ${settings}"
	unschedule()
    unsubscribe()
	initialize()
}

def initialize() {
	cleanupChildDevices()
	createChildDevices()
	updateSubscriptions()
	schedule("0 */30 * * * ? *", refreshDevices)
}

def buildDNI(deviceid) {
	return "withings:${state.userid}:${deviceid}"
}

def createChildDevices() {
	for (scale in scales)
	{
		if (!getChildDevice(buildDNI(scale)))
            addChildDevice("dcm.withings", "Withings Scale", buildDNI(scale), 1234, ["name": "${userName} ${state.devices.scales[scale]}", isComponent: false])
	}
	for (sleepMonitor in sleepMonitors)
	{
		if (!getChildDevice(buildDNI(sleepMonitor)))
            addChildDevice("dcm.withings", "Withings Sleep Sensor", buildDNI(sleepMonitor), 1234, ["name": "${userName} ${state.devices.sleepMonitors[sleepMonitor]}", isComponent: false])
	}
	for (activityTracker in activityTrackers)
	{
		if (!getChildDevice(buildDNI(activityTracker)))
            addChildDevice("dcm.withings", "Withings Activity Tracker", buildDNI(activityTracker), 1234, ["name": "${userName} ${state.devices.activityTrackers[activityTracker]}", isComponent: false])
	}
	for (bp in bloodPressure)
	{
		if (!getChildDevice(buildDNI(bp)))
            addChildDevice("dcm.withings", "Withings Blood Pressure Monitor", buildDNI(bp), 1234, ["name": "${userName} ${state.devices.bloodPressure[bp]}", isComponent: false])
	}
	for (thermometer in thermometers)
	{
		if (!getChildDevice(buildDNI(thermometer)))
            addChildDevice("dcm.withings", "Withings Thermometer", buildDNI(thermometer), 1234, ["name": "${userName} ${state.devices.thermometers[thermometer]}", isComponent: false])
	}
}

def cleanupChildDevices()
{
	for (device in getChildDevices())
	{
		def deviceId = device.deviceNetworkId.replace("withings:","")
		def allDevices = (scales ?: []) + (sleepMonitors ?: []) + (activityTrackers ?: []) + (bloodPressure ?: []) + (thermometers ?: [])
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
