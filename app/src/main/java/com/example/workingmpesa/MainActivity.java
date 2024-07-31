package com.example.workingmpesa;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.workingmpesa.Services.DarajaApiClient;
import com.example.workingmpesa.databinding.ActivityMainBinding;
import com.example.workingmpesa.Model.AccessToken;
import com.example.workingmpesa.Model.STKPush;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    // Declare variables
    private DarajaApiClient mApiClient;
    private ProgressDialog mProgressDialog;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate the layout using ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Ensure binding is not null before accessing views
        if (binding == null || binding.getRoot() == null) {
            throw new RuntimeException("Error inflating binding or getting root view");
        }

        // Initialize ProgressDialog
        mProgressDialog = new ProgressDialog(this);

        // Initialize API client
        mApiClient = new DarajaApiClient();
        mApiClient.setIsDebug(true);

        // Set click listener for the pay button
        binding.btnPay.setOnClickListener(this);

        // Get access token
        getAccessToken();
    }

    // Method to get access token from API
    public void getAccessToken() {
        mApiClient.setGetAccessToken(true);
        mApiClient.mpesaService().getAccessToken().enqueue(new Callback<AccessToken>() {
            @Override
            public void onResponse(@NonNull Call<AccessToken> call, @NonNull Response<AccessToken> response) {
                if (response.isSuccessful() && response.body() != null) {

                    // Set auth token if request is successful
                    mApiClient.setAuthToken(response.body().accessToken);
                }
            }

            @Override
            public void onFailure(@NonNull Call<AccessToken> call, @NonNull Throwable t) {
                Timber.e(t, "Failed to get access token");
            }
        });
    }

    // Click listener for pay button
    @Override
    public void onClick(View view) {
        if (view == binding.btnPay) {

            // Perform STK push when pay button is clicked
            String phone_number = binding.phone.getText().toString();
            String amount = binding.amount.getText().toString();
            performSTKPush(phone_number, amount);
        }
    }

    // Method to perform STK push
    public void performSTKPush(String phone_number, String amount) {
        // Show progress dialog while processing request
        mProgressDialog.setMessage("Processing your request");
        mProgressDialog.setTitle("Please Wait...");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.show();

        // Generate encoded password for authentication
        String timestamp = Utils.getTimestamp();
        String toEncode = "174379" + "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919" + timestamp;

        // Encode password using Base64
        byte[] byteArray = toEncode.getBytes(StandardCharsets.UTF_8);
        String encodedPassword;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            encodedPassword = Base64.getEncoder().encodeToString(byteArray);
        } else {
            encodedPassword = android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP);
        }

        // Create STKPush object with required parameters
        STKPush stkPush = new STKPush(
                "174379",  // BusinessShortCode
                encodedPassword,  // Password
                timestamp,  // Timestamp
                "CustomerPayBillOnline",  // TransactionType
                Integer.parseInt(amount),  // Amount
                "254715798225",  // PartyA
                "174379",  // PartyB
                Utils.sanitizePhoneNumber(phone_number),  // PhoneNumber


                "https://mydomain.com/path",  // CallBackURL
                "Smart Sales Tracker",  // AccountReference
                "Payment of X"  // TransactionDesc
        );

        // Disable access token retrieval after first call
        mApiClient.setGetAccessToken(false);

        // Make API call to perform STK push
        mApiClient.mpesaService().sendPush(stkPush).enqueue(new Callback<STKPush>() {
            @Override
            public void onResponse(@NonNull Call<STKPush> call, @NonNull Response<STKPush> response) {

                // Dismiss progress dialog after API call completes
                mProgressDialog.dismiss();
                try {
                    if (response.isSuccessful()) {

                        // Log success message if request is successful
                        Timber.d("post submitted to API. %s", response.body());

                        // Display success message
                        Toast.makeText(MainActivity.this, "Success", Toast.LENGTH_SHORT).show();
                    } else {
                        if (response.errorBody() != null) {

                            // Log error message if request fails
                            Timber.e("Response %s", response.errorBody().string());

                            // Display error message
                            Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    Timber.e(e, "Error processing response");
                }
            }

            @Override
            public void onFailure(@NonNull Call<STKPush> call, @NonNull Throwable t) {

                // Dismiss progress dialog if request fails
                mProgressDialog.dismiss();
                Timber.e(t, "Request failed");
            }
        });
    }
}