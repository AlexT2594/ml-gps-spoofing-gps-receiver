package com.alext2594.nmealogger.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.alext2594.nmealogger.R;
import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.JSONObjectRequestListener;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import java.io.*;
import java.nio.*;
import java.security.*;
import java.security.spec.*;

public class MainActivity extends AppCompatActivity implements LocationListener{

    private static final String TAG = "NMEALogger";
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    LocationManager locationManager;
    NMEAMessageListener messageListener;
    boolean currentlyLogging;
    Button logButton;
    StringBuilder NMEASentences;
    TextView nmeaPhraseTextView;
    String jwtToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logButton = findViewById(R.id.log_button);
        nmeaPhraseTextView = findViewById(R.id.nmea_phrase_textView);
        currentlyLogging = false;
        NMEASentences = new StringBuilder();

        AndroidNetworking.initialize(getApplicationContext());

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        messageListener = new NMEAMessageListener();

        logButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentlyLogging)
                    stopNMeaLogger();
                else
                    getLocationPermission();
            }
        });


        try{
            byte[] keyBytes = IOUtils.toByteArray(getResources().openRawResource(R.raw.private_jwt_key));

            PKCS8EncodedKeySpec spec =
                    new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey key = kf.generatePrivate(spec);

            jwtToken = Jwts.builder().setSubject("gps_receiver").signWith(key,SignatureAlgorithm.RS256).compact();

        } catch (Exception e) {
            e.printStackTrace();
        }




    }

    @Override
    public void onStop() {
        super.onStop();
        locationManager.removeUpdates(this);
        locationManager.removeNmeaListener(messageListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);
        locationManager.removeNmeaListener(messageListener);
    }

    private void getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startNMEALogger();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startNMEALogger();
                }
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }


    @SuppressLint("MissingPermission")
    private void startNMEALogger() {
        currentlyLogging = true;
        logButton.setTextColor(Color.RED);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10, 0, this);
        locationManager.addNmeaListener(messageListener);
    }

    private void stopNMeaLogger() {
        currentlyLogging = false;
        logButton.setTextColor(Color.BLACK);
        locationManager.removeUpdates(this);

        File file = new File(getExternalFilesDir("NMEA_logger"), "sample2.txt");
        FileOutputStream stream;
        Log.d(TAG,NMEASentences.toString());
        try {
            stream = new FileOutputStream(file, true);
            stream.write(NMEASentences.toString().getBytes());
            stream.flush();
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private class NMEAMessageListener implements OnNmeaMessageListener {
        @Override
        public void onNmeaMessage(String message, long timestamp) {
            Log.d(TAG, "NMEA: " + message);
            nmeaPhraseTextView.setText(message);

            JSONObject wrapper = new JSONObject();
            JSONObject data = new JSONObject();

            try {
                data.put("phrase", message);
                wrapper.put("data", data);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            AndroidNetworking.post("https://172.20.10.2:8000/raw_nmea_producer/produce_data")
                    .addHeaders("Content-Type", "application/json")
                    .addHeaders("Authorization", jwtToken)
                    .addJSONObjectBody(wrapper)
                    .setOkHttpClient(getTrustingCertOkHttpClient(getApplicationContext()))
                    .build()
                    .getAsJSONObject(new JSONObjectRequestListener() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                Log.d(TAG, response.getJSONObject("data")
                                        .getString("message"));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onError(ANError anError) {

                        }
                    });
        }
    }

/*
    The approach of bulding an OkHttpClient that trusts any TLS certificate is wrong and unsafe in a
    production environment. DO NOT DO THIS! This has been done only in a development environment to
    test HTTPS connections. If the server gets a certificate signed from a trusted CA,
    then the following code is not needed.
 */
    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

            OkHttpClient okHttpClient = builder.build();
            return okHttpClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
    * This approach of bulding an OkHttpClient that trusts ONLY the certificate offered by our
    * GPS producer server is secure. Using a TrustManager that trusts all certificates is not
    * considered secure.
    * */

    private static OkHttpClient getTrustingCertOkHttpClient(Context context) {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            InputStream caInput = context.getResources().openRawResource(R.raw.cert);
            Certificate certificate = certificateFactory.generateCertificate(caInput);

            // Create a KeyStore containing our trusted CAs
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", certificate);

            // Create a TrustManager that trusts the CAs in our KeyStore
            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());
            // Create an ssl socket factory with our manager that trusts our certificate
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)tmf.getTrustManagers()[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

            OkHttpClient okHttpClient = builder.build();
            return okHttpClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
