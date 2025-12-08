package com.example.localnow.model;

public class TokenRequest {
    private String fcm_token;

    public TokenRequest(String fcm_token) {
        this.fcm_token = fcm_token;
    }

    public String getFcmToken() {
        return fcm_token;
    }

    public void setFcmToken(String fcm_token) {
        this.fcm_token = fcm_token;
    }
}
