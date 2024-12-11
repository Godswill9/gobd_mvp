package com.fr3ts0n.ecu.gui.androbd;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;


import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SignupActivity extends Activity {

    private EditText usernameEditText, userEmailEditText, passwordEditText, userPhoneEditText;
    private Button signupButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize views
        usernameEditText = findViewById(R.id.username);
        userEmailEditText = findViewById(R.id.userEmail);
        passwordEditText = findViewById(R.id.password);
        userPhoneEditText = findViewById(R.id.phone);
        signupButton = findViewById(R.id.signUp);
        progressBar = findViewById(R.id.loading);

        // Add text watchers to enable/disable signup button
        usernameEditText.addTextChangedListener(createTextWatcher());
        passwordEditText.addTextChangedListener(createTextWatcher());
        userEmailEditText.addTextChangedListener(createTextWatcher());
        userPhoneEditText.addTextChangedListener(createTextWatcher());

        // Set signup button click listener
        signupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performSignup();
            }
        });
    }

    private TextWatcher createTextWatcher() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                updateSignupButtonState();
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        };
    }

    private void updateSignupButtonState() {
        String username = usernameEditText.getText().toString().trim();
        String email = userEmailEditText.getText().toString().trim();
        String phone = userPhoneEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Enable button only if all fields are filled in
        signupButton.setEnabled(!username.isEmpty() && !email.isEmpty() && !phone.isEmpty() && !password.isEmpty());
    }

    private void performSignup() {
        String username = usernameEditText.getText().toString().trim();
        String email = userEmailEditText.getText().toString().trim();
        String phone = userPhoneEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Basic field validation
        if (username.isEmpty() || email.isEmpty() || phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(SignupActivity.this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate email format
        if (!email.contains("@") || !email.contains(".")) {
            Toast.makeText(SignupActivity.this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate phone number format
        if (phone.length() != 11) {
            Toast.makeText(SignupActivity.this, "Please enter a valid phone number.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Perform the signup using HttpURLConnection
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    URL url = new URL("https://aimechanic.asoroautomotive.com/api/appsignup"); // Replace with your actual API URL

                    // Open a connection to the API endpoint
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                    // Set the request method to POST
                    urlConnection.setRequestMethod("POST");

                    // Set the request headers
                    urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    // Enable input/output streams
                    urlConnection.setDoOutput(true);

                    // Create the POST data
                    // Create the POST data with additional fields initialized as empty strings
                    String data = "username=" + username +
                            "&email=" + email +
                            "&password=" + password +
                            "&phoneNumber=" + phone +
                            "&subscription_status=in-active" +
                            "&car_make=" + "" +        // Empty string for car_make
                            "&car_model=" + "" +       // Empty string for car_model
                            "&car_year=" + "" +        // Empty string for car_year
                            "&engine_type=" + "";

                    // Send the POST data to the server
                    DataOutputStream outputStream = new DataOutputStream(urlConnection.getOutputStream());
                    outputStream.writeBytes(data);
                    outputStream.flush();
                    outputStream.close();

                    // Get the response code from the server
                    int responseCode = urlConnection.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // Server responded with success, handle the response
                        BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                        String inputLine;
                        StringBuilder response = new StringBuilder();
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();

                        // On successful signup, show a success message and navigate to the LoginActivity
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                                signupButton.setVisibility(View.VISIBLE);
                                Toast.makeText(SignupActivity.this, "Signup successful!", Toast.LENGTH_SHORT).show();
//                                startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                                finish();
                            }
                        });
                    } else {
                        // Handle failure response
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                                signupButton.setVisibility(View.VISIBLE);
                                Toast.makeText(SignupActivity.this, "Signup failed. Please try again.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    // Disconnect from the server
                    urlConnection.disconnect();

                } catch (Exception e) {
                    // Handle any exceptions that occurred during the request
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            signupButton.setVisibility(View.VISIBLE);
                            Toast.makeText(SignupActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();

        // Show progress bar while the signup process is running
        progressBar.setVisibility(View.VISIBLE);
        signupButton.setVisibility(View.INVISIBLE);
    }

    // Inflate the menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present
        getMenuInflater().inflate(R.menu.reg_menu, menu);
        return true;
    }

    // Handle item selections
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.homePage:
                // Navigate to Homepage (MainActivity)
                Intent intent = new Intent(SignupActivity.this, MainActivity.class);
                startActivity(intent);
                finish();  // Close the login activity
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
