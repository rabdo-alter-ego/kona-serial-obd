# Kona Serial Obd
Allows you to connect to the ecu of the car to obtain SOC, BATTERIES VOLTAGE and STATE OF CHARGE informations, then i suggest you to make a simple change to the app to deliver the data to your server or to Home Assistant (personally i prefer Tinytuja).
This project is not for everybody, it's mainly for developers. Lots of times with tecnology we can just try and see if it works or not, but i don t suggest you to test if you don t know what you are doing.


<img width="500" height="375" alt="immagine" src="https://github.com/user-attachments/assets/51d80612-8e16-491c-a9d5-177f69f21667" />



# My hardware
In order to use this project you will need:
- A hyundai Kona (~2020) or a hyundai Kia niro (i don t know the exact model but i can check it for my kona and i know kona and niro use the same protocol)
- An obd2 elm serial bluetooth chip that supports EV ecu (not all supports it). There are plenty of discussion about the "good" and the "bad" OBD, onestly i don t care and bought the cheapest one from aliexpress (photo) and it works but remember that the OBD needs to be BLUETOOTH SERIAL!!! The use of the software is at your own risk. I am not liable for damage caused by improper use or cheap, fake OBD2 dongle.
- An android mobile with version over 6 but below 13


<img width="600" height="400" alt="immagine" src="https://github.com/user-attachments/assets/b843a3ee-ab34-4eea-ab8a-98c80fedf239" />


# Main inspirators

This Android app comes directly from https://github.com/kai-morich/SimpleBluetoothTerminal, i noticed that it was successfully running commands on my OBD so I cloned the repo and made some modifications to it:
- use the proper end terminator for obd
- added a function to send and wait for response to send commands 1 at time that never stops in background
- added initilizations commands, added commands to parse the main battery voltage and soc
- removed terminal and added notifications instead (ui caused more troubles than it solved)

There are other resources that helped me a lot in this project like EVNotify inspired me first in this hard journey
https://github.com/EVNotify/EVNotify/ 
https://github.com/OBDb/Hyundai-Kona-Electric
https://github.com/JejuSoul/OBD-PIDs-for-HKMC-EVs


# Similiar projects
https://github.com/Tuoris/niro-spy
https://github.com/nickn17/evDash
https://github.com/cyberelectronics/Konassist
https://github.com/nickn17/evDash


[![Codacy Badge](https://api.codacy.com/project/badge/Grade/a3d8a40d7133497caa11051eaac6f1a2)](https://www.codacy.com/manual/kai-morich/SimpleBluetoothTerminal?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=kai-morich/SimpleBluetoothTerminal&amp;utm_campaign=Badge_Grade)

# Known facts
- the car seems to keep the obd running only when turned on or during charge (need to check for edge cases like soc 100% but still plugged)
- the device sometimes responds with NO DATA while charging, the solution seems to reconnect (problem found from EV notify github issues and happened to me only once)

# SimpleBluetoothTerminal

This Android app provides a line-oriented terminal / console for classic Bluetooth (2.x) devices implementing the Bluetooth Serial Port Profile (SPP)

For an overview on Android Bluetooth communication see 
[Android Bluetooth Overview](https://developer.android.com/guide/topics/connectivity/bluetooth).

This App implements RFCOMM connection to the well-known SPP UUID 00001101-0000-1000-8000-00805F9B34FB

## Motivation

I got various requests asking for help with Android development or source code for my 
[Serial Bluetooth Terminal](https://play.google.com/store/apps/details?id=de.kai_morich.serial_bluetooth_terminal) app.
Here you find a simplified version of my app.


