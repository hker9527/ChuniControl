package com.bckps7336.chunicontrol;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
    MainCallback mainCallback;
    private Button main;
    private int airIndex = -1; // Trace for finger that do air
    private VelocityTracker velocityTracker = null;
    private int AIR_THRESHOLD;
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

    private boolean checkNetwork() {
        if (networkThread == null) {
            dialogHost.show();
            return false;
        }

        if (!networkThread.isConnected()) {
            //sharedPreferences.edit().remove("ip").apply();
            networkThread = null;
            Toast.makeText(getApplicationContext(), "Connection failed! \n - Check your host firewall settings.\n - Check if the game is on.\n - Check if the host is correct.", Toast.LENGTH_LONG).show();
            dialogHost.show();
            return false;
        }

        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainCallback = new MainCallback(this);

        queue = new HashMap<>();

        main = findViewById(R.id.main);
        main.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!checkNetwork()) return false;

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

                            if (airIndex == -1 && vy < -AIR_THRESHOLD) { // No air ongoing
                                Log.d("KeyAction", "AIR SWIPE ON");
                                airIndex = _i;
                                networkThread.sendKey(false, index);
                                networkThread.sendAir(true);
                                main.setBackgroundColor(getResources().getColor(R.color.colorMainOn));
                            } else if (airIndex == _i && vy > AIR_THRESHOLD) { // Air ongoing and same finger
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

        builder.setPositiveButton("OK", null);

        dialogHost = builder.create();

        dialogHost.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String host = txtHost.getText().toString();
                        networkThread = new NetworkThread(mainCallback, host);
                        networkThread.start();
                        if (checkNetwork()) {
                            dialog.dismiss();
                            sharedPreferences.edit().putString("ip", txtHost.getText().toString()).apply();
                        }
                    }
                });
            }
        });
        builder = new AlertDialog.Builder(this);
        builder.setTitle("Air Sensitivity");

        seekAir = new SeekBar(this);
        seekAir.setMax(100);
        seekAir.setProgress(AIR_THRESHOLD / 100);
        seekAir.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress * 100;
                dialogAir.setTitle(String.valueOf(value));
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
                AIR_THRESHOLD = value * 100;
            }
        });

        dialogAir = builder.create();

        sharedPreferences = getSharedPreferences("data", MODE_PRIVATE);
        if (sharedPreferences.contains("ip")) {
            String host = sharedPreferences.getString("ip", "");
            networkThread = new NetworkThread(mainCallback, host);
            networkThread.start();
            txtHost.setText(host);
            checkNetwork();
        } else {
            dialogHost.show();
        }

        if (sharedPreferences.contains("airThreshold")) {
            int value = sharedPreferences.getInt("airThreshold", 50);
            AIR_THRESHOLD = value * 100;
            seekAir.setProgress(value);
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
            case R.id.btnUsbNet:
                Intent tetherSettings = new Intent();
                tetherSettings.setClassName("com.android.settings", "com.android.settings.TetherSettings");
                startActivity(tetherSettings);
                break;
            default: // All network-required functions
                if (!checkNetwork()) break;

                switch (item.getItemId()) {
                    case R.id.btnSendCoin:
                        networkThread.sendCoin();
                        break;
                    case R.id.btnSendService:
                        networkThread.sendService();
                        break;
                    case R.id.btnSendST:
                        networkThread.sendService();
                        networkThread.sendTest();
                        break;
                    case R.id.btnSendTest:
                        networkThread.sendTest();
                        break;
                    case R.id.btnSendShut:
                        networkThread.sendShutdown();
                        this.finishAffinity();
                        break;
                    default:
                        return super.onOptionsItemSelected(item);
                }
                break;
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

    public class MainCallback {
        private MainActivity parent;
        private TextView[] btnPlaceholderArray;
        /*
        private int prevColor = -1;
        private int colorArray[] = new int[16];
        private final int COLOR_NONE = Color.rgb(0, 0, 0);
         */

        public MainCallback(MainActivity parent) {
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
                    switch (data[1]) {
                        case Constant.TYPE_LED_SET:
                            int target = 0xf - data[2];
                            int color = Color.rgb(data[3] & 0xFF, data[4] & 0xFF, data[5] & 0xFF);

                            // TODO: Make fadein & fadeout effect v
                            /*
                            colorArray[target] = color;
                            if (color == prevColor) { // Continous block
                                btnPlaceholderArray[target].setBackgroundColor(color);
                            } else if (target == 0 && color != 0) { // Key 0
                                int[] fadeIn = {Color.TRANSPARENT, color};
                                GradientDrawable gdIn = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, fadeIn);
                                gdIn.setCornerRadius(0);
                                btnPlaceholderArray[target].setBackground(gdIn);
                            } else if (color != COLOR_NONE) { // New block
                                int[] fo = {prevColor, Color.TRANSPARENT};
                                GradientDrawable gdOut = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, fo);
                                gdOut.setCornerRadius(0);
                                btnPlaceholderArray[target - 1].setBackground(gdOut);

                                int[] fi = {Color.TRANSPARENT, color};
                                GradientDrawable gdIn = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, fi);
                                gdIn.setCornerRadius(0);
                                btnPlaceholderArray[target].setBackground(gdIn);
                            }
                            prevColor = color;*/

                            int[] colors = {Color.TRANSPARENT, color};
                            GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
                            gradientDrawable.setCornerRadius(0);
                            btnPlaceholderArray[target].setBackground(gradientDrawable);
                            break;
                        case Constant.TYPE_PONG:
                            break;
                        default:
                            break;
                    }
                }
            });
        }
    }
}
