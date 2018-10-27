package com.iothub.azure.microsoft.com.androidsample;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubEventCallback;
import com.microsoft.azure.sdk.iot.device.IotHubMessageResult;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;
import com.microsoft.azure.sdk.iot.device.Message;

import java.io.IOException;
import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {

    private final String connString = BuildConfig.DeviceConnectionString;;

    private double temperature;
    private double humidity;
    private String msgStr;
    private Message sendMessage;
    private String lastException;

    private DeviceClient client;

    IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;

    Button btnStart;
    Button btnStop;

    TextView txtMsgsSentVal;
    TextView txtLastTempVal;
    TextView txtLastHumidityVal;
    TextView txtLastMsgSentVal;
    TextView txtLastMsgReceivedVal;

    private int msgSentCount = 0;
    private int receiptsConfirmedCount = 0;
    private int sendFailuresCount = 0;
    private int msgReceivedCount = 0;

    private final Handler handler = new Handler();
    private Thread sendThread;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        txtMsgsSentVal = findViewById(R.id.txtMsgsSentVal);

        txtLastTempVal = findViewById(R.id.txtLastTempVal);
        txtLastHumidityVal = findViewById(R.id.txtLastHumidityVal);
        txtLastMsgSentVal = findViewById(R.id.txtLastMsgSentVal);
        txtLastMsgReceivedVal = findViewById(R.id.txtLastMsgReceivedVal);

        btnStop.setEnabled(false);
    }

    public void btnStopOnClick(View v)
    {
        new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    sendThread.interrupt();
                    client.closeNow();
                    System.out.println("Shutting down...");
                }
                catch (Exception e)
                {
                    lastException = "Exception while closing IoTHub connection: " + e.toString();
                    handler.post(exceptionRunnable);
                }
            }
        }).start();

        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
    }

    public void btnStartOnClick(View v)
    {
        sendThread = new Thread(new Runnable() {
            public void run()
            {
                try
                {
                    initClient();
                    for(;;)
                    {
                        sendMessages();
                        Thread.sleep(5000);
                    }
                }
                catch (InterruptedException e)
                {
                    return;
                }
                catch (Exception e)
                {
                    lastException = "Exception while opening IoTHub connection: " + e.toString();
                    handler.post(exceptionRunnable);
                }
            }
        });

        sendThread.start();

        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
    }

    final Runnable updateRunnable = new Runnable() {
        public void run() {
            txtLastTempVal.setText(String.format("%.2f",temperature));
            txtLastHumidityVal.setText(String.format("%.2f",humidity));
            txtMsgsSentVal.setText(Integer.toString(msgSentCount));
            txtLastMsgSentVal.setText("[" + new String(sendMessage.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET) + "]");
        }
    };

    final Runnable exceptionRunnable = new Runnable() {
        public void run() {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage(lastException);
            builder.show();
            System.out.println(lastException);
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
        }
    };

    private void sendMessages()
    {
        temperature = 20.0 + Math.random() * 10;
        humidity = 30.0 + Math.random() * 20;
        msgStr = "\"temperature\":" + String.format("%.2f",temperature) + ", \"humidity\":" + String.format("%.2f",humidity);
        try
        {
            sendMessage = new Message(msgStr);
            sendMessage.setProperty("temperatureAlert", temperature > 28 ? "true" : "false");
            sendMessage.setMessageId(java.util.UUID.randomUUID().toString());
            System.out.println(msgStr);
            EventCallback eventCallback = new EventCallback();
            client.sendEventAsync(sendMessage, eventCallback, 1);
            msgSentCount++;
            handler.post(updateRunnable);
        }
        catch (Exception e)
        {
            System.err.println("Exception while sending event: " + e.getMessage());
        }
    }

    private void initClient() throws URISyntaxException, IOException
    {
        client = new DeviceClient(connString, protocol);

        try
        {
            client.open();
            MessageCallback callback = new MessageCallback();
            client.setMessageCallback(callback, null);
        }
        catch (Exception e2)
        {
            System.err.println("Exception while opening IoTHub connection: " + e2.getMessage());
            client.closeNow();
            System.out.println("Shutting down...");
        }
    }

    class EventCallback implements IotHubEventCallback
    {
        public void execute(IotHubStatusCode status, Object context)
        {
            Integer i = (Integer) context;
            System.out.println("IoT Hub responded to message " + i.toString()
                    + " with status " + status.name());

            if((status == IotHubStatusCode.OK) || (status == IotHubStatusCode.OK_EMPTY))
            {
                TextView txtReceiptsConfirmedVal = findViewById(R.id.txtReceiptsConfirmedVal);
                receiptsConfirmedCount++;
                txtReceiptsConfirmedVal.setText(Integer.toString(receiptsConfirmedCount));
            }
            else
            {
                TextView txtSendFailuresVal = findViewById(R.id.txtSendFailuresVal);
                sendFailuresCount++;
                txtSendFailuresVal.setText(Integer.toString(sendFailuresCount));
            }
        }
    }

    class MessageCallback implements com.microsoft.azure.sdk.iot.device.MessageCallback
    {
        public IotHubMessageResult execute(Message msg, Object context)
        {
            System.out.println(
                    "Received message with content: " + new String(msg.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));
            msgReceivedCount++;
            TextView txtMsgsReceivedVal = findViewById(R.id.txtMsgsReceivedVal);
            txtMsgsReceivedVal.setText(Integer.toString(msgReceivedCount));
            txtLastMsgReceivedVal.setText("[" + new String(msg.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET) + "]");
            return IotHubMessageResult.COMPLETE;
        }
    }
}
