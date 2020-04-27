/**
 *  Wireless Tags (Connect)
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
definition(
    name: 'Wireless Tags (Connect)',
    namespace: 'swanny',
    author: 'swanny',
    description: 'Wireless Tags connection',
    category: 'Convenience',
    iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
    iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
    oauth: true)

preferences {
    page(name: 'auth', title: 'Wireless Tags', nextPage:'deviceList', content:'authPage', uninstall: true)
    page(name: 'deviceList', title: 'Wireless Tags', content:'wirelessDeviceList', install:true)
}

mappings {
    path('/swapToken') { action: [GET: 'swapToken'] }
}

def wirelessDeviceList() {
    Map availDevices = getWirelessTags()

    def p = dynamicPage(name: 'deviceList', title: 'Select Your Devices', uninstall: true) {
        section('') {
            paragraph 'Tap below to see the list of Wireless Tag devices available in your Wireless Tags account and select the ones you want to connect to Hubitat.'
            paragraph 'When you hit Done, the setup can take as much as 10 seconds per device selected.'
            input(name: 'devices', title:'', type: 'enum', required:true, multiple:true, description: 'Tap to choose', metadata:[values:availDevices])
            paragraph 'Note that only the wireless motion sensor tags (8- and 13-bit) have been tested. The PIR/Reed Kumosensor will likely work with the same Device Type and will automatically attempt to use it. The water sensors should work with the specific device type but they have not been tested.'
        }
        section('Tuning Tips', hidden: true, hideable: true) {
            paragraph 'A lot of settings can be tuned using your mytaglist.com account: '
            paragraph 'For the sensing of motion and open/close, it is recommended to turn up the motion sensor responsiveness but it will use your battery faster.'
            paragraph 'The temperature and humidity values can be calibrated using the mytaglist web UI.'
        }
        section('Optional Settings', hidden: true, hideable: true) {
            input 'pollTimer', 'number', title: 'Minutes between poll updates of the sensors', defaultValue: 5
        }
        section([mobileOnly:true]) {
            paragraph 'Select up to 5 devices in each instance. Use a unique name here to create multiple apps.'
            label title: 'Assign a name for this SmartApp instance (optional)', required: false
        }
    }

    return p
}

Map getWirelessTags() {
    Map result = getTagStatusFromServer()

    Map availDevices = [:]
    result?.each { device ->
        UUID dni = device?.uuid
        availDevices[dni] = device?.name
    }

    log.debug "devices: $availDevices"

    return availDevices
}

void installed() {
    initialize()
}

void updated() {
    unsubscribe()
    initialize()
}

String getChildNamespace() { 'swanny' }

String getChildName(Map tagInfo) {
    String deviceType = 'Wireless Tag Motion'
    if (tagInfo) {
        switch (tagInfo.tagType) {
            case 32:
            case 33:
                deviceType = 'Wireless Tag Water'
                break
        // add new device types here
        }
    }
    return deviceType
}

void initialize() {
    unschedule()

    def curDevices = devices.collect { dni ->
        def d = getChildDevice(dni)

        def tag = atomicState.tags.find { it.uuid == dni }

        if (d) {
            log.debug "found ${d.displayName} $dni already exists"
            d.updated()
        }
        else
        {
            d = addChildDevice(getChildNamespace(), getChildName(tag), dni, null, [label:tag?.name])
            d.initialSetup()
            log.debug "created ${d.displayName} $dni"
        }

        return dni
    }

    def delete
    // Delete any that are no longer in settings
    if (curDevices) {
        delete = getChildDevices().findAll { !curDevices.contains(it.deviceNetworkId) }
    }
    else {
        delete = getAllChildDevices()
    }

    delete.each { deleteChildDevice(it.deviceNetworkId) }

    if (atomicState.tags == null) { atomicState.tags = [:] }

    pollHandler()

    // set up internal poll timer
    if (pollTimer == null) {
        pollTimer = 5
    }
    log.trace "setting poll to ${pollTimer}"
    schedule("0 0/${pollTimer.toInteger()} * * * ?", pollHandler)
}

def authPage() {
    if (!atomicState.accessToken) {
        log.debug 'about to create access token'
        createAccessToken()
        atomicState.accessToken = state.accessToken
    }

    // oauth docs = http://www.mytaglist.com/eth/oauth2_apps.html
    String description = 'Required'
    boolean uninstallAllowed = false
    boolean oauthTokenProvided = false

    if (atomicState.authToken) {
        description = 'You are connected.'
        uninstallAllowed = true
        oauthTokenProvided = true
    }

    String redirectUrl = oauthInitUrl()
    log.debug "RedirectUrl = $redirectUrl"

    // get rid of next button until the user is actually auth'd
    if (oauthTokenProvided) {
        log.debug 'oauthTokenProvided'
        return dynamicPage(name: 'auth', title: 'Log In', nextPage:'deviceList', uninstall:uninstallAllowed) {
            section {
                paragraph 'Tap Next to continue to setup your devices.'
                href url: redirectUrl, style: 'embedded', state: 'complete', title: 'Wireless Tags', description: description
            }
        }
    } else {
        log.debug '!oauthTokenProvided'
        log.debug "desscription: $description"
        return dynamicPage(name: 'auth', title: 'Login', nextPage:null, uninstall:uninstallAllowed) {
            section {
                paragraph 'Tap below to log in to the Wireless Tags service and authorize Hubitat access.'
                href url:redirectUrl, style:'embedded', required:true, title:'Wireless Tags', description:description
            }
        }
    }
}

String oauthInitUrl() {
    log.debug 'oauthInitUrl'
    String stcid = getHubitatClientId()

    atomicState.oauthInitState = UUID.randomUUID().toString()

    Map oauthParams = [
        client_id: stcid,
        state: atomicState.oauthInitState,
        redirect_uri: generateRedirectUrl(),
    ]

    return 'https://www.mytaglist.com/oauth2/authorize.aspx?' + toQueryString(oauthParams)
}

String generateRedirectUrl() {
    log.debug 'generateRedirectUrl'
    // return apiServerUrl("/api/token/${atomicState.accessToken}/smartapps/installations/${app.id}/swapToken")
    // return apiServerUrl("token/${atomicState.accessToken}/smartapps/installations/${app.id}/swapToken")
    log.debug 'state.accessToken: ' + state.accessToken
    return getFullApiServerUrl() + "/oauth/initialize?access_token=${state.accessToken}"
}

void swapToken() {
    log.debug "swapping token: $params"

    // Linter suggested these are not used, commenting out for now:
    // def code = params.code
    // def oauthState = params.state

    // TODO: verify oauthState == atomicState.oauthInitState
    String stcid = getHubitatClientId()

    Map refreshParams = [
        method: 'POST',
        uri: 'https://www.mytaglist.com/',
        path: '/oauth2/access_token.aspx',
        query: [ grant_type: 'authorization_code', client_id: stcid, client_secret: '042cf455-0fe9-483c-a4e4-9198a2ae7c9d', code: params.code, redirect_uri:generateRedirectUrl()],
    ]

    try {
        Map jsonMap
        httpPost(refreshParams) { resp ->
            if (resp.status == 200) {
                jsonMap = resp.data
                if (resp.data) {
                    atomicState.authToken = jsonMap?.access_token
                } else {
                    log.trace 'error = ' + resp
                }
            } else {
                log.trace 'response = ' + resp
            }
        }
    } catch ( ex ) {
        atomicState.authToken = null
        log.trace 'error = ' + ex
    }

    String html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=640">
<title>Withings Connection</title>
<style type="text/css">
    @font-face {
        font-family: 'Swiss 721 W01 Thin';
        src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot');
        src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot?#iefix') format('embedded-opentype'),
             url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.woff') format('woff'),
             url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.ttf') format('truetype'),
             url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.svg#swis721_th_btthin') format('svg');
        font-weight: normal;
        font-style: normal;
    }
    @font-face {
        font-family: 'Swiss 721 W01 Light';
        src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot');
        src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot?#iefix') format('embedded-opentype'),
             url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.woff') format('woff'),
             url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.ttf') format('truetype'),
             url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.svg#swis721_lt_btlight') format('svg');
        font-weight: normal;
        font-style: normal;
    }
    .container {
        width: 560px;
        padding: 40px;
        /*background: #eee;*/
        text-align: center;
    }
    img {
        vertical-align: middle;
    }
    img:nth-child(2) {
        margin: 0 30px;
    }
    p {
        font-size: 2.2em;
        font-family: 'Swiss 721 W01 Thin';
        text-align: center;
        color: #666666;
        padding: 0 40px;
        margin-bottom: 0;
    }
