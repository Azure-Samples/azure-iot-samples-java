// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package com.microsoft.azure.sdk.iot.digitaltwin.sample;

import com.azure.core.util.IterableStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.azure.messaging.eventhubs.*;
import com.azure.messaging.eventhubs.models.*;
import com.microsoft.azure.sdk.iot.digitaltwin.service.DigitalTwinServiceClient;
import com.microsoft.azure.sdk.iot.digitaltwin.service.DigitalTwinServiceClientImpl;
import com.microsoft.azure.sdk.iot.digitaltwin.service.models.DigitalTwinCommandResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.*;

@Slf4j
public class InvokeDigitalTwinAsyncCommandSample {
    private static final int THREAD_POOL_MAX_NUMBER = 100;
    private static final int RECEIVE_TIMEOUT = 5;
    private static final String COMMAND_REQUEST_ID_PROPERTY_NAME = "iothub-command-request-id";

    private static String IOTHUB_CONNECTION_STRING = System.getenv("IOTHUB_CONNECTION_STRING");
    private static String DEVICE_ID = System.getenv("DEVICE_ID");
    private static String INTERFACE_INSTANCE_NAME = System.getenv("INTERFACE_INSTANCE_NAME");
    private static String ASYNC_COMMAND_NAME = System.getenv("ASYNC_COMMAND_NAME");
    private static String EVENTHUB_CONNECTION_STRING = System.getenv("EVENTHUB_CONNECTION_STRING");
    private static String PAYLOAD = System.getenv("PAYLOAD"); //optional

    private static final String usage =
            "In order to run this sample, you must set environment variables for \n" +
                    "IOTHUB_CONNECTION_STRING - Your IoT Hub's connection string\n" +
                    "DEVICE_ID - The ID of the device to invoke the command onto\n" +
                    "INTERFACE_INSTANCE_NAME - The interface the command belongs to\n" +
                    "ASYNC_COMMAND_NAME - The name of the command to invoke on your digital twin\n" +
                    "EVENTHUB_CONNECTION_STRING - The connection string to the EventHub associated to your IoT Hub\n" +
                    "PAYLOAD - (optional) The json payload to include in the command";

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
        verifyInputs();

        DigitalTwinServiceClient digitalTwinServiceClient = DigitalTwinServiceClientImpl.buildFromConnectionString().connectionString(IOTHUB_CONNECTION_STRING).build();

        log.info("Invoking " + ASYNC_COMMAND_NAME + " on device " + DEVICE_ID + " with interface instance name " + INTERFACE_INSTANCE_NAME);

        DigitalTwinCommandResponse digitalTwinCommandResponse = digitalTwinServiceClient.invokeCommand(DEVICE_ID, INTERFACE_INSTANCE_NAME, ASYNC_COMMAND_NAME, PAYLOAD);

        log.info("Command invoked on the device successfully, the returned status was " + digitalTwinCommandResponse.getStatus() + " and the request id was " + digitalTwinCommandResponse.getRequestId());
        if (digitalTwinCommandResponse.getPayload() == null) {
            log.info("The returned PAYLOAD was null");
        } else {
            log.info("The returned PAYLOAD was ");
            log.info(toPrettyFormat(digitalTwinCommandResponse.getPayload()));
        }

        listenForAsyncCommandUpdates(digitalTwinCommandResponse.getRequestId());
    }

    private static void listenForAsyncCommandUpdates(String requestId) throws ExecutionException, InterruptedException, IOException {
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(THREAD_POOL_MAX_NUMBER);
        EventHubConsumerClient eventHubConsumerClient = new EventHubClientBuilder()
                .connectionString(EVENTHUB_CONNECTION_STRING)
                .consumerGroup(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME)
                .buildConsumerClient();
        ConcurrentMap<String, Instant> instantMap = new ConcurrentHashMap();
        Iterator<String> partitionIterators = eventHubConsumerClient.getPartitionIds().iterator();
        while (partitionIterators.hasNext()) {
            String partitionId = partitionIterators.next();
            instantMap.put(partitionId, Instant.now().minusSeconds(0));
            executorService.scheduleWithFixedDelay(() -> {
                IterableStream<PartitionEvent> partitionEventIterableStream =
                        eventHubConsumerClient.receiveFromPartition(partitionId, 1,
                                EventPosition.fromEnqueuedTime(instantMap.get(partitionId)),
                                Duration.ofSeconds(RECEIVE_TIMEOUT));
                System.out.println(String.format("EventHub receiver created for partition %s, listening from %s [OperationTimeout: %s secs]", partitionId, instantMap.get(partitionId), RECEIVE_TIMEOUT));
                try {
                    receiveMessages(partitionEventIterableStream, partitionId, requestId);
                } catch (InterruptedException e) {
                    log.error("Eventhub receiver thread interrupted, gracefully closing the receiver now");
                } catch (ExecutionException e) {
                    log.error(String.format("Eventhub receiver thread encountered ExecutionException, gracefully closing the receiver now : %s", e.getMessage()));
                }
            }, 0, 1000, TimeUnit.MILLISECONDS);
        }

        //Wait for user to enter a key to exit the program
        new Scanner(System.in).nextLine();
        executorService.shutdown();
        eventHubConsumerClient.close();
        System.exit(0);
    }

    private static void receiveMessages(IterableStream<PartitionEvent> partitionEventIterableStream, String partitionId, String requestId) throws InterruptedException, ExecutionException {
        log.trace(String.format("Receiving from partition: %s", partitionId));
        long batchSize = partitionEventIterableStream.spliterator().getExactSizeIfKnown();
        Iterator<PartitionEvent> eventIterator = partitionEventIterableStream.iterator();
        if (!eventIterator.hasNext()) {
            log.trace("No events received.");
        } else {
            log.trace("ReceivedBatch Size: {}", batchSize);
            while (eventIterator.hasNext()) {
                EventData receivedEvent = eventIterator.next().getData();
                if (receivedEvent.getProperties() != null
                        && receivedEvent.getProperties().keySet().contains(COMMAND_REQUEST_ID_PROPERTY_NAME)) {
                    String payload = new String(receivedEvent.getBody(), Charset.forName("UTF-8"));

                    log.info("Received an update on the async command:");
                    for (String propertyKey : receivedEvent.getProperties().keySet()) {
                        log.info("    " + propertyKey + ":" + receivedEvent.getProperties().get(propertyKey));
                    }
                    log.info("    " + "Update Payload: ");
                    log.info("        " + payload);

                    if (payload.contains("100%")) {
                        log.info("Async command has finished, enter any key to finish\n");
                    }
                }
            }
        }
    }

    private static void verifyInputs() {
        if (isNullOrEmpty(IOTHUB_CONNECTION_STRING) || isNullOrEmpty(DEVICE_ID) || isNullOrEmpty(INTERFACE_INSTANCE_NAME) || isNullOrEmpty(ASYNC_COMMAND_NAME)) {
            log.warn(usage);
            System.exit(0);
        }
    }

    private static boolean isNullOrEmpty(String s) {
        return s == null || s.length() == 0;
    }

    public static String toPrettyFormat(String jsonString) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Object json = mapper.readValue(jsonString, Object.class);
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
    }
}
