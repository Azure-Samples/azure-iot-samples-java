package com.microsoft.azure.sdk.iot.samples.androidsample;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceMethod;
import com.microsoft.azure.sdk.iot.service.devicetwin.MethodResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private final String connString = BuildConfig.ConnectionString;

    Button btnInvoke;

    EditText editTxtSendMsgsIntVal;
    DeviceMethod methodClient;

    private String lastException;

    private int sendMessagesInterval = 5000;
    MethodResult result;

    private final Handler handler = new Handler();

    private static final Long responseTimeout = TimeUnit.SECONDS.toSeconds(200);
    private static final Long connectTimeout = TimeUnit.SECONDS.toSeconds(5);

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnInvoke = findViewById(R.id.btnInvoke);
        btnInvoke.setEnabled(true);

        editTxtSendMsgsIntVal = findViewById(R.id.editTxtSendMsgsIntVal);
    }

    final Runnable exceptionRunnable = new Runnable() {
        public void run() {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage(lastException);
            builder.show();
            System.out.println(lastException);
        }
    };

    final Runnable methodResultRunnable = new Runnable() {
        public void run() {
            Context context = getApplicationContext();
            CharSequence text;
            if (result != null)
            {
                text = "Received Status=" + result.getStatus() + " Payload=" + result.getPayload();
            }
            else
            {
                text = "Received null result";
            }
            int duration = Toast.LENGTH_LONG;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        }
    };

    private void invokeMethod()
    {
        new Thread(new Runnable() {
            public void run()
            {
                result = null;
                Map<String, Object> payload = new HashMap<String, Object>()
                {
                    {
                        put("sendInterval", editTxtSendMsgsIntVal.getText().toString());
                    }
                };

                try
                {
                    methodClient = DeviceMethod.createFromConnectionString(connString);
                    result = methodClient.invoke(BuildConfig.DeviceId, "setSendMessagesInterval", responseTimeout, connectTimeout, payload);

                    if(result == null)
                    {
                        throw new IOException("Method invoke returns null");
                    }
                    else
                    {
                        handler.post(methodResultRunnable);
                    }
                }
                catch (Exception e)
                {
                    lastException = "Exception while trying to invoke direct method: " + e.toString();
                    handler.post(exceptionRunnable);
                }
            }
        }).start();
    }

    public void btnInvokeOnClick(View v)
    {
        invokeMethod();
    }
}