/*
    p:last-child {
        margin-top: 0px;
    }
*/
    span {
        font-family: 'Swiss 721 W01 Light';
    }
</style>
</head>
<body>
    <div class="container">
        <img src="http://wirelesstag.net/media/product_title.png" alt="wireless tags icon" />
        <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="connected device icon" />
        <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/st-logo%402x.png" alt="SmartThings logo" />
        <p>Your Wireless Tags Account is now connected to SmartThings!</p>
        <p>Click 'Done' to finish setup.</p>
    </div>
</body>
</html>
"""

    render contentType: 'text/html', data: html
}

Map getEventStates() {
    Map tagEventStates = [ 0: 'Disarmed',
        1: 'Armed',
        2: 'Moved',
        3: 'Opened',
        4: 'Closed',
        5: 'Detected',
        6: 'Timed Out',
        7: 'Stabilizing...', ]
    return tagEventStates
}

void pollHandler() {
    log.trace 'pollHandler'
    getTagStatusFromServer()
    updateAllDevices()
}

void updateAllDevices() {
    atomicState.tags.each { device ->
        UUID dni = device.uuid
        List<Device> d = getChildDevice(dni)

        if (d) {
            updateDeviceStatus(device, d)
        }
    }
}

void pollSingle(def child) {
    log.trace 'pollSingle'
    getTagStatusFromServer()

    def device = atomicState.tags.find { it.uuid == child.device.deviceNetworkId }

    if (device) {
        updateDeviceStatus(device, child)
    }
}

void updateDeviceStatus(def device, def d) {
    Map tagEventStates = getEventStates()

    log.debug "device info: ${device}"

    // parsing data here
    Map data = [
        tagType: convertTagTypeToString(device),
        temperature: device.temperature.toDouble().round(1),
        battery: (device.batteryVolt * 100 / 3).toDouble().round(),
        humidity: (device.cap).toDouble().round(),
        illuminance: (device.lux)?.toDouble().round(),
        contact : (tagEventStates[device.eventState] == 'Opened') ? 'open' : 'closed',
        water : (device.shorted == true) ? 'wet' : 'dry',
    ]
    d.generateEvent(data)
}

int getPollRateMillis() { return 2 * 1000 }

Map getTagStatusFromServer() {
    int timeSince = (atomicState.lastPoll != null) ? now() - atomicState.lastPoll : 1000 * 1000

    if ((atomicState.tags == null) || (timeSince > getPollRateMillis())) {
        Map result = postMessage('/ethClient.asmx/GetTagList', null)
        atomicState.tags = result?.d
        atomicState.lastPoll = now()
    } else {
        log.trace 'waiting to refresh from server'
    }
    return atomicState.tags
}

// Poll Child is invoked from the Child Device itself as part of the Poll Capability
void pollChild( Map child ) {
    pollSingle(child)
}

void refreshChild( Map child ) {
    UUID id = getTagID(child.device.deviceNetworkId)

    if (id != null) {
        // PingAllTags didn't reliable update the tag we wanted so just ping the one
        Map query = ['id':id]
        postMessage('/ethClient.asmx/PingTag', query)
        pollSingle( child )
    } else {
        log.trace 'Could not find tag'
    }
}

Map postMessage(String path, Object query) {
    log.trace "sending ${path}"

    Map message = [
                method: 'POST',
                uri: 'https://www.mytaglist.com/',
                path: path,
                headers: ['Content-Type': 'application/json', 'Authorization': "Bearer ${atomicState.authToken}"],
            ]
    if (query != null) {
        if (query instanceof String) {
            message['body'] = query
        } else {
            message['body'] =  toJson(query)
        }
    }

    Map jsonMap
    try {
        httpPost(message) { resp ->
            if (resp.status == 200) {
                if (resp.data) {
                    log.trace 'success'
                    jsonMap = resp.data
                } else {
                    log.trace 'error = ' + resp
                }
            } else {
                log.debug "http status: ${resp.status}"
                if (resp.status == 500 && resp.data.status.code == 14) {
                    log.debug 'Need to refresh auth token?'
                    atomicState.authToken = null
                }
                else {
                    log.error 'Authentication error, invalid authentication method, lack of credentials, etc.'
                }
            }
        }
    } catch ( ex ) {
        //atomicState.authToken = null
        log.trace 'error = ' + ex
    }

    return jsonMap
}

String getQuoted(String orig) {
    return (orig != null) ? "\"${orig}\"" : orig
}

def getTagID(UUID uuid) {
    return atomicState.tags.find { it.uuid == uuid}?.slaveId
}

String getTagVersion(Map tag) {
    if (tag.version1 == 2) {
        return (tag.rev == 14) ? ' (v2.1)' : ' (v2.0)'
    }
    if (tag.tagType != 12) { return '' }
    switch (tag.rev) {
        case 0:
            return ' (v1.1)'
        case 1:
            return ' (v1.2)'
        case 11:
            return ' (v1.3)'
        case 12:
            return ' (v1.4)'
        case 13:
            return ' (v1.5)'
        default:
            return ''
    }
}

String convertTagTypeToString(Map tag) {
    String tagString = 'Unknown'

    switch (tag.tagType) {
        case 12:
            tagString = 'MotionSensor'
            break
        case 13:
            tagString = 'MotionHTU'
            break
        case 72:
            tagString = 'PIR'
            break
        case 52:
            tagString = 'ReedHTU'
            break
        case 53:
            tagString = 'Reed'
            break
        case 62:
            tagString = 'Kumostat'
            break
        case 32:
        case 33:
            tagString = 'Moisture'
            break
        case 26:
            tagString = 'Lux'
            break
    }

    return tagString + getTagVersion(tag)
}

String toJson(Map m) {
    return new groovy.json.JsonBuilder(m).toString()
}

String toQueryString(Map m) {
    return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join('&')
}

// def getSmartThingsClientId() { '67953bd9-8adf-422a-a7f0-5dbf256b9024' }
String getHubitatClientId() { 'ec3ba18c-cb06-4d23-8b20-5d7ef6b86f1d' }

void debugEvent(String message, boolean displayEvent) {
    Map results = [name: 'appdebug', descriptionText: message, displayed:displayEvent]
    log.debug "Generating AppDebug Event: ${results}"
    sendEvent (results)
}
