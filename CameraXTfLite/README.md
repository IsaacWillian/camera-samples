# CameraX + Tensorflow Lite

## Original
This sample implements an Activity that performs real-time object detection on
the live camera frames. It performs the following operations:
1. Initializes camera preview and image analysis frame streams using CameraX
2. Loads a mobilenet quantized model using Tensorflow Lite
3. Converts each incoming frame to the RGB colorspace and resizes it to 224x224 pixels
4. Performs inference on the transformed frames and reports the object predicted on the screen

The whole pipeline is able to maintain 30 FPS on a Pixel 3 XL.

## Changes
1. Beginning in the search activity, where an object name is taken
2. The camera activity now finish when a object it's found, without user action
3. The frame where contains object is show in new activity

## Screenshots
![demo](screenshots/demo.gif "demo animation")
![screenshot 1](screenshots/screenshot-1.jpg "screenshot 1")

