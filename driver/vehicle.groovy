/**
 *
 *  Smartcar Vehicle
 *
 *  Copyright 2022 Jonathan Fields
 */

metadata {
  definition(name: "Smartcar Vehicle") {
    capability "Lock"
    capability "Battery"

        attribute "weight", "number"
        attribute "weightDisplay", "string"
        attribute "pulse", "number"
        attribute "fatFreeMass", "number"
        attribute "fatFreeMassDisplay", "string"
        attribute "fatRatio", "number"
        attribute "fatMassWeight", "number"
        attribute "fatMassWeightDisplay", "string"
        attribute "muscleMass", "number"
        attribute "muscleMassDisplay", "string"
        attribute "boneMass", "number"
        attribute "boneMassDisplay", "string"
        attribute "pulseWaveVelocity", "number"
    }
}
