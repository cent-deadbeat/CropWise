package com.example.soilmate;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.*;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import android.app.ProgressDialog;
import com.google.firebase.auth.PhoneAuthOptions;

public class Login extends AppCompatActivity {
    private FirebaseAuth auth;
    private EditText editTextEmail, editTextPassword;
    private TextView errorEmail, errorPassword;
    private AppCompatButton buttonLogin, googleSignInButton;

    private GoogleSignInClient mGoogleSignInClient;

    private String verificationId; // Store the verification ID for OTP

    private boolean isOTPVerified = false; // Add this flag


    // SharedPreferences keys for fraudulent activity check
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_LAST_FAILED_TIME = "last_failed_time";

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long BLOCK_DURATION = 5 * 60 * 1000; // 5 minutes

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.isEmailVerified()) {
            // ✅ Redirect logged-in users with verified email to MyPlants
            Log.d("Login", "User is logged in and email is verified. Redirecting to MyPlants.");
            startActivity(new Intent(Login.this, MyPlants.class));
            finish();
        } else {
            Log.d("Login", "User is not logged in or email is not verified. Staying on Login screen.");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Firebase Auth instance
        auth = FirebaseAuth.getInstance();

        editTextEmail = findViewById(R.id.emailAddress);
        editTextPassword = findViewById(R.id.password);
        errorEmail = findViewById(R.id.errorEmail);
        errorPassword = findViewById(R.id.errorPassword);
        buttonLogin = findViewById(R.id.signin);
        googleSignInButton = findViewById(R.id.googleSignInButton);

        configureGoogleSignIn();
        googleSignInButton.setOnClickListener(v -> signInWithGoogle());

        // Set up text validation
        addTextWatcher(editTextEmail, errorEmail, true);
        addTextWatcher(editTextPassword, errorPassword, false);

        setTogglePasswordVisibility(editTextPassword);

        // Login Button Click
        buttonLogin.setOnClickListener(v -> validateForm());

        // Signup Link
        findViewById(R.id.signup).setOnClickListener(v -> {
            startActivity(new Intent(Login.this, Signup.class));
        });

        // Forgot Password Link
        findViewById(R.id.forgot).setOnClickListener(v -> {
            startActivity(new Intent(Login.this, AccountRecovery.class));
        });
    }

    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.google_web_client_id))  // Ensure this is correct!
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    // Start Google Sign-In Flow
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, 100);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                Log.d("GoogleSignIn", "✅ Google sign-in successful: " + account.getEmail());
                firebaseAuthWithGoogle(account);
            } catch (ApiException e) {
                Log.e("GoogleSignIn", "❌ Google Sign-In failed: " + e.getStatusCode());
                Toast.makeText(this, "Google Sign-In failed! Code: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
            }
        }
    }



    public void onVerificationFailed(@NonNull FirebaseException e) {
        // Handle verification failure
        Toast.makeText(Login.this, "Failed to send OTP: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        Log.e("Login", "OTP verification failed: " + e.getMessage());

        // Prevent the user from proceeding to the homepage
        auth.signOut(); // Log out the user
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            String userId = user.getUid();
                            String email = user.getEmail();
                            String fullName = acct.getDisplayName();  // ✅ Get user's actual Google name

                            checkUserStatusAndProceed(userId, email, fullName);
                        }
                    } else {
                        Toast.makeText(Login.this, "Google login failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void checkUserStatusAndProceed(String userId, String email, String fullName) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userRef = db.collection("users").document(userId);

        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                // ✅ User already exists → Proceed to the app
                startActivity(new Intent(Login.this, MyPlants.class));
                finish();
            } else {
                // ❌ New User → Save Name & Email in Firestore
                Map<String, Object> user = new HashMap<>();
                user.put("userId", userId);
                user.put("email", email);
                user.put("username", fullName);  // ✅ Store actual name from Google
                user.put("2FA_enabled", false);  // Optional: Enable 2FA later

                userRef.set(user)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(Login.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(Login.this, MyPlants.class));
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(Login.this, "Failed to create user", Toast.LENGTH_SHORT).show();
                            FirebaseAuth.getInstance().signOut();
                            mGoogleSignInClient.signOut();
                        });
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(Login.this, "Error checking account: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    // Validate Form and Login User
    private void validateForm() {
        boolean isValid = true;
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        errorEmail.setVisibility(View.GONE);
        errorPassword.setVisibility(View.GONE);

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorEmail.setText("Invalid email");
            errorEmail.setVisibility(View.VISIBLE);
            isValid = false;
        }

        // Password Validation
        if (TextUtils.isEmpty(password)) {
            errorPassword.setText("Invalid password");
            errorPassword.setVisibility(View.VISIBLE);
            isValid = false;
        }

        if (isValid) {
            if (isUserBlocked()) {
                Toast.makeText(this, "Too many failed attempts. Please try again later.", Toast.LENGTH_LONG).show();
            } else {
                toggleLoading(true);
                loginUser(email, password);
            }
        }
    }

    // Check if the user is blocked due to too many failed attempts
    private boolean isUserBlocked() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0);
        long lastFailedTime = prefs.getLong(KEY_LAST_FAILED_TIME, 0);

        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFailedTime < BLOCK_DURATION) {
                return true; // User is blocked
            } else {
                // Reset failed attempts if the block duration has passed
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(KEY_FAILED_ATTEMPTS, 0);
                editor.apply();
            }
        }
        return false; // User is not blocked
    }

    // Login User with Firebase Authentication
    private void loginUser(String email, String password) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    Log.d("LoginDebug", "User logged in: " + user.getUid());

                    if (user.isEmailVerified()) {
                        // ✅ Email is verified → Proceed to the app
                        startActivity(new Intent(Login.this, MyPlants.class));
                        finish();
                    } else {
                        // ❌ Email is NOT verified → Show Toast & sign out
                        auth.signOut(); // Log out the user
                        Toast.makeText(Login.this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Log.e("LoginDebug", "User is NULL after successful login!");
                }
            } else {
                Log.e("LoginDebug", "Firebase authentication failed: " + task.getException());
                errorEmail.setText("Invalid credentials");
                errorPassword.setText("Invalid credentials");
                errorEmail.setVisibility(View.VISIBLE);
                errorPassword.setVisibility(View.VISIBLE);
                toggleLoading(false);
            }
        });
    }



    // Show OTP Dialog for 2FA
    private void showOTPDialog(FirebaseUser user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter OTP");
        builder.setMessage("We've sent a code to your verified phone number.");

        final EditText otpInput = new EditText(this);
        otpInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(otpInput);

        builder.setPositiveButton("Verify", (dialog, which) -> {
            String code = otpInput.getText().toString().trim();
            if (code.isEmpty() || code.length() < 6) {
                otpInput.setError("Enter valid code");
                otpInput.requestFocus();
            } else {
                verifyOTP(code, user);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            // Log out the user if they cancel 2FA
            auth.signOut();
            Toast.makeText(this, "Login canceled", Toast.LENGTH_SHORT).show();
        });

        builder.setCancelable(false); // Prevent dialog from being dismissed by tapping outside
        AlertDialog dialog = builder.create();
        dialog.show();

        // Send OTP to the user's verified phone number
        sendOTP(user);
    }



    // Send OTP to the user's verified phone number
    private void sendOTP(FirebaseUser user) {
        // First, try to get the phone number from FirebaseUser
        String phoneNumber = user.getPhoneNumber();

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            // If the phone number is not available in FirebaseUser, fetch it from Firestore
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users").document(user.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String firestorePhoneNumber = documentSnapshot.getString("phoneNumber");
                            if (firestorePhoneNumber != null && !firestorePhoneNumber.isEmpty()) {
                                Log.d("Login", "Phone number from FirebaseUser: " + user.getPhoneNumber());
                                Log.d("Login", "Phone number from Firestore: " + firestorePhoneNumber);
                                // Send OTP to the retrieved phone number
                                sendOTPToPhoneNumber(firestorePhoneNumber);
                            } else {
                                Toast.makeText(this, "No verified phone number found", Toast.LENGTH_SHORT).show();
                                // Prevent the user from proceeding to the homepage
                                auth.signOut(); // Log out the user
                            }
                        } else {
                            Toast.makeText(this, "User document not found", Toast.LENGTH_SHORT).show();
                            // Prevent the user from proceeding to the homepage
                            auth.signOut(); // Log out the user
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to fetch phone number: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        // Prevent the user from proceeding to the homepage
                        auth.signOut(); // Log out the user
                    });
        } else {
            // Send OTP to the phone number from FirebaseUser
            sendOTPToPhoneNumber(phoneNumber);
        }
    }




    // Helper method to send OTP to the phone number
    private void sendOTPToPhoneNumber(String phoneNumber) {
        // Show a progress dialog
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Sending verification code...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(auth)
                        .setPhoneNumber(phoneNumber)       // Phone number to verify
                        .setTimeout(60L, TimeUnit.SECONDS) // Timeout duration
                        .setActivity(this)                 // Activity for reCAPTCHA flow
                        .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            @Override
                            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                                progressDialog.dismiss();
                                signInWithPhoneAuthCredential(credential);
                            }

                            @Override
                            public void onVerificationFailed(@NonNull FirebaseException e) {
                                progressDialog.dismiss();
                                Toast.makeText(Login.this, "Failed to send OTP: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                Log.e("Login", "OTP verification failed: " + e.getMessage());

                                // Prevent the user from proceeding to the homepage
                                auth.signOut(); // Log out the user
                            }

                            @Override
                            public void onCodeSent(@NonNull String verificationId, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                                progressDialog.dismiss();
                                // Store the verification ID for later use
                                Login.this.verificationId = verificationId;
                                Log.d("Login", "OTP code sent successfully");

                                // Show the OTP dialog
                                showOTPDialog();
                            }
                        })
                        .build();

        // Start the OTP verification process
        PhoneAuthProvider.verifyPhoneNumber(options);
    }


    // Verify the OTP entered by the user
    private void verifyOTP(String code, FirebaseUser user) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        user.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // OTP verified, proceed to MyPlants
                        startActivity(new Intent(Login.this, MyPlants.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void showOTPDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter OTP");
        builder.setMessage("We've sent a code to your verified phone number.");

        final EditText otpInput = new EditText(this);
        otpInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(otpInput);

        builder.setPositiveButton("Verify", (dialog, which) -> {
            String code = otpInput.getText().toString().trim();
            if (code.isEmpty() || code.length() < 6) {
                otpInput.setError("Enter valid code");
                otpInput.requestFocus();
            } else {
                verifyCode(code);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            // Log out the user if they cancel 2FA
            auth.signOut();
            Toast.makeText(this, "Login canceled", Toast.LENGTH_SHORT).show();
        });

        builder.setCancelable(false); // Prevent dialog from being dismissed by tapping outside
        AlertDialog dialog = builder.create();
        dialog.show();
    }




    private void verifyCode(String code) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneAuthCredential(credential);
    }




    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // OTP verification successful
                        isOTPVerified = true;
                        Toast.makeText(Login.this, "OTP verified successfully", Toast.LENGTH_SHORT).show();

                        // Proceed to MyPlants
                        startActivity(new Intent(Login.this, MyPlants.class));
                        finish();
                    } else {
                        // OTP verification failed
                        Toast.makeText(Login.this, "OTP verification failed", Toast.LENGTH_SHORT).show();
                        Log.e("Login", "OTP verification failed: " + task.getException().getMessage());

                        // Prevent the user from proceeding to the homepage
                        auth.signOut(); // Log out the user
                    }
                });
    }








    // Increment failed attempts and block user if necessary
    private void incrementFailedAttempts() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        long lastFailedTime = System.currentTimeMillis();

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_FAILED_ATTEMPTS, failedAttempts);
        editor.putLong(KEY_LAST_FAILED_TIME, lastFailedTime);
        editor.apply();

        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            Toast.makeText(this, "Too many failed attempts. Please try again later.", Toast.LENGTH_LONG).show();
        }
    }

    // Toggle Password Visibility
    private void setTogglePasswordVisibility(EditText passwordField) {
        passwordField.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                Drawable endDrawable = passwordField.getCompoundDrawables()[2];
                if (endDrawable != null && event.getRawX() >= (passwordField.getRight() - endDrawable.getBounds().width())) {
                    if (passwordField.getTransformationMethod() instanceof PasswordTransformationMethod) {
                        passwordField.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        passwordField.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ass, 0, R.drawable.ic_hide_pass, 0);
                    } else {
                        passwordField.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        passwordField.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ass, 0, R.drawable.hide, 0);
                    }
                    passwordField.setSelection(passwordField.getText().length());
                    return true;
                }
            }
            return false;
        });
    }

    // Live validation for email and password fields
    private void addTextWatcher(EditText editText, TextView errorTextView, boolean isEmail) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String input = s.toString().trim();
                if (isEmail) {
                    if (input.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
                        errorTextView.setText("Invalid email");
                        errorTextView.setVisibility(View.VISIBLE);
                    } else {
                        errorTextView.setVisibility(View.GONE);
                    }
                } else {
                    if (input.isEmpty()) {
                        errorTextView.setText("Password required");
                        errorTextView.setVisibility(View.VISIBLE);
                    } else {
                        errorTextView.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    //progress bar
    private void toggleLoading(boolean isLoading) {
        if (isLoading) {
            buttonLogin.setVisibility(View.GONE);
            findViewById(R.id.loginProgressBar).setVisibility(View.VISIBLE);
        } else {
            buttonLogin.setVisibility(View.VISIBLE);
            findViewById(R.id.loginProgressBar).setVisibility(View.GONE);
        }
    }
}