package cmu.xprize.rthomescreen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class PermissionsActivity extends AppCompatActivity {

    public static final String[] PERMISSIONS = new String[]{
            READ_EXTERNAL_STORAGE,
            WRITE_EXTERNAL_STORAGE
    };

    private SharedPreferences mPrefs;

    private static final String TAG = "PermissionsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_permissions);

        mPrefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);

        if (!hasPermissions(this, PERMISSIONS)) {
            if (hasDenied(this, PERMISSIONS)) {
               requestPermissions(PERMISSIONS);
            } else {
                if (isFirstTimeAsking(PERMISSIONS)) {
                    requestPermissions(PERMISSIONS);
                } else {
                    //Launch App Page with settings
                    Toast.makeText(this, "Please grant the permissions through Settings!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                    finish();
                }
            }
        }
        else {
            launchHomeActivity();
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(context, permission) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean hasDenied(Context context, String... permissions) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context, permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isFirstTimeAsking(String... permissions) {
        if (permissions != null) {
            for (String permission : permissions) {
                if (mPrefs.getBoolean(permission, false)) return false;
            }
        }
        return true;
    }

    public void requestPermissions(String... permissions) {
        if (permissions != null) {
            SharedPreferences.Editor editor = mPrefs.edit();
            for (String permission : permissions) {
                editor.putBoolean(permission, true);
            }
            editor.apply();
            ActivityCompat.requestPermissions(this, permissions, 1001);
        }
    }

    private void launchHomeActivity() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1001 && grantResults.length > 0) {
            Log.e(TAG, "Permission results");
            if (hasPermissions(this, PERMISSIONS)) launchHomeActivity();
            else {
//                Intent intent = new Intent(this, PermissionsActivity.class);
//                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
//                startActivity(intent);
                finish();
            }
        }
    }
}