package com.fr3ts0n.ecu.gui.androbd;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends Activity {

    private EditText usernameEditText, passwordEditText;
    private Button loginButton;
    private ProgressBar progressBar;

    // SharedPreferences to store user data like JWT
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);


        // Find views by ID
        usernameEditText = findViewById(R.id.loginUsername);
        passwordEditText = findViewById(R.id.loginPassword);
        loginButton = findViewById(R.id.loginBut);
        progressBar = findViewById(R.id.loginLoading);

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String jwtToken = sharedPreferences.getString("accessToken", null);
        if (jwtToken != null) {
            // User is already logged in
            Toast.makeText(LoginActivity.this, "User is already logged in", Toast.LENGTH_SHORT).show();
            // Proceed to the main screen directly
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish(); // Close login activity
        } else {
            // User is not logged in
            Toast.makeText(LoginActivity.this, "Please log in", Toast.LENGTH_SHORT).show();
            // Show login screen or show login dialog
            // You might want to start the LoginActivity here if user is not logged in
        }

        // Add text watchers to both EditTexts to enable/disable login button
        usernameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                updateLoginButtonState();
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                updateLoginButtonState();
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

        // Set up login button click listener
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performLogin();
            }
        });
    }

    private void updateLoginButtonState() {
        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        loginButton.setEnabled(!username.isEmpty() && !password.isEmpty());
    }

    private void performLogin() {
        // Get the entered username and password
        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Log the entered values (for debugging purposes)
        Log.d("LoginActivity", "Username: " + username);
        Log.d("LoginActivity", "Password: " + password);

        // Check if the fields are not empty
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(LoginActivity.this, "Please enter both username and password", Toast.LENGTH_SHORT).show();
            return;
        }

        // Simulate network call (login)
        progressBar.setVisibility(View.VISIBLE);
        loginButton.setVisibility(View.INVISIBLE);

        // Perform login in a background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result = loginRequest(username, password);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        loginButton.setVisibility(View.VISIBLE);

                        if (result != null) {
                            try {
                                // Parse the JSON response
                                JSONObject jsonResponse = new JSONObject(result);

                                // Extract the 'status' value from the JSON response
                                String message = jsonResponse.getString("message");

                                // Check if login was successful
                                if ("success".equals(message)) {
                                    // Extract the access token from the response
                                    String accessToken = jsonResponse.getString("accessToken");

                                    // Save the access token to SharedPreferences
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putString("accessToken", result);
                                    editor.apply(); // Apply changes asynchronously

                                    // Handle success response, proceed to the main activity
                                    Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
//                                    finish(); // Close the current activity
                                } else {
                                    // Handle various failure messages
                                    switch (message) {
                                        case "incorrect password":
                                            Toast.makeText(LoginActivity.this, "Incorrect password. Please try again.", Toast.LENGTH_SHORT).show();
                                            break;
                                        case "user not verified":
                                            Toast.makeText(LoginActivity.this, "User not verified. Please verify your email.", Toast.LENGTH_SHORT).show();
                                            break;
                                        case "user not subscribed":
                                            Toast.makeText(LoginActivity.this, "User not subscribed. Please subscribe to proceed.", Toast.LENGTH_SHORT).show();
                                            break;
                                        case "user not found":
                                            Toast.makeText(LoginActivity.this, "User not found. Please check your credentials or sign up.", Toast.LENGTH_SHORT).show();
                                            break;
                                        default:
                                            Toast.makeText(LoginActivity.this, "Login failed. Please try again.", Toast.LENGTH_SHORT).show();
                                            break;
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(LoginActivity.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(LoginActivity.this, "Login failed. Try again.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }


    // Method to send HTTP request and get the response
    private String loginRequest(String email, String password) {
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;
        String result = null;

        try {
            // Set the URL to your backend API login endpoint
            URL url = new URL("https://aimechanic.asoroautomotive.com/api/applogin"); // Correct endpoint
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setRequestProperty("Content-Type", "application/json");

            // Prepare the login data to be sent in JSON format with email instead of username
            String jsonInputString = String.format("{\"email\": \"%s\", \"password\": \"%s\"}", email, password);

            // Log the request data for debugging
            Log.d("LoginRequest", "Request JSON: " + jsonInputString);

            // Send the POST request with the login data
            urlConnection.setDoOutput(true);
            try (OutputStream os = urlConnection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Get the response code
            int responseCode = urlConnection.getResponseCode();
            Log.d("LoginRequest", "Response Code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read the response from the input stream
                reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                // Log the response
                Log.d("LoginRequest", "Response: " + response.toString());

                // The response should contain the JWT token or an error message
                result = response.toString();
            } else {
                // Handle non-OK response code (e.g., 400, 500)
                Log.e("LoginRequest", "Error: " + responseCode);
                result = "Error: " + responseCode;
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("LoginRequest", "Exception: " + e.getMessage());
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }


    // Method to handle the click event for the "Sign up" TextView
    public void onSignUpClick(View view) {
        // Start the SignupActivity to navigate to the sign-up screen
        Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
        startActivity(intent);
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
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();  // Close the login activity
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
