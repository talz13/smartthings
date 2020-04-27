/**
 *  Wireless Tag Lux
 *
 *  Copyright 2014 Dave Swanson (swanny)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
    definition (name: 'Wireless Tag Lux', namespace: 'swanny', author: 'swanny') {
        capability 'Water Sensor'
        capability 'Relative Humidity Measurement'
        capability 'Temperature Measurement'
        capability 'Illuminance Measurement'
        capability 'Battery'
        capability 'Refresh'
        capability 'Polling'
        capability 'Sensor'

        command 'generateEvent'
        command 'initialSetup'

        attribute 'tagType', 'string'
    }
}

// parse events into attributes
void parse(String description) {
    log.debug "Parsing '${description}'"
}

// handle commands
void poll() {
    log.debug 'poll'
    parent.pollChild(this)
}

void refresh() {
    log.debug 'refresh'
    parent.refreshChild(this)
}

void initialSetup() {
}

void generateEvent(Map results) {
    log.debug "parsing data $results"
    if (results) {
        results.each { name, value ->
            boolean isDisplayed = true

            if (name == 'temperature') {
                double tempValue = getTemperature(value)
                // def isChange = isTemperatureStateChange(device, name, tempValue.toString())
                isDisplayed = isChange
                if (device.currentState(name)?.value != tempValue) {
                    sendEvent(name: name, value: tempValue, unit: getTemperatureScale(), displayed: isDisplayed)
                }
            }
            else {
                boolean isChange = isStateChange(device, name, value.toString())
                isDisplayed = isChange
                if (device.currentState(name)?.value != value) {
                    sendEvent(name: name, value: value, isStateChange: isChange, displayed: isDisplayed)
                }
            }
        }
    }
}

double getTemperature(double value) {
    float returnVal = value
    //log.debug("Temperature value: ${value}")
    if (getTemperatureScale() == 'C') {
        returnVal = value
    } else {
        returnVal = (celsiusToFahrenheit(value) as Float)
    }
    return returnVal.round(1)
}
