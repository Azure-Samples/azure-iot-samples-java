// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

// This application uses the Azure IoT Hub device SDK for Java
// For samples see: https://github.com/Azure/azure-iot-sdk-java/tree/master/device/iot-device-samples

package com.microsoft.docs.iothub.samples;

import com.microsoft.azure.sdk.iot.device.*;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.*;
import com.google.gson.Gson;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class SimulatedDevice {
  // The device connection string to authenticate the device with your IoT hub.
  // Using the Azure CLI:
  // az iot hub device-identity show-connection-string --hub-name {YourIoTHubName} --device-id MyJavaDevice --output table
  private static String connString = "{Your device connection string here}";

  // Using the MQTT protocol to connect to IoT Hub
  private static IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;
  private static DeviceClient client;

  // Define method response codes
  private static final int METHOD_SUCCESS = 200;
  private static final int METHOD_NOT_DEFINED = 404;
  private static final int INVALID_PARAMETER = 400;

  private static int telemetryInterval = 1000;

  // Specify the telemetry to send to your IoT hub.
  private static class TelemetryDataPoint {
    public double temperature;
    public double humidity;

    // Serialize object to JSON format.
    public String serialize() {
      Gson gson = new Gson();
      return gson.toJson(this);
    }
  }

  // Print the acknowledgement received from IoT Hub for the method acknowledgement sent.
  protected static class DirectMethodStatusCallback implements IotHubEventCallback
  {
    public void execute(IotHubStatusCode status, Object context)
    {
      System.out.println("Direct method # IoT Hub responded to device method acknowledgement with status: " + status.name());
    }
  }

  // Print the acknowledgement received from IoT Hub for the telemetry message sent.
  private static class EventCallback implements IotHubEventCallback {
    public void execute(IotHubStatusCode status, Object context) {
      System.out.println("IoT Hub responded to message with status: " + status.name());

      if (context != null) {
        synchronized (context) {
          context.notify();
        }
      }
    }
  }

  protected static class DirectMethodCallback implements DeviceMethodCallback
  {
    private void setTelemetryInterval(int interval)
    {
      System.out.println("Direct method # Setting telemetry interval (seconds): " + interval);
      telemetryInterval = interval * 1000;
    }

    @Override
    public DeviceMethodData call(String methodName, Object methodData, Object context)
    {
      DeviceMethodData deviceMethodData;
      String payload = new String((byte[])methodData);
      switch (methodName)
      {
        case "SetTelemetryInterval" :
        {
          int interval;
          try {
            int status = METHOD_SUCCESS;
            interval = Integer.parseInt(payload);
            System.out.println(payload);
            setTelemetryInterval(interval);
            deviceMethodData = new DeviceMethodData(status, "Executed direct method " + methodName);
          } catch (NumberFormatException e) {
            int status = INVALID_PARAMETER;
            deviceMethodData = new DeviceMethodData(status, "Invalid parameter " + payload);
          }
          break;
        }
        default:
        {
          int status = METHOD_NOT_DEFINED;
          deviceMethodData = new DeviceMethodData(status, "Not defined direct method " + methodName);
        }
      }
      return deviceMethodData;
    }
  }

  private static class MessageSender implements Runnable {
    public void run() {
      try {
        // Initialize the simulated telemetry.
        double minTemperature = 20;
        double minHumidity = 60;
        Random rand = new Random();

        while (true) {
          // Simulate telemetry.
          double currentTemperature = minTemperature + rand.nextDouble() * 15;
          double currentHumidity = minHumidity + rand.nextDouble() * 20;
          TelemetryDataPoint telemetryDataPoint = new TelemetryDataPoint();
          telemetryDataPoint.temperature = currentTemperature;
          telemetryDataPoint.humidity = currentHumidity;

          // Add the telemetry to the message body as JSON.
          String msgStr = telemetryDataPoint.serialize();
          Message msg = new Message(msgStr);

          // Add a custom application property to the message.
          // An IoT hub can filter on these properties without access to the message body.
          msg.setProperty("temperatureAlert", (currentTemperature > 30) ? "true" : "false");
          
          System.out.println("Sending message: " + msgStr);

          Object lockobj = new Object();

          // Send the message.
          EventCallback callback = new EventCallback();
          client.sendEventAsync(msg, callback, lockobj);

          synchronized (lockobj) {
            lockobj.wait();
          }
          Thread.sleep(telemetryInterval);
        }
      } catch (InterruptedException e) {
        System.out.println("Finished.");
      }
    }
  }

  public static void main(String[] args) throws IOException, URISyntaxException {

    // Connect to the IoT hub.
    client = new DeviceClient(connString, protocol);
    client.open();

    // Register to receive direct method calls.
    client.subscribeToDeviceMethod(new DirectMethodCallback(), null, new DirectMethodStatusCallback(), null);
    
    // Create new thread and start sending messages 
    MessageSender sender = new MessageSender();
    ExecutorService executor = Executors.newFixedThreadPool(1);
    executor.execute(sender);

    // Stop the application.
    System.out.println("Press ENTER to exit.");
    System.in.read();
    executor.shutdownNow();
    client.closeNow();
  }
}
