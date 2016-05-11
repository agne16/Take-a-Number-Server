# Take a Number
Senior Capstone Project

Take a Number is a suite of applications intended to optimize the rate at which instructors and lab aides provide help to students in computer lab courses at the University of Portland.
This project additionally allows instructors to log student's checkpoint progress and sync it to the lab aides.

This is the Server portion of Take a Number. As the core of the project, it handles data manipulation and network synchronization between the Android application and web interface.

Take a Number is made with Java and requires a network connection to run.

Dependencies:

[Jasypt](http://www.jaysypt.org) - To encrypt student checkpoint to data to conform to FERPA regulations

[WebSocket](https://github.com/TooTallNate/Java-WebSocket) - To ensure proper communication between Java, Android-Java, and Javascript

[JDOM](http://www.jdom.org) - To parse XML


The Android application can be found **[here](https://github.com/agne16/Take-a-Number-Android)**

The web page can be found **[here](https://github.com/agne16/Take-a-Number-Web)**
