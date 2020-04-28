/**
 *  Wireless Tag Generic
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
    definition (name: 'Wireless Tag Generic', namespace: 'swanny', author: 'swanny') {
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
    // log.debug "parsing data $results"

    if (results) {
        results.each { name, value ->
            valueUnit = null

            switch (name) {
                case 'battery':
                case 'humidity':
                    valueUnit = '%'
                    break
                case 'illuminance':
                    valueUnit = 'Lux'
                    break
            }
            if (name == 'temperature') {
                double tempValue = getTemperature(value)
                boolean isChange = isStateChange(device, name, tempValue.toString())
                // log.debug "name: $name, isChange: $isChange"
                // if (device.currentState(name)?.value != tempValue) {
                if (isChange) {
                    sendEvent(name: name, value: tempValue, unit: getTemperatureScale(), isStateChange: isChange, displayed: isChange)
                }
            }
            else {
                boolean isChange = isStateChange(device, name, value.toString())
                // log.debug "name: $name, isChange: $isChange"
                // log.debug "Previous: ${device.currentState(name)?.value}, New: $value"
                // if (device.currentState(name)?.value == value) {
                //     log.debug "It shouldn't be a state change..."
                // }
                // if (device.currentState(name)?.value != value) {
                if (isChange) {
                    // log.debug "It's a state change!"
                    if (valueUnit) {
                        sendEvent(name: name, value: value, unit: valueUnit, isStateChange: isChange, displayed: isChange)
                    } else {
                        sendEvent(name: name, value: value, isStateChange: isChange, displayed: isChange)
                    }
                }
            }
        }
    }
}

double getTemperature(double value) {
    double returnVal = value
    if (getTemperatureScale() == 'C') {
        returnVal = value
    } else {
        returnVal = (celsiusToFahrenheit(value) as double)
    }
    return returnVal.round(1)
}
