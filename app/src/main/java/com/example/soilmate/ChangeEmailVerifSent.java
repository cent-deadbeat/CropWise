package com.example.soilmate;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.HashMap;

public class ChangeEmailVerifSent extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseUser user;
    private FirebaseFunctions functions;
    private ImageView verifiedIcon, mailIcon;
    private TextView messageText;
    private Button resendButton;
    private ProgressBar progressBar;
    private String newEmail;

    private Handler handler = new Handler();
    private Runnable checkEmailVerified;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_email_verif_sent);

        auth = FirebaseAuth.getInstance();
        user = auth.getCurrentUser();
        functions = FirebaseFunctions.getInstance();

        verifiedIcon = findViewById(R.id.verifiedIcon);
        mailIcon = findViewById(R.id.imageView8);
        messageText = findViewById(R.id.message);
        resendButton = findViewById(R.id.resendEmail);
        progressBar = findViewById(R.id.progressBar);

        // Get new email from intent
        newEmail = getIntent().getStringExtra("newEmail");
        if (newEmail == null) {
            Toast.makeText(this, "Error: No email provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set up resend button
        resendButton.setOnClickListener(v -> {
            resendButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            resendVerificationEmail();
        });

        checkEmailVerified = new Runnable() {
            @Override
            public void run() {
                user.reload().addOnSuccessListener(aVoid -> {
                    if (user.isEmailVerified()) {
                        updateEmail();
                    } else {
                        // Re-run the check every 5 seconds if not verified
                        handler.postDelayed(this, 5000);
                    }
                }).addOnFailureListener(e -> {
                    Toast.makeText(ChangeEmailVerifSent.this,
                            "Error checking verification status", Toast.LENGTH_SHORT).show();
                    handler.postDelayed(this, 5000);
                });
            }
        };

        // Start checking if email is verified
        handler.postDelayed(checkEmailVerified, 5000);
    }

    private void resendVerificationEmail() {
        // Create data to pass to the Cloud Function
        HashMap<String, Object> data = new HashMap<>();
        data.put("newEmail", newEmail);

        // Call the Cloud Function to resend verification email
        functions.getHttpsCallable("resendEmailChangeVerification")
                .call(data)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    resendButton.setVisibility(View.VISIBLE);

                    if (task.isSuccessful()) {
                        HttpsCallableResult result = task.getResult();
                        Toast.makeText(ChangeEmailVerifSent.this,
                                "Verification email resent to " + newEmail,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Exception e = task.getException();
                        Toast.makeText(ChangeEmailVerifSent.this,
                                "Failed to resend verification: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        Log.e("ResendEmail", "Failed to resend verification", e);
                    }
                });
    }

    private void updateEmail() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Hide mail icon and resend button, show verified icon
        mailIcon.setVisibility(View.GONE);
        resendButton.setVisibility(View.GONE);
        verifiedIcon.setVisibility(View.VISIBLE);

        // Update message text
        messageText.setText("Email successfully verified and updated to " + newEmail);

        // First update Firestore
        db.collection("users")
                .document(user.getUid())
                .update("email", newEmail, "pendingEmail", null)
                .addOnSuccessListener(aVoid -> {
                    // Then update Firebase Auth
                    user.updateEmail(newEmail)
                            .addOnSuccessListener(aVoid1 -> {
                                Toast.makeText(ChangeEmailVerifSent.this,
                                        "Email updated to " + newEmail, Toast.LENGTH_LONG).show();

                                // Redirect to MyAccount after a short delay
                                new Handler().postDelayed(() -> {
                                    Intent intent = new Intent(ChangeEmailVerifSent.this, MyAccount.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                    startActivity(intent);
                                    finish();
                                }, 3000);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(ChangeEmailVerifSent.this,
                                        "Failed to update email: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                Log.e("EmailUpdate", "Failed to update email in Auth", e);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ChangeEmailVerifSent.this,
                            "Failed to update Firestore: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e("EmailUpdate", "Failed to update email in Firestore", e);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(checkEmailVerified);
    }
}