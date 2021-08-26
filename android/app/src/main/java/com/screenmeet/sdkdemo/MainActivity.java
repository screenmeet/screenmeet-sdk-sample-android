package com.screenmeet.sdkdemo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.screenmeet.sdk.Challenge;
import com.screenmeet.sdk.CompletionError;
import com.screenmeet.sdk.CompletionHandler;
import com.screenmeet.sdk.ErrorCode;
import com.screenmeet.sdk.Participant;
import com.screenmeet.sdk.ScreenMeet;
import com.screenmeet.sdk.SessionEventListener;
import com.screenmeet.sdkdemo.databinding.ActivityMainBinding;

import org.jetbrains.annotations.NotNull;
import org.webrtc.VideoTrack;

import java.util.ArrayList;

import io.flutter.embedding.android.FlutterActivity;

@SuppressWarnings("CodeBlock2Expr")
@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        enableButtons();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        handler = new Handler();
        handler.post(mockUiUpdate);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ScreenMeet.unregisterEventListener(eventListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ScreenMeet.registerEventListener(eventListener);
        ScreenMeet.SessionState sessionState = ScreenMeet.connectionState().getState();
        displayConnectionState(sessionState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        eventListener = null;
        mockUiUpdate = null;
    }

    private void enableButtons(){
        binding.connectBtn.setOnClickListener(v -> {
            String code = binding.codeEt.getText().toString();
            connectSession(code);
        });
        binding.disconnectBtn.setOnClickListener(v -> ScreenMeet.disconnect());
        binding.navigateToWebView.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, WebViewActivity.class));
        });
        binding.confidentialityDemoBtn.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ConfidentialityActivity.class));
        });
        binding.flutterFragmentDemoBtn.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, CombinedFragmentActivity.class));
        });
        binding.flutterActivityDemoBtn.setOnClickListener(v -> {
            startActivity(FlutterActivity.createDefaultIntent(MainActivity.this));
        });
        binding.reactActivityDemoBtn.setOnClickListener(v ->  {
            startActivity(new Intent(MainActivity.this, ReactNativeActivity.class));
        });
        binding.callActivityDemoBtn.setOnClickListener(v -> {
            SupportApplication.instance.widget.hideFloatingWidget();
            startActivity(new Intent(MainActivity.this, CallActivity.class));
        });
    }

    private void connectSession(String code){
        ScreenMeet.connect(code, new CompletionHandler() {
            @Override
            public void onSuccess() {
                SupportApplication.startListeningForeground();
                ScreenMeet.shareScreen();
                ScreenMeet.shareAudio();
            }

            @Override
            public void onFailure(@NotNull CompletionError completionError) {
                if(completionError.getCode() == ErrorCode.CAPTCHA_ERROR){
                    Challenge challenge = completionError.getChallenge();
                    if (challenge != null) showCaptchaDialog(challenge);
                } else if(completionError.getCode() == ErrorCode.KNOCK_PERMISSION_TIMEOUT){
                    binding.resultTv.setText(getString(R.string.waiting_for_knock));
                } else showSessionFailure(completionError.getCode(), completionError.getMessage());
            }
        });
    }

    private void showCaptchaDialog(Challenge challenge){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.captcha_dialog, null);
        builder.setTitle(R.string.captcha_not_robot)
                .setCancelable(true)
                .setView(view)
                .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                    Editable text = ((EditText) view.findViewById(R.id.captchaEdit)).getText();
                    challenge.solve(text.toString());
                }).setNegativeButton(android.R.string.no, (dialog, id) -> challenge.solve(""))
                .setOnCancelListener(dialog -> challenge.solve(""))
                .create()
                .show();

        ImageView imageView = view.findViewById(R.id.captchaImage);
        imageView.setImageBitmap(challenge.getChallenge());
    }

    private Runnable mockUiUpdate = new Runnable() {
        @Override
        public void run() {
            try {
                int color = ((int)(Math.random()*16777215)) | (0xFF << 24);
                binding.mockView.setBackgroundColor(color);
            } finally {
                handler.postDelayed(mockUiUpdate, 500);
            }
        }
    };

    private SessionEventListener eventListener = new SessionEventListener() {
        @Override
        public void onParticipantJoined(@NotNull Participant participant) {
            updateParticipants();
        }

        @Override
        public void onParticipantLeft(@NotNull Participant participant) {
            updateParticipants();
        }

        @Override
        public void onParticipantMediaStateChanged(@NotNull Participant participant) {
            VideoTrack videoTrack = participant.getVideoTrack();
            if(videoTrack != null) {
                SupportApplication.instance.widget.showFloatingWidget(MainActivity.this, videoTrack);
            } else SupportApplication.instance.widget.hideFloatingWidget();
        }

        @Override
        public void onLocalVideoCreated(@NotNull VideoTrack videoTrack) {
        }

        @Override
        public void onLocalVideoStopped() {

        }

        @Override
        public void onLocalAudioCreated() {

        }

        @Override
        public void onLocalAudioStopped() {

        }

        @Override
        public void onActiveSpeakerChanged(@NotNull Participant participant) {

        }

        @Override
        public void onConnectionStateChanged(@NotNull ScreenMeet.ConnectionState connectionState) {
            displayConnectionState(connectionState.getState());
        }
    };

    private void displayConnectionState(@NotNull ScreenMeet.SessionState sessionState){
        binding.stateLabelTv.setVisibility(View.GONE);
        binding.stateTv.setVisibility(View.GONE);

        boolean connected = sessionState == ScreenMeet.SessionState.CONNECTED;

        binding.connectBtn.setVisibility(connected ? View.GONE : View.VISIBLE);
        binding.disconnectBtn.setVisibility(connected ? View.VISIBLE : View.GONE);
        binding.resultTv.setVisibility(View.GONE);
        binding.connectProgress.setVisibility(View.GONE);
        binding.mockView.setVisibility(connected ? View.VISIBLE : View.GONE);

        binding.navigateToWebView.setVisibility(connected ? View.VISIBLE : View.GONE);
        binding.confidentialityDemoBtn.setVisibility(connected ? View.VISIBLE : View.GONE);
        binding.flutterFragmentDemoBtn.setVisibility(connected ? View.VISIBLE : View.GONE);
        binding.flutterActivityDemoBtn.setVisibility(connected ? View.VISIBLE : View.GONE);
        binding.reactActivityDemoBtn.setVisibility(connected ? View.VISIBLE : View.GONE);
        binding.callActivityDemoBtn.setVisibility(connected ? View.VISIBLE : View.GONE);

        binding.stateLabelTv.setVisibility(View.VISIBLE);
        binding.stateTv.setVisibility(View.VISIBLE);
        binding.stateTv.setText(sessionState.toString());

        binding.participantsLabelTv.setVisibility(connected ? View.VISIBLE : View.GONE);
        binding.participantsTv.setVisibility(connected ? View.VISIBLE : View.GONE);

        switch (sessionState){
            case CONNECTED:
                binding.stateTv.setTextColor(Color.GREEN);
                updateParticipants();
                break;
            case CONNECTING:
                hideKeyboardInput();
                binding.stateTv.setTextColor(Color.YELLOW);
                binding.connectProgress.setVisibility(View.VISIBLE);
                break;
            case RECONNECTING:
                binding.stateTv.setTextColor(Color.YELLOW);
                break;
            case DISCONNECTED:
                SupportApplication.stopListeningForeground();
                SupportApplication.instance.widget.hideFloatingWidget();
                binding.stateTv.setTextColor(Color.RED);
                break;
        }
    }

    private void showSessionFailure(ErrorCode errorCode, String error){
        displayConnectionState(ScreenMeet.SessionState.DISCONNECTED);

        binding.resultTv.setTextColor(Color.RED);
        String errorText = errorCode + " " + error;
        binding.resultTv.setText(errorText);
        binding.resultTv.setVisibility(View.VISIBLE);
    }

    private void updateParticipants(){
        ArrayList<Participant> participants = ScreenMeet.participants();
        StringBuilder participantsString = new StringBuilder();
        for (Participant participant : participants) {
            participantsString.append(participant.getIdentity().getName()).append("\n");
        }
        binding.participantsTv.setText(participantsString.toString());
    }

    private void hideKeyboardInput(){
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}