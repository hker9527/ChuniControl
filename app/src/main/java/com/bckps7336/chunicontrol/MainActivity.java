package com.bckps7336.chunicontrol;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private final int KEY_COUNT = 16;
    NetworkThread networkThread;
    ServerHandler serverHandler;
    private Button main;
    private int airIndex = -1; // Trace for finger that do air
    private VelocityTracker velocityTracker = null;
    private int AIR_THERESHOLD;
    private EditText txtHost;
    private AlertDialog dialogHost;
    private SeekBar seekAir;
    private AlertDialog dialogAir;
    private HashMap<Integer, Integer> queue;
    private SharedPreferences sharedPreferences;
    private boolean lock = true;

    private int safeQueueGet(int k) {
        if (queue.containsKey(k)) return queue.get(k);
        else return -1;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serverHandler = new ServerHandler(this);

        queue = new HashMap<>();

        main = findViewById(R.id.main);
        main.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (networkThread == null) return false;

                int action = event.getAction() & MotionEvent.ACTION_MASK;
                int pointerIndex = (event.getAction() & MotionEvent.ACTION_POINTER_INDEX_MASK) >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                int fingerId = event.getPointerId(pointerIndex);

                double w = v.getWidth();

                double x = event.getX(pointerIndex);

                double d = w / KEY_COUNT;
                int index = Math.min(Math.max(0, (int) (x / d)), KEY_COUNT - 1);

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_POINTER_DOWN:
                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        } else {
                            velocityTracker.clear();
                        }
                        velocityTracker.addMovement(event);
                        queue.put(fingerId, index);
                        networkThread.sendKey(true, index);
                        Log.d("KeyAction", "DOWN " + fingerId);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                    case MotionEvent.ACTION_CANCEL:
                        velocityTracker.clear();
                        queue.remove(fingerId);
                        networkThread.sendKey(false, index);
                        Log.d("KeyAction", "UP " + fingerId);
                        if (airIndex == fingerId) {
                            Log.d("KeyAction", "AIR TAP OFF");
                            airIndex = -1;
                            networkThread.sendAir(false);
                            main.setBackgroundColor(getResources().getColor(R.color.colorMainOff));
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        Log.d("KeyAction", "MOVE");
                        velocityTracker.addMovement(event);
                        int pointerCount = event.getPointerCount();
                        for (int i = 0; i < pointerCount; i++) {
                            int _i = event.getPointerId(i);
                            x = event.getX(i);

                            index = Math.min(Math.max(0, (int) (x / d)), KEY_COUNT - 1);
                            int oldIndex = safeQueueGet(i);
                            if (oldIndex != index) { // Key changed
                                if (airIndex == _i) continue; // Ignore movement of airing finger
                                if (oldIndex != -1) networkThread.sendKey(false, oldIndex);
                                networkThread.sendKey(true, index);
                                queue.put(i, index);
                            }

                            velocityTracker.computeCurrentVelocity(1000);

                            double vy = velocityTracker.getYVelocity(_i);
                            AIR_THERESHOLD = 5000;

                            if (airIndex == -1 && vy < -AIR_THERESHOLD) { // No air ongoing
                                Log.d("KeyAction", "AIR SWIPE ON");
                                airIndex = _i;
                                networkThread.sendKey(false, index);
                                networkThread.sendAir(true);
                                main.setBackgroundColor(getResources().getColor(R.color.colorMainOn));
                            } else if (airIndex == _i && vy > AIR_THERESHOLD) { // Air ongoing and same finger
                                Log.d("KeyAction", "AIR SWIPE OFF");
                                airIndex = -1;
                                networkThread.sendAir(false);
                                main.setBackgroundColor(getResources().getColor(R.color.colorMainOff));
                            }
                        }
                        break;
                }
                return true;
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Host");

        txtHost = new EditText(this);
        builder.setView(txtHost);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String host = txtHost.getText().toString();
                networkThread = new NetworkThread(serverHandler, host);
                networkThread.start();
                if (networkThread.isConnected()) {
                    sharedPreferences.edit().putString("ip", host).apply();
                }
            }
        });

        dialogHost = builder.create();

        builder = new AlertDialog.Builder(this);
        builder.setTitle("Air Sensitivity");

        seekAir = new SeekBar(this);
        seekAir.setMax(100);
        seekAir.setProgress(AIR_THERESHOLD);
        seekAir.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                dialogAir.setTitle(String.valueOf(progress * 500));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        builder.setView(seekAir);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int value = seekAir.getProgress();
                sharedPreferences.edit().putInt("airThreshold", value).apply();
            }
        });

        dialogAir = builder.create();

        sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);
        if (sharedPreferences.contains("ip")) {
            networkThread = new NetworkThread(serverHandler, sharedPreferences.getString("ip", ""));
            networkThread.start();
            if (networkThread.isConnected()) {

            } else {
                sharedPreferences.edit().remove("ip").apply();
                dialogHost.show();
            }
        } else {
            dialogHost.show();
        }

        if (sharedPreferences.contains("airThreshold")) {
            AIR_THERESHOLD = sharedPreferences.getInt("airThreshold", 5000);
        } else {
            sharedPreferences.edit().putInt("airThreshold", 5000).apply();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mymenu, menu);
        return true;
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.btnConnect:
                dialogHost.show();
                break;
            case R.id.btnAir:
                dialogAir.show();
                break;
            case R.id.chkLockBtn:
                boolean status = item.isChecked();
                item.setChecked(!status);
                lock = !status;
                break;
            case R.id.chkLockOrient:
                boolean stat = item.isChecked();
                item.setChecked(!stat);
                if (!stat) {
                    Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

                    int orientation = display.getRotation();

                    if (orientation == Surface.ROTATION_90) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                    }
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                }
                break;
            case R.id.btnSendCoin:
                if (networkThread != null)
                    if (networkThread.isConnected()) networkThread.sendCoin();
                break;
            case R.id.btnSendService:
                if (networkThread != null)
                    if (networkThread.isConnected()) networkThread.sendService();
                break;
            case R.id.btnSendST:
                if (networkThread != null)
                    if (networkThread.isConnected()) networkThread.sendService();
                if (networkThread != null)
                    if (networkThread.isConnected()) networkThread.sendTest();
                break;
            case R.id.btnSendTest:
                if (networkThread != null)
                    if (networkThread.isConnected()) networkThread.sendTest();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (lock) {
            Toast.makeText(getApplicationContext(), "Locked! Press menu to unlock.", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (lock) {
            Toast.makeText(getApplicationContext(), "Locked! Press menu to unlock.", Toast.LENGTH_SHORT).show();
            ActivityManager activityManager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);

            activityManager.moveTaskToFront(getTaskId(), 0);
        }
    }

    public class ServerHandler {
        private MainActivity parent;
        private TextView[] btnPlaceholderArray;

        public ServerHandler(MainActivity parent) {
            super();
            this.parent = parent;
            this.btnPlaceholderArray = new TextView[]{
                    parent.findViewById(R.id.tv0),
                    parent.findViewById(R.id.tv1),
                    parent.findViewById(R.id.tv2),
                    parent.findViewById(R.id.tv3),
                    parent.findViewById(R.id.tv4),
                    parent.findViewById(R.id.tv5),
                    parent.findViewById(R.id.tv6),
                    parent.findViewById(R.id.tv7),
                    parent.findViewById(R.id.tv8),
                    parent.findViewById(R.id.tv9),
                    parent.findViewById(R.id.tva),
                    parent.findViewById(R.id.tvb),
                    parent.findViewById(R.id.tvc),
                    parent.findViewById(R.id.tvd),
                    parent.findViewById(R.id.tve),
                    parent.findViewById(R.id.tvf)
            };
        }

        public void handle(final byte[] data) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // assert data[0] == 0x0 && data[1] == 0x3; // LED_SET
                    int target = data[2];
                    int color = Color.rgb(data[3] & 0xFF, data[4] & 0xFF, data[5] & 0xFF);
                    if (color == Color.BLACK) color = Color.TRANSPARENT;
                    //Log.d("Color", "Slider " + target + ", Color" + color);
                    btnPlaceholderArray[0xf - target] // reverse
                            .setBackgroundColor(color);
                }
            });
        }
    }
}
