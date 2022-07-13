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
	description: "Integrate Smartcar into Hubitat.",
	category: "My Apps",
	iconUrl: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo.png",
	iconX2Url: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo@2x.png",
	iconX3Url: "https://raw.githubusercontent.com/fieldsjm/Hubitat-Smartcar/main/resources/smartcar_logo@3x.png",
	documentationLink: "https://github.com/fieldsjm/Hubitat-Smartcar/blob/main/README.md")

preferences {
     page(name: "mainPage", title: "", install: true, uninstall: true)
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
                    paragraph """<ul><li>Go to <a href="https://dashboard.smartcar.com/signup" target="_blank">https://dashboard.smartcar.com/signup</a></li>
                    <li>Enter Name, Contact Email, and creat a password</li>
		    <li>Copy both the <b>Client Id</b> and <b>Consumer Secret</b> into the boxes below</li>
                    <li>Enter <b>https://cloud.hubitat.com/oauth/stateredirect</b> for the Callback URL</li></ul>"""
                }
			}
			section("General") {
				input "clientID", "text", title: "API Client ID", required: true
				input "clientSecret", "text", title: "API Client Secret", required: true
				input "measurementSystem", "enum", title: "Measurement System", options: [1: "Imperial", 2: "Metric"], required: true, defaultValue: defaultMeasurementSystem
				input "debugOutput", "bool", title: "Enable debug logging?", defaultValue: true
				label title: "Enter a name for parent app (optional)", required: false
			}
	    if(state.appInstalled == 'COMPLETE'){
		    section("Smartcar Vehicles") {
				app(name: "Smartcar_Vehicles", appName: "Smartcar Vehicles", title: "Add a new Smartcar Vehicle", multiple: true)
			}
	}
}

def isInstalled() {
	state.appInstalled = app.getInstallationState() 
	if (state.appInstalled != 'COMPLETE') {
		section
		{
			paragraph "Please click <b>Done</b> to install the parent app. Afterwards reopen the app to add your Smartcar Vechicles."
		}
  	}
}

def getOAuthDetails() {
    return [clientID: clientID, clientSecret: clientSecret]
}

def getDebugLogging() {
    return debugOutput ?: false
}

def getFormat(type, myText=""){			// Modified from @Stephack Code   
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}
