package com.fr3ts0n.ecu.gui.androbd;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

public class AboutUs extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_us);  // Set the layout
    }

    public void browseClickedUrl(View view) {
        // Define the URL you want to open
        String url = "https://github.com/fr3ts0n/AndrOBD"; // Replace with the actual URL you want

        // Create an implicit intent to open the URL in a browser
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

        // Check if there is a browser available to handle the intent
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }
}
