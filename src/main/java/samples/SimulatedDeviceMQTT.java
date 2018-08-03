// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

// This application uses the Azure IoT Hub device SDK for Java
// For samples see: https://github.com/Azure/azure-iot-sdk-java/tree/master/device/iot-device-samples

// java 버전은 https://docs.microsoft.com/ko-kr/azure/iot-hub/tutorial-routing#clean-up-resources
// 예제 수행 시 정상 동작하지 않아 https://github.com/Azure-Samples/azure-iot-samples-csharp/archive/master.zip
// 참고하여 일부 추가함

package samples;

import com.google.gson.Gson;
import com.microsoft.azure.sdk.iot.device.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



public class SimulatedDeviceMQTT {
    // The device connection string to authenticate the device with your IoT hub.
    // Using the Azure CLI:
    // az iot hub device-identity show-connection-string --hub-name {YourIoTHubName} --device-id MyJavaDevice --output table
    private static String connString = "HostName=ContosoTestHub30850.azure-devices.net;DeviceId=Contoso-Test-Device;SharedAccessKey=n7eQMg5qdCHQ03uuFVWh4sfdLi8iwRtpx9J5d9VocU4=";

    // Using the MQTT protocol to connect to IoT Hub
    private static IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;
    private static DeviceClient client;

    // Specify the telemetry to send to your IoT hub.
    private static class TelemetryDataPoint {
        public String deviceId;
        public double temperature;
        public double humidity;
        public String pointInfo;

        // Serialize object to JSON format.
        public String serialize() {
            Gson gson = new Gson();
            return gson.toJson(this);
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

                    String infoString;
                    String levelValue;

                    if (rand.nextDouble() > 0.7)
                    {
                        if (rand.nextDouble() > 0.5)
                        {
                            levelValue = "critical";
                            infoString = "This is a critical message.";
                        }
                        else
                        {
                            levelValue = "storage";
                            infoString = "This is a storage message.";
                        }
                    }
                    else
                    {
                        levelValue = "normal";
                        infoString = "This is a normal message.";
                    }

                    telemetryDataPoint.pointInfo = infoString;

                    // Add the telemetry to the message body as JSON.
                    String msgStr = telemetryDataPoint.serialize();
                    Message msg = new Message(msgStr);

                    // Add a custom application property to the message.
                    // An IoT hub can filter on these properties without access to the message body.
//                    msg.setProperty("temperatureAlert", (currentTemperature > 30) ? "true" : "false");
                    msg.setProperty("level", levelValue);

                    System.out.println(msg.getProperty("level"));

                    System.out.println("Sending message: " + msgStr);

                    Object lockobj = new Object();

                    // Send the message.
                    EventCallback callback = new EventCallback();
                    client.sendEventAsync(msg, callback, lockobj);

                    synchronized (lockobj) {
                        lockobj.wait();
                    }
                    Thread.sleep(1000);
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
