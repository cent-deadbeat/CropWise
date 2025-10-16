package com.example.soilmate;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

public interface ThingsBoardApi {
    @GET("api/plugins/telemetry/DEVICE/b668ad50-0b09-11f0-ac5d-b39d8f8876b0/values/timeseries")
    Call<JsonObject> getTelemetryData(
            @Path("deviceId") String deviceId,
            @Header("X-Authorization") String authHeader
    );
}
