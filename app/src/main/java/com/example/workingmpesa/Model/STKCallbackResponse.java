package com.example.workingmpesa.Model;

import com.google.gson.annotations.SerializedName;

public class STKCallbackResponse {

    @SerializedName("ResultCode")
    private int resultCode;
    @SerializedName("ResultDesc")
    private String resultDesc;
    @SerializedName("CheckoutRequestID")
    private String checkoutRequestID;

    public int getResultCode() {
        return resultCode;
    }

    public void setResultCode(int resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultDesc() {
        return resultDesc;
    }

    public void setResultDesc(String resultDesc) {
        this.resultDesc = resultDesc;
    }

    public String getCheckoutRequestID() {
        return checkoutRequestID;
    }

    public void setCheckoutRequestID(String checkoutRequestID) {
        this.checkoutRequestID = checkoutRequestID;
    }
}
