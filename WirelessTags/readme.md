## Features

The Wireless Sensor Tags integration connects securely to your www.mytaglist.com account securely using OAuth2. Once attached, the app gets a list of the tags you have in your account. You can then choose which tags you want to create as devices. The app currently creates the devices as a Wireless Tag Generic type.

Generic Device Features:

* Temperature 
* Humidity (shows as zero on tags that don't support humidity)
* Illuminance
* Refresh & Poll
* Battery Level

The majority of the tuning you can do on www.mytaglist.com will apply. Tuning options like responsiveness, sensitivity, and threshold angle may improve responsiveness or fuctionality in Hubitat as well.

## Installation

To set up the Wireless Sensor Tags integration, follow these steps:

Set up the app on Hubitat:
* Create a new item under App Code
* Copy the code from WirelessTagsConnect.groovy into the new App
* Save
* Go back into the new App
* Click the "OAuth" button
* Click "Enable OAuth in App"
* Save your way out...

Set up the Device Types:
* Create a new item under Drivers Code
* Copy the code from WirelessTagsGeneric.groovy
* Save

Connect to your Wireless Tags account:
* Open the Apps screen on Hubitat
* Click "Add User App" and choose Wireless Tags (Connect)
* Click "Authorize wirelesstag.net"
* Log in to wirelesstag, and Allow or Approve access
* Once the popup page loads to a blank white page, close that window to continue setup
* Follow the instructions in the App for the rest of the details

If you add new tags to your web account, go through the Wireless Tags (Connect) app again to add the new devices.

## Update Previous Installation
Update the app:
* Open your previously created App Code
* Copy the code from WirelessTagsConnect.groovy over the code of your App Code
* Save

Set up the Device Types:
* Open your previously created Drivers Code
* Copy the code from WirelessTagsGeneric.groovy over the code of your Drivers Code
* Save
