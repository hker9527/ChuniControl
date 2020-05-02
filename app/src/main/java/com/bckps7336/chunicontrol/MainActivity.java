package com.bckps7336.chunicontrol;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class MainActivity extends AppCompatActivity {
    private static ClientThread clientThread;
    private SharedPreferences sharedPreferences;
    private static Context mContext;
    private static MainCallback mainCallback;

    // Data to pass to ControlActivity
    private static String host;
    private static boolean isLockTask;
    private static boolean isLockBack;
    private static boolean isLockHome;

    private static void makeToast(String s) {
        Toast.makeText(mContext, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mContext = getApplicationContext();
        mainCallback = new MainCallback();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        host = sharedPreferences.getString("addr", "");
        if (!host.equals("")) {
            clientThread = new ClientThread(mainCallback, host);
            clientThread.start();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) { // Get back button to work
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this, ControlActivity.class);
                clientThread.stopIt();
                startActivity(intent);
                return false;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            Preference.OnPreferenceClickListener onPreferenceClickListener = new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if (clientThread != null) {
                        if (clientThread.isConnected()) {
                            switch (preference.getKey()) {
                                case "ping":
                                    clientThread.sendPing();
                                    break;
                                case "coin":
                                    clientThread.sendCoin();
                                    break;
                                case "service":
                                    clientThread.sendService();
                                    break;
                                case "test":
                                    clientThread.sendTest();
                                    break;
                                case "st":
                                    clientThread.sendService();
                                    clientThread.sendTest();
                                    break;
                                case "shutdown":
                                    clientThread.sendShutdown();
                                    break;
                            }
                        } else {
                            makeToast("Not connected!");
                        }
                    } else {
                        makeToast("Please connect first!");
                    }
                    return false;
                }
            };

            findPreference("ping").setOnPreferenceClickListener(onPreferenceClickListener);
            findPreference("coin").setOnPreferenceClickListener(onPreferenceClickListener);
            findPreference("service").setOnPreferenceClickListener(onPreferenceClickListener);
            findPreference("test").setOnPreferenceClickListener(onPreferenceClickListener);
            findPreference("st").setOnPreferenceClickListener(onPreferenceClickListener);
            findPreference("shutdown").setOnPreferenceClickListener(onPreferenceClickListener);

            findPreference("addr").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (clientThread != null) clientThread.stopIt();
                    clientThread = null;
                    clientThread = new ClientThread(mainCallback, (String) newValue);
                    clientThread.start();
                    if (!clientThread.isConnected()) {
                        makeToast("Connection failed! \n - Check your host firewall settings.\n - Check if the game is on.\n - Check if the host is correct.");
                        return false;
                    } else {
                        //makeToast("Connected! Ping: " + clientThread.getPing() + "ms");
                        host = (String) newValue;
                        return true;
                    }
                }
            });
        }
    }

    public class MainCallback extends NetworkCallback {
        public void handle(final byte[] data) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    makeToast("Latency: " + clientThread.getPing() + "ms");
                }
            });
        }
    }
}