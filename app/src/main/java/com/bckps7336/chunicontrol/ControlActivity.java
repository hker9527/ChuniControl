package com.bckps7336.chunicontrol;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.util.HashMap;

public class ControlActivity extends AppCompatActivity {
    private final int KEY_COUNT = 16;
    ClientThread clientThread;
    ControlCallback controlCallback;
    private Button btnMain;
    private int airFingerIndex = -1; // Trace for finger that do air
    private VelocityTracker velocityTracker = null;
    private int airThreshold;
    private HashMap<Integer, Integer> keyMap;
    private SharedPreferences sharedPreferences;

    private boolean longPressedExit = false;

    private boolean isLockTask;
    private boolean isLockBack;
    private boolean isLockHome;

    private boolean isLockOrientation;
    private boolean lockOrientationRotation;

    private int safeQueueGet(int k) {
        if (keyMap.containsKey(k)) return keyMap.get(k);
        else return -1;
    }

    private void makeToast(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    private void showChooseHomeDialog() {
        PackageManager p = getPackageManager();
        ComponentName cN = new ComponentName(getApplicationContext(), FakeHome.class);
        p.setComponentEnabledSetting(cN, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        Intent selector = new Intent(Intent.ACTION_MAIN);
        selector.addCategory(Intent.CATEGORY_HOME);
        startActivity(selector);

        p.setComponentEnabledSetting(cN, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    private boolean isAppNotHome() {
        PackageManager localPackageManager = getPackageManager();
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.addCategory("android.intent.category.HOME");
        String str = localPackageManager.resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY).activityInfo.packageName;
        return !str.equals(getPackageName());
    }

    private void loadDataFromSP() {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.contains("addr")) {
            String host = sharedPreferences.getString("addr", "");
            controlCallback = new ControlCallback(this);
            clientThread = new ClientThread(controlCallback, host);
            clientThread.start();
        }

        if (sharedPreferences.contains("airThreshold")) {
            int value = sharedPreferences.getInt("airThreshold", 50);
            airThreshold = value * 100;
        }

        isLockHome = sharedPreferences.getBoolean("lockHome", false);
        isLockTask = sharedPreferences.getBoolean("lockTask", false);
        isLockBack = sharedPreferences.getBoolean("lockBack", false);

        isLockOrientation = sharedPreferences.getBoolean("lockOrientation", false);
    }

    @SuppressLint({"ClickableViewAccessibility", "SourceLockedOrientationActivity"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadDataFromSP();

        // Set the UI to be borderless
        View decorView = getWindow().getDecorView();

        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LOW_PROFILE |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        if (isLockOrientation) {
            Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

            if (lockOrientationRotation) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            }
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        // Callback from server thread
        controlCallback = new ControlCallback(this);

        // A map for storing current keys corresponding to fingers
        keyMap = new HashMap<>();

        btnMain = findViewById(R.id.main);
        btnMain.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (clientThread == null) return false;
                if (!clientThread.isConnected()) return false;

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
                        keyMap.put(fingerId, index);
                        clientThread.sendKey(true, index);
                        Log.d("KeyAction", "DOWN " + fingerId);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                    case MotionEvent.ACTION_CANCEL:
                        velocityTracker.clear();
                        keyMap.remove(fingerId);
                        clientThread.sendKey(false, index);
                        Log.d("KeyAction", "UP " + fingerId);
                        if (airFingerIndex == fingerId) {
                            Log.d("KeyAction", "AIR TAP OFF");
                            airFingerIndex = -1;
                            clientThread.sendAir(false);
                            btnMain.setBackgroundColor(getResources().getColor(R.color.colorMainOff));
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
                                if (airFingerIndex == _i)
                                    continue; // Ignore movement of airing finger
                                if (oldIndex != -1) clientThread.sendKey(false, oldIndex);
                                clientThread.sendKey(true, index);
                                keyMap.put(i, index);
                            }

                            velocityTracker.computeCurrentVelocity(1000);

                            double vy = velocityTracker.getYVelocity(_i);

                            if (airFingerIndex == -1 && vy < -airThreshold) { // No air ongoing
                                Log.d("KeyAction", "AIR SWIPE ON");
                                airFingerIndex = _i;
                                clientThread.sendKey(false, index);
                                clientThread.sendAir(true);
                                btnMain.setBackgroundColor(getResources().getColor(R.color.colorMainOn));
                            } else if (airFingerIndex == _i && vy > airThreshold) { // Air ongoing and same finger
                                Log.d("KeyAction", "AIR SWIPE OFF");
                                airFingerIndex = -1;
                                clientThread.sendKey(true, index);
                                clientThread.sendAir(false);
                                btnMain.setBackgroundColor(getResources().getColor(R.color.colorMainOff));
                            }
                        }
                        break;
                }
                return true;
            }
        });
    }


    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (longPressedExit) {
                finishAffinity();
            } else {
                longPressedExit = true;
                makeToast("Long press one more time to exit.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                longPressedExit = false;
                            }
                        }, 5000); // Reset after 5 seconds
                    }
                });
            }
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (isLockBack) {
            makeToast("Locked back button! Press menu to unlock. To quit app, long press.");
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isLockTask) {
            makeToast("Locked task button! Press menu to unlock.");

            ActivityManager activityManager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
            activityManager.moveTaskToFront(getTaskId(), 0);
        }
    }

    @Override
    public void onResume() { // Check if app is being set as home screen
        if (isAppNotHome() && isLockHome) showChooseHomeDialog();
        super.onResume();
    }

    public class ControlCallback extends NetworkCallback {
        private ControlActivity parent;
        private TextView[] btnPlaceholderArray;
        /*
        private int prevColor = -1;
        private int colorArray[] = new int[16];
        private final int COLOR_NONE = Color.rgb(0, 0, 0);
         */

        public ControlCallback(ControlActivity parent) {
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
