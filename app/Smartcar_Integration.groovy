/**
 *
 *  Smartcar Integration
 *
 *  Copyright 2022 Jonathan Fields
 */
 
import groovy.transform.Field

definition(
	name: "Smartcar Integration",
	author: "Jonathan Fields",
    namespace: "fieldsjm.smartcar",
	description: "Integrate Smartcar into Hubitat.",
	category: "My Apps",
	iconUrl: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo.png",
	iconX2Url: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo@2x.png",
	iconX3Url: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo@3x.png",
	documentationLink: "https://github.com/fieldsjm/Hubitat-Smartcar/blob/main/README.md")

preferences {
    page(name: "mainPage", title: "", install: true, uninstall: true)
	page(name: "prefOAuth")
	page(name: "prefDevices")
} 

def installed() {
    app.updateSetting("showInstructions", false)
}

def updated() {
    app.updateSetting("showInstructions", false)
}

def mainPage() {
    dynamicPage(name: "mainPage") {
        isInstalled()
        def defaultMeasurementSystem = 2
        section("API Access"){
            paragraph "To connect to the Smartcar API you will need to obtain Client Id and Client Secret from Smartcar."
            paragraph "Check the box below to view instructions on how to obtain API access."
            input "showInstructions", "bool", title: "Show API Instructions", submitOnChange: true
            
            if (showInstructions) {
                paragraph "<ul><li>Go to <a href='https://dashboard.smartcar.com/signup' target='_blank'>https://dashboard.smartcar.com/signup</a></li><li>Enter Name, Contact Email, and creat a password</li><li>Copy both the <b>Client Id</b> and <b>Client Secret</b> into the boxes below</li><li>Enter <b>https://cloud.hubitat.com/oauth/stateredirect</b> for the Callback URL</li></ul>"
                    }
        }
        section("General") {
            input "clientID", "text", title: "API Client ID", required: true
            input "clientSecret", "text", title: "API Client Secret", required: true
            input "measurementSystem", "enum", title: "Measurement System", options: [1: "imperial", 2: "metric"], required: true, defaultValue: defaultMeasurementSystem
            input "debugOutput", "bool", title: "Enable debug logging?", defaultValue: true
            label title: "Enter a name for parent app (optional)", required: false
        }
        if(state.appInstalled != 'COMPLETE'){
            section {
                paragraph "Please click <b>Done</b> to install the parent app. Afterwards reopen the app to add your Smartcar Vechicles."
            }
        } else {
            prefOAuth()
        }
    }
}

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
	
def oauthInitialize() {
	if (state.accessToken == null)
	createAccessToken()

	state.oauthState = "${getHubUID()}/apps/${app.id}/callback?access_token=${state.accessToken}"
		
	return "https://connect.smartcar.com/oauth/authorize?response_type=code&client_id=${clientID}&scope=read_vin read_odometer read_vehicle_info read_engine_oil read_battery read_charge read_fuel control_security read_tires required:read_location&redirect_uri=${URLEncoder.encode("https://cloud.hubitat.com/oauth/stateredirect")}&state=${URLEncoder.encode(state.oauthState)}"
}

def oauthCallback() {
	if (params.state == state.oauthState) {
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
	
def isInstalled() {
	state.appInstalled = app.getInstallationState()
}

def logDebug(msg) {
    if(debugOutput) log.debug msg
}	

def showHideNextButton(show) {
	if(show) paragraph "<script>\$('button[name=\"_action_next\"]').show()</script>"  
	else paragraph "<script>\$('button[name=\"_action_next\"]').hide()</script>"
}

def getFormat(type, myText=""){			// Modified from @Stephack Code   
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}
