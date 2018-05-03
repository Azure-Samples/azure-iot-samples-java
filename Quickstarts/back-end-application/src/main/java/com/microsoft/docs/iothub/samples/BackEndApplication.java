// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

// This application uses the Azure IoT Hub service SDK for Java
// For samples see: https://github.com/Azure/azure-iot-sdk-java/tree/master/service/iot-service-samples

package com.microsoft.docs.iothub.samples;

import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceMethod;
import com.microsoft.azure.sdk.iot.service.devicetwin.MethodResult;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class BackEndApplication {

  // Connection string for your IoT Hub
  // az iot hub show-connection-string --hub-name {your iot hub name}
  public static final String iotHubConnectionString = "{Your service connection string here}";

  // Device to call direct method on.
  public static final String deviceId = "MyJavaDevice";

  // Name of direct method and payload.
  public static final String methodName = "SetTelemetryInterval";
  public static final int payload = 10; // Number of seconds for telemetry interval.

  public static final Long responseTimeout = TimeUnit.SECONDS.toSeconds(30);
  public static final Long connectTimeout = TimeUnit.SECONDS.toSeconds(5);

  public static void main(String[] args) {
    try {
      System.out.println("Calling direct method...");

      // Create a DeviceMethod instance to call a direct method.
      DeviceMethod methodClient = DeviceMethod.createFromConnectionString(iotHubConnectionString);

      // Call the direct method.
      MethodResult result = methodClient.invoke(deviceId, methodName, responseTimeout, connectTimeout, payload);

      if (result == null) {
        throw new IOException("Direct method invoke returns null");
      }

      // Show the acknowledgement from the device.
      System.out.println("Status: " + result.getStatus());
      System.out.println("Response: " + result.getPayload());
    } catch (IotHubException e) {
      System.out.println("IotHubException calling direct method:");
      System.out.println(e.getMessage());
    } catch (IOException e) {
      System.out.println("IOException calling direct method:");
      System.out.println(e.getMessage());
    }
    System.out.println("Done!");
  }
}
