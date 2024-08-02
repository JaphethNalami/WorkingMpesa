package com.example.workingmpesa;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.workingmpesa.Model.STKCallbackResponse;
import com.example.workingmpesa.Services.DarajaApiClient;
import com.example.workingmpesa.databinding.ActivityMainBinding;
import com.example.workingmpesa.Model.AccessToken;
import com.example.workingmpesa.Model.STKPush;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private DarajaApiClient mApiClient;
    private ProgressDialog mProgressDialog;
    private ActivityMainBinding binding;
    private String accessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (binding == null || binding.getRoot() == null) {
            throw new RuntimeException("Error inflating binding or getting root view");
        }

        mProgressDialog = new ProgressDialog(this);

        mApiClient = new DarajaApiClient();
        mApiClient.setIsDebug(true);

        binding.btnPay.setOnClickListener(this);

        getAccessToken();
    }

    public void getAccessToken() {
        mApiClient.setGetAccessToken(true);
        mApiClient.mpesaService().getAccessToken().enqueue(new retrofit2.Callback<AccessToken>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<AccessToken> call, @NonNull retrofit2.Response<AccessToken> response) {
                if (response.isSuccessful() && response.body() != null) {
                    accessToken = response.body().accessToken;
                    mApiClient.setAuthToken(accessToken);
                }
            }

            @Override
            public void onFailure(@NonNull retrofit2.Call<AccessToken> call, @NonNull Throwable t) {
                Timber.e(t, "Failed to get access token");
            }
        });
    }

    @Override
    public void onClick(View view) {
        if (view == binding.btnPay) {
            String phone_number = binding.phone.getText().toString();
            String amount = binding.amount.getText().toString();
            performSTKPush(phone_number, amount);
        }
    }

    public void performSTKPush(String phone_number, String amount) {
        mProgressDialog.setMessage("Processing your request");
        mProgressDialog.setTitle("Please Wait...");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.show();

        String timestamp = Utils.getTimestamp();
        String toEncode = "174379" + "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919" + timestamp;
        byte[] byteArray = toEncode.getBytes(StandardCharsets.UTF_8);
        String encodedPassword = Base64.getEncoder().encodeToString(byteArray);

        STKPush stkPush = new STKPush(
                "174379",
                encodedPassword,
                timestamp,
                "CustomerPayBillOnline",
                Integer.parseInt(amount),
                "254715798225",
                "174379",
                Utils.sanitizePhoneNumber(phone_number),
                "https://mydomain.com/path",
                "Smart Sales Tracker",
                "Payment of X"
        );

        mApiClient.setGetAccessToken(false);
        mApiClient.mpesaService().sendPush(stkPush).enqueue(new retrofit2.Callback<STKPush>() {
            @Override
            public void onResponse(@NonNull retrofit2.Call<STKPush> call, @NonNull retrofit2.Response<STKPush> response) {
                mProgressDialog.dismiss();
                if (response.isSuccessful()) {
                    Timber.d("post submitted to API. %s", response.body());

                    Toast.makeText(MainActivity.this, "Request sent. Please complete payment on your phone.", Toast.LENGTH_SHORT).show();

                    //toast the checkout request ID
                    Toast.makeText(MainActivity.this, "Checkout Request ID: " + response.body().getCheckoutRequestID(), Toast.LENGTH_SHORT).show();

                    new Handler().postDelayed(() -> checkTransactionStatus(response.body().getCheckoutRequestID()), 3000);

                } else {
                    try {
                        if (response.errorBody() != null) {
                            Timber.e("Response %s", response.errorBody().string());
                            Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Timber.e(e, "Error processing response");
                    }
                }
            }

            @Override
            public void onFailure(@NonNull retrofit2.Call<STKPush> call, @NonNull Throwable t) {
                mProgressDialog.dismiss();
                Timber.e(t, "Request failed");
            }
        });
    }

    public void checkTransactionStatus(String checkoutRequestID) {

        //log this function has been called on the console
        Log.d("MainActivity", "checkTransactionStatus called");

        OkHttpClient client = new OkHttpClient().newBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");
        String requestBodyJson = "{\n" +
                "  \"BusinessShortCode\": \"174379\",\n" +
                "  \"Password\": \"MTc0Mzc5YmZiMjc5ZjlhYTliZGJjZjE1OGU5N2RkNzFhNDY3Y2QyZTBjODkzMDU5YjEwZjc4ZTZiNzJhZGExZWQyYzkxOTIwMjQwODAxMTE0MjU5\",\n" +
                "  \"Timestamp\": \"" + Utils.getTimestamp() + "\",\n" +
                "  \"CheckoutRequestID\": \"" + checkoutRequestID + "\"\n" +
                "}";
        RequestBody body = RequestBody.create(mediaType, requestBodyJson);
        Request request = new Request.Builder()
                .url("https://sandbox.safaricom.co.ke/mpesa/stkpushquery/v1/query")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        //log the request body
        Log.d("MainActivity", "Request body: " + request);
        Log.d("MainActivity", "Request body: " + requestBodyJson);

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Timber.e(e, "Request to check transaction status failed");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    Timber.d("Transaction status response: %s", responseBody);

                    // Deserialize response
                    Gson gson = new Gson();
                    STKCallbackResponse stkCallbackResponse = gson.fromJson(responseBody, STKCallbackResponse.class);

                    // Handle transaction response on main thread
                    runOnUiThread(() -> handleTransactionResponse(stkCallbackResponse));
                } else {
                    Timber.e("Transaction status response failed: %s", response.body().string());
                }
            }
        });
    }

    public void handleTransactionResponse(STKCallbackResponse response) {
        if (response.getResultCode() == 0) {
            Toast.makeText(this, "Transaction completed successfully", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Transaction failed: " + response.getResultDesc(), Toast.LENGTH_SHORT).show();
        }
    }
}
