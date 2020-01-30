// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package com.microsoft.azure.sdk.iot.digitaltwin.sample;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.digitaltwin.device.DigitalTwinClientResult;
import com.microsoft.azure.sdk.iot.digitaltwin.device.DigitalTwinDeviceClient;
import com.microsoft.azure.sdk.iot.digitaltwin.device.SdkInformationComponent;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Scanner;

import static com.microsoft.azure.sdk.iot.device.IotHubClientProtocol.MQTT;
import static java.util.Arrays.asList;

@Slf4j
public class Application {
    private static final String DIGITAL_TWIN_DEVICE_CONNECTION_STRING = System.getenv("DIGITAL_TWIN_DEVICE_CONNECTION_STRING");
    private static final String DCM_ID = "urn:java_sdk_sample:sample_device:1";
    private static final String ENVIRONMENTAL_SENSOR_INTERFACE_INSTANCE_NAME = "environmentalSensor";
    private static final String MODEL_DEFINITION_COMPONENT_NAME = "urn_azureiot_ModelDiscovery_ModelDefinition";

    public static void main(String[] args) throws URISyntaxException, IOException {
        if (DIGITAL_TWIN_DEVICE_CONNECTION_STRING == null || DIGITAL_TWIN_DEVICE_CONNECTION_STRING.isEmpty()) {
            log.info("Please set a value for the environment variable \"DIGITAL_TWIN_DEVICE_CONNECTION_STRING\"");
            return;
        }

        DeviceClient deviceClient = new DeviceClient(DIGITAL_TWIN_DEVICE_CONNECTION_STRING, MQTT);

        DigitalTwinDeviceClient digitalTwinDeviceClient = new DigitalTwinDeviceClient(deviceClient, DCM_ID);
        final EnvironmentalSensor environmentalSensor = new EnvironmentalSensor(ENVIRONMENTAL_SENSOR_INTERFACE_INSTANCE_NAME);
        final DeviceInformation deviceInformation = DeviceInformation.builder()
                                                                     .manufacturer("Microsoft")
                                                                     .model("1.0.0")
                                                                     .osName(System.getProperty("os.name"))
                                                                     .processorArchitecture(System.getProperty ("os.arch"))
                                                                     .processorManufacturer("Intel(R) Core(TM)")
                                                                     .softwareVersion("JDK" + System.getProperty ("java.version"))
                                                                     .totalMemory(16e9)
                                                                     .totalStorage(1e12)
                                                                     .build();
        final ModelDefinition modelDefinition = ModelDefinition.builder()
                .digitalTwinComponentName(MODEL_DEFINITION_COMPONENT_NAME)
                .build();

        // step 1: bindComponents
        DigitalTwinClientResult bindComponentsResult = digitalTwinDeviceClient.bindComponents(asList(deviceInformation, environmentalSensor, modelDefinition, SdkInformationComponent.getInstance()));
        log.info("Bind components result: {}.", bindComponentsResult);

        // step 2: send registration message, optional
        // TODO It's now required for IoTExplorer
        DigitalTwinClientResult registerComponentsResult = digitalTwinDeviceClient.registerComponents();
        log.info("Register components result: {}.", registerComponentsResult);

        // step 3: subscribe for commands and properties, optional, to enable command and properties
        DigitalTwinClientResult subscribeForCommandsResult = digitalTwinDeviceClient.subscribeForCommands();
        log.info("Subscribe for commands result: {}.", subscribeForCommandsResult);
        DigitalTwinClientResult subscribeForPropertiesResult = digitalTwinDeviceClient.subscribeForProperties();
        log.info("Subscribe for properties result: {}.", subscribeForPropertiesResult);

        // step 4: ready to use
        DigitalTwinClientResult readyResult = digitalTwinDeviceClient.ready();
        log.info("Notify ready result: {}.", readyResult);

        // step 5: sync up properties, optional
        DigitalTwinClientResult syncupPropertiesResult = digitalTwinDeviceClient.syncupProperties();
        log.info("Sync up properties result: {}.", syncupPropertiesResult);

        DigitalTwinClientResult updateStatusResult = environmentalSensor.updateStatusAsync(true).blockingGet();
        log.info("Update state of environmental sensor to true, result: {}", updateStatusResult);

        log.info("Waiting for service updates...");
        log.info("Enter any key to finish");
        new Scanner(System.in).nextLine();
        System.exit(0);
    }

}
