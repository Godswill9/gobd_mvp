package com.fr3ts0n.ecu.gui.androbd;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri; // Import Uri
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;


public class WebViewActivity extends Activity {
    private WebView webView;
    private CookieManager cookieManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);

        // Safely set the title
        if (getActionBar() != null) {
            getActionBar().setTitle("GOBD");
        }


        // Initialize WebView
        webView = findViewById(R.id.myWebView);
        cookieManager = CookieManager.getInstance();

        // Retrieve data from SharedPreferences
        SharedPreferences sharedPref = getSharedPreferences("carDetails", MODE_PRIVATE);
        String carMake = sharedPref.getString("carMake", "");
        String carBrand = sharedPref.getString("carBrand", "");
        String carYear = sharedPref.getString("carYear", "");
        String carEngineType = sharedPref.getString("carEngineType", "");
        String faultCode = sharedPref.getString("faultCode", "");
        String userToken = sharedPref.getString("userToken", "");

        // Construct the URL using Uri.Builder
        Uri uri = new Uri.Builder()
                .scheme("https")
                .authority("gobd-users.onrender.com")
                .appendPath(carMake + '&' + carBrand + '&' + carYear + '&' + carEngineType + '&' + faultCode + '&' + userToken )
                .build();

        // Convert Uri to String
        String url = uri.toString();

        if (!url.isEmpty()) {
            // Enable cookies
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(webView, true); // Accept third-party cookies

            webView.setHapticFeedbackEnabled(true);
            int progress = webView.getProgress();
            Log.d("WebViewActivity", "Loading progress: " + progress + "%");

            webView.loadUrl(url);
            webView.setWebViewClient(new WebViewClient());


            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setAllowUniversalAccessFromFileURLs(true);
            webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
            webSettings.setAllowFileAccess(true); // Allow file access
            webSettings.setAllowContentAccess(true); // Allow content access

//            // Save the userId to localStorage inside WebView
//            String script = "window.localStorage.setItem('jwt_user', '" + userToken + "');";
//            webView.evaluateJavascript(script, null); // Inject the JavaScript code to store the userId in localStorage

            // Set the userId in cookies
//            String cookieString = "jwt_user=" + userToken + "; Path=/; HttpOnly"; // Setting the cookie
//            cookieManager.setCookie(url, cookieString); // Setting cookie for the WebView URL

        } else {
            Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        int progress = webView.getProgress();
        Log.d("WebViewActivity", "Loading progress: " + progress + "%");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    // Inflate the menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webview_menu, menu); // Inflate your menu here
        return true;
    }

    // Handle menu item selection
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_reload:
                webView.reload(); // Reload the WebView
                return true;
            case R.id.action_home:
                navigateToHome(); // Navigate back to home
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void navigateToHome() {
        // Retrieve car details from SharedPreferences
        SharedPreferences sharedPref = getSharedPreferences("carDetails", MODE_PRIVATE);
        String carMake = sharedPref.getString("carMake", "");
        String carBrand = sharedPref.getString("carBrand", "");
        String carYear = sharedPref.getString("carYear", "");
        String carEngineType = sharedPref.getString("carEngineType", "");
        String faultCode = sharedPref.getString("faultCode", "");

        // Construct the URL using Uri.Builder
        Uri uri = new Uri.Builder()
                .scheme("https")
                .authority("gobd-users.onrender.com")
                .appendPath(carMake + '&' + carBrand + '&' + carYear + '&' + carEngineType + '&' + faultCode)
                .build();

        // Convert Uri to String
        String url = uri.toString();

        if (!url.isEmpty()) {
            webView.loadUrl(url);
            webView.setWebViewClient(new WebViewClient());

            setLocalStorage("car_make", carMake);
            setLocalStorage("car_model", carBrand);
            setLocalStorage("car_year", carYear);
            setLocalStorage("engine_type", carEngineType);
            setLocalStorage("fault_code", faultCode);


            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        } else {
            Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    private void setLocalStorage(String key, String value) {
        String script = "localStorage.setItem('" + key + "', '" + value + "');";
        webView.evaluateJavascript(script, null);
    }

//    private void getLocalStorage(String key) {
//        String script = "localStorage.getItem('" + key + "');";
//        webView.evaluateJavascript(script, new ValueCallback<String>() {
//            @Override
//            public void onReceiveValue(String value) {
//                // Handle the retrieved value
//                // For example, log it
//                Log.d("WebViewActivity", "Value from local storage: " + value);
//            }
//        });
//    }
}
