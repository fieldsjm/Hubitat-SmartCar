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
    iconUrl: "https://smartcar.com/static/3a27faa94cb586130bfa2c033f863ae2/05c1b/black_logo.webp"
	documentationLink: "https://github.com/fieldsjm/hubitat-Smartcar/blob/master/README.md")

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
		
            // Use the temperature scale to guess the default value for the measurement system
            def defaultMeasurementSystem = 2
            if (getTemperatureScale() == "F")
                defaultMeasurementSystem = 1
			section("API Access"){
                paragraph "To connect to the Withings API you will need to obtain Client Id and Consumer Secret from Withings."
                paragraph "Check the box below to view instructions on how to obtain API access."
				input "showInstructions", "bool", title: "Show API Instructions", submitOnChange: true

                if (showInstructions) {
                    paragraph """<ul><li>Go to <a href="https://account.withings.com/partner/add_oauth2" target="_blank">https://account.withings.com/partner/add_oauth2</a></li>
                    <li>Enter a Description, Contact Email, and Company name</li>
                    <li>Enter <b>https://cloud.hubitat.com/oauth/stateredirect</b> for the Callback URL</li>
                    <li>Choose <b>Prod</b> for the environment</li>
                    <li>For the logo you can use <b>https://github.com/dcmeglio/hubitat-withings/raw/master/hubitat-logo.PNG</b></li>
                    <li>Copy both the <b>Client Id</b> and <b>Consumer Secret</b> into the boxes below</li></ul>"""
                }
			}
			section("General") {
                input "clientID", "text", title: "API Client ID", required: true
			    input "clientSecret", "text", title: "API Client Secret", required: true
			    input "measurementSystem", "enum", title: "Measurement System", options: [1: "Imperial", 2: "Metric", 3: "Imperial (UK)"], required: true, defaultValue: defaultMeasurementSystem
			    input "debugOutput", "bool", title: "Enable debug logging?", defaultValue: true
       			label title: "Enter a name for parent app (optional)", required: false
 			}
        if(state.appInstalled == 'COMPLETE'){
            section("Withings Users") {
				app(name: "withingsUsers", appName: "Withings User", namespace: "dcm.withings", title: "Add a new Withings User", multiple: true)
			}
			displayFooter()
		}
	}
}

def isInstalled() {
	state.appInstalled = app.getInstallationState() 
	if (state.appInstalled != 'COMPLETE') {
		section
		{
			paragraph "Please click <b>Done</b> to install the parent app. Afterwards reopen the app to add your Withings Users."
		}
  	}
}

def getOAuthDetails() {
    return [clientID: clientID, clientSecret: clientSecret]
}

def getMeasurementSystem() {
    return measurementSystem.toInteger()
}

def getDebugLogging() {
    return debugOutput ?: false
}

def displayFooter(){
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:#1A77C9;text-align:center'>Withings Integration<br><a href='https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=7LBRPJRLJSDDN&source=url' target='_blank'><img src='https://www.paypalobjects.com/webstatic/mktg/logo/pp_cc_mark_37x23.jpg' border='0' alt='PayPal Logo'></a><br><br>Please consider donating. This app took a lot of work to make.<br>If you find it valuable, I'd certainly appreciate it!</div>"
	}       
}

def getFormat(type, myText=""){			// Modified from @Stephack Code   
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}
