/**
 *
 *  Smartcar Vehicle
 *
 *  Copyright 2022 Jonathan Fields
 */

metadata {
  definition(name: "Smartcar Vehicle", namespace: "fieldsjm.smartcar") {
    capability "Lock"
    
    attribute "Oil Life %", "number"
    attribute "EV Battery Capacity kwh", "number"
    attribute "EV Battery Level %", "number"
    attribute "EV Battery Range", "number"
    attribute "EV Battery Range Unit", "string"
    attribute "EV Battery Charge Status", "string"
    attribute "Fuel Range", "number"
    attribute "Fuel Range Unit", "string"
    attribute "Fuel Remaining %", "number"
    attribute "Fuel Remaining", "number"
    attribute "Fuel Remaining Unit", "string"
    attribute "Latitude", "string"
    attribute "Longitude", "string"
    attribute "Odometer", "number"
    attribute "Odometer", "string"
    attribute "Tire Pressure Unit", "string"
    attribute "Tire Pressure LF", "number"
    attribute "Tire Pressure RF", "number"
    attribute "Tire Pressure LR", "number"
    attribute "Tire Pressure RR", "number"
    
    command "getLocation"
    }
}

def lock() {
  app = getParent()
  app.action("lock")
}

def getLocation() {
  app = getParent()
  id = device.id
  app.getLocation(id)
}
