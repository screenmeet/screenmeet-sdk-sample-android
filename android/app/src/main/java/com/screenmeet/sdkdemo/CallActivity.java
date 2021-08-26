package com.screenmeet.sdkdemo;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.screenmeet.sdk.Identity;
import com.screenmeet.sdk.Participant;
import com.screenmeet.sdk.ScreenMeet;
import com.screenmeet.sdk.SessionEventListener;
import com.screenmeet.sdkdemo.databinding.ActivityCallBinding;
import com.screenmeet.sdkdemo.recycler.ParticipantsAdapter;

import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Objects;

public class CallActivity extends AppCompatActivity {

    private final String TAG = CallActivity.class.getSimpleName();

    private ActivityCallBinding binding;
    private ParticipantsAdapter participantsAdapter;
    private EglBase eglBase;

    private VideoTrack localVideoTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        eglBase = EglBase.create();
        binding = ActivityCallBinding.inflate(getLayoutInflater());

        //The same eglContext as a capturing one should be used to preview camera
        EglBase.Context eglBaseContext = Objects.requireNonNull(ScreenMeet.getEglContext());
        binding.localRenderer.surfaceViewRenderer.init(eglBaseContext, null);
        binding.activeSpeakerRenderer.renderer.setZOrderOnTop(false);
        binding.activeSpeakerRenderer.renderer.init(eglBaseContext, new RendererCommon.RendererEvents() {

            @Override
            public void onFirstFrameRendered() { }

            @Override
            public void onFrameResolutionChanged(int width, int height, int i2) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    ViewGroup.LayoutParams layoutParams = binding.activeSpeakerRenderer.renderer.getLayoutParams();
                    layoutParams.width = width;
                    layoutParams.height = height;
                    binding.activeSpeakerRenderer.zoomView.updateViewLayout(
                            binding.activeSpeakerRenderer.renderer,
                            layoutParams
                    );

                });
            }
        });
        binding.localRenderer.surfaceViewRenderer.setZOrderMediaOverlay(true);
        binding.localRenderer.surfaceViewRenderer.setZOrderOnTop(true);

        setContentView(binding.getRoot());
    }

    private final SessionEventListener eventListener = new SessionEventListener() {
        @Override
        public void onParticipantJoined(@NonNull Participant participant) {
            Log.d(TAG, "onParticipantJoined");
            participantsAdapter.add(participant);
            if(!participantsAdapter.activeSpeakerPresent()){
               switchActiveSpeaker(participant);
            }
        }

        @Override
        public void onParticipantLeft(@NonNull Participant participant) {
            Log.d(TAG, "onParticipantLeft");
            boolean wasActiveSpeaker = participantsAdapter.isActiveSpeaker(participant);
            participantsAdapter.remove(participant);
            if(wasActiveSpeaker){
                if(!ScreenMeet.participants().isEmpty()){
                    Participant p = ScreenMeet.participants().get(0);
                    switchActiveSpeaker(p);
                } else activeSpeakerAbsent();
            }
        }

        @Override
        public void onLocalVideoCreated(@NonNull VideoTrack videoTrack) {
            Log.d(TAG, "onLocalVideoCreated");
            applyControlsState();

            renderLocalVideoTrack(videoTrack);
        }

        @Override
        public void onLocalVideoStopped() {
            Log.d(TAG, "onLocalVideoStopped");
            applyControlsState();

            renderLocalVideoTrack(null);
        }

        @Override
        public void onLocalAudioCreated() {
            Log.d(TAG, "onLocalAudioCreated");
            applyControlsState();
        }

        @Override
        public void onLocalAudioStopped() {
            Log.d(TAG, "onLocalAudioStopped");
            applyControlsState();
        }

        @Override
        public void onParticipantMediaStateChanged(@NonNull Participant participant) {
            Log.d(TAG, "onParticipantMediaStateChanged");
            if(participantsAdapter.isActiveSpeaker(participant)) {
                displayActiveSpeaker(participant);
            }  else participantsAdapter.update(participant);
        }

        @Override
        public void onConnectionStateChanged(@NonNull ScreenMeet.ConnectionState connectionState) {
            Log.d(TAG, "onConnectionStateChanged");
            switch (connectionState.getState()){
                case CONNECTED:
                    binding.connectionLoss.setVisibility(View.GONE);
                    loadState();
                    break;
                case RECONNECTING:
                    binding.connectionLoss.setVisibility(View.VISIBLE);
                    break;
                case DISCONNECTED:
                    sessionEnded();
                    break;
            }
        }

        @Override
        public void onActiveSpeakerChanged(@NonNull Participant participant) {
            Log.d(TAG, "onActiveSpeakerChanged");
            switchActiveSpeaker(participant);
        }
    };

    void renderLocalVideoTrack(@Nullable VideoTrack videoTrack){
        if (videoTrack != null) {
            if (localVideoTrack != null) {
                localVideoTrack.removeSink(binding.localRenderer.surfaceViewRenderer);
                localVideoTrack.removeSink(rotationSink);
            }

            localVideoTrack = videoTrack;
            localVideoTrack.setEnabled(true);
            localVideoTrack.addSink(rotationSink);
        } else {
            localVideoTrack = null;
            binding.localRenderer.surfaceViewRenderer.clearImage();
        }
    }

    private final VideoSink rotationSink = videoFrame -> {
        VideoFrame frame = new VideoFrame(videoFrame.getBuffer(), 0, videoFrame.getTimestampNs());
        binding.localRenderer.surfaceViewRenderer.onFrame(frame);
    };

    @Override
    protected void onResume() {
        super.onResume();
        ScreenMeet.registerEventListener(eventListener);

        binding.localRenderer.surfaceViewRenderer.clearImage();
        enableButtons();
        loadState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (participantsAdapter != null) {
            participantsAdapter.dispose();
        }

        if (localVideoTrack != null) {
            localVideoTrack.removeSink(binding.localRenderer.surfaceViewRenderer);
        }

        ScreenMeet.unregisterEventListener(eventListener);
    }

    private void loadState(){
        ArrayList<Participant> participants = ScreenMeet.participants();
        if (participantsAdapter != null) {
            participantsAdapter.dispose();
        }

        participantsAdapter = new ParticipantsAdapter(participants, eglBase);
        binding.participantsRecycler.setAdapter(participantsAdapter);
        binding.participantsRecycler.setLayoutManager(new LinearLayoutManager(this));

        if(!participants.isEmpty()){
            Participant activeSpeaker = participantsAdapter.getActiveSpeaker();
            if (activeSpeaker != null) {
                switchActiveSpeaker(activeSpeaker);
            } else switchActiveSpeaker(participants.get(0));
        } else activeSpeakerAbsent();

        renderLocalVideoTrack(ScreenMeet.localVideoTrack());

        applyControlsState();
        setButtonBackgroundColor(binding.hangUp, R.color.bright_red);
    }

    private void sessionEnded(){
        SupportApplication.stopListeningForeground();
        finish();
    }

    private void enableButtons(){
        binding.micro.setOnClickListener(v -> {
            switchButton(binding.micro, true, true);
            if(ScreenMeet.localMediaState().isAudioActive()){
                ScreenMeet.stopAudioSharing();
            } else ScreenMeet.shareAudio();
        });

        binding.camera.setOnClickListener(v -> {
            switchButton(binding.camera, true, true);
            switch (ScreenMeet.localMediaState().getVideoState().getSource()){
                case BACK_CAMERA:
                case FRONT_CAMERA:
                case CUSTOM_CAMERA:
                    ScreenMeet.stopVideoSharing();
                    break;
                case SCREEN:
                case NONE:
                    ScreenMeet.shareCamera(true);
                    break;
            }
        });

        binding.cameraSwitch.setOnClickListener(v -> {
            switchButton(binding.cameraSwitch, true, true);
            switch (ScreenMeet.localMediaState().getVideoState().getSource()){
                case BACK_CAMERA:
                    ScreenMeet.shareCamera(true);
                    break;
                case FRONT_CAMERA:
                    ScreenMeet.shareCamera(false);
                    break;
                case CUSTOM_CAMERA:
                case SCREEN:
                case NONE:
                    break;
            }
        });

        binding.screen.setOnClickListener(v -> {
            switchButton(binding.screen, true, true);
            switch (ScreenMeet.localMediaState().getVideoState().getSource()){
                case BACK_CAMERA:
                case FRONT_CAMERA:
                case CUSTOM_CAMERA:
                case NONE:
                    ScreenMeet.shareScreen();
                    break;
                case SCREEN:
                    ScreenMeet.stopVideoSharing();
                    break;
            }
        });

        binding.hangUp.setOnClickListener(v -> ScreenMeet.disconnect());
    }

    private void applyControlsState(){
        ScreenMeet.VideoSource sourceType = ScreenMeet.localMediaState().getVideoState().getSource();
        switch (sourceType) {
            case SCREEN:
                switchButton(binding.screen, false, true);
                switchButton(binding.camera, false, false);
                binding.cameraSwitch.setVisibility(View.GONE);
                binding.camera.setImageResource(R.drawable.videocam_off);
                binding.localRenderer.cameraButton.setImageResource(R.drawable.screenshot);
                break;
            case FRONT_CAMERA:
            case BACK_CAMERA:
                switchButton(binding.screen, false, false);
                switchButton(binding.camera, false, true);
                switchButton(binding.cameraSwitch, false, true);
                binding.cameraSwitch.setVisibility(View.VISIBLE);
                binding.camera.setImageResource(R.drawable.videocam);
                binding.localRenderer.cameraButton.setImageResource(R.drawable.videocam);
                break;
            case CUSTOM_CAMERA:
                switchButton(binding.screen, false, false);
                switchButton(binding.camera, false, true);
                switchButton(binding.cameraSwitch, false, true);
                binding.cameraSwitch.setVisibility(View.GONE);
                binding.camera.setImageResource(R.drawable.videocam);
                binding.localRenderer.cameraButton.setImageResource(R.drawable.videocam);
                break;
            case NONE:
                switchButton(binding.camera, false, false);
                switchButton(binding.screen, false, false);
                binding.cameraSwitch.setVisibility(View.GONE);
                binding.camera.setImageResource(R.drawable.videocam_off);
                binding.localRenderer.cameraButton.setImageResource(R.drawable.videocam_off);
                break;
        }

        ScreenMeet.SessionState connectionState = ScreenMeet.connectionState().getState();
        if(connectionState == ScreenMeet.SessionState.CONNECTED){
            binding.connectionLoss.setVisibility(View.GONE);
        } else  binding.connectionLoss.setVisibility(View.VISIBLE);

        boolean audioActive = ScreenMeet.localMediaState().isAudioActive();
        switchButton(binding.micro, false, audioActive);
        if(audioActive) {
            binding.micro.setImageResource(R.drawable.mic);
            binding.localRenderer.microButton.setImageResource(R.drawable.mic);
        } else {
            binding.micro.setImageResource(R.drawable.mic_off);
            binding.localRenderer.microButton.setImageResource(R.drawable.mic_off);
        }
    }

    private void switchButton(ImageButton button, boolean pending, boolean enabled){
        if(pending){
            button.setEnabled(false);
            setButtonBackgroundColor(button, R.color.loading_button);
        } else {
            if(enabled) setButtonBackgroundColor(button, R.color.enabled_button);
            else setButtonBackgroundColor(button, R.color.disabled_button);
            button.setEnabled(true);
        }
    }

    private void setButtonBackgroundColor(ImageButton button, int colorRes){
        GradientDrawable background = (GradientDrawable) button.getBackground();
        background.setColor(getResources().getColor(colorRes));
    }

    private void switchActiveSpeaker(Participant participant){
        Participant activeSpeakerCurrent = participantsAdapter.getActiveSpeaker();
        if (activeSpeakerCurrent != null) {
            activeSpeakerCurrent.clearSinks();
        }

        Participant activeSpeaker = participantsAdapter.updateActiveSpeaker(participant);
        if(activeSpeaker != null) {
            displayActiveSpeaker(activeSpeaker);
        }
    }

    private void displayActiveSpeaker(Participant participant){
        participant.clearSinks();

        binding.activeSpeakerRenderer.nameTv.setText(participant.getIdentity().getName());
        binding.activeSpeakerRenderer.nameTv.setVisibility(View.VISIBLE);

        if(participant.getIdentity().getRole() == Identity.Role.HOST){
            binding.activeSpeakerRenderer.hostImage.setVisibility(View.VISIBLE);
        } else binding.activeSpeakerRenderer.hostImage.setVisibility(View.GONE);

        binding.activeSpeakerRenderer.microButton.setVisibility(View.VISIBLE);
        if(participant.getMediaState().isAudioActive()){
            binding.activeSpeakerRenderer.microButton.setImageResource(R.drawable.mic);
        } else binding.activeSpeakerRenderer.microButton.setImageResource(R.drawable.mic_off);

        binding.activeSpeakerRenderer.cameraButton.setVisibility(View.VISIBLE);
        switch (participant.getMediaState().getVideoState().getSource()){
            case BACK_CAMERA:
            case FRONT_CAMERA:
            case CUSTOM_CAMERA:
                binding.activeSpeakerRenderer.cameraButton.setImageResource(R.drawable.videocam);
                break;
            case SCREEN:
                binding.activeSpeakerRenderer.cameraButton.setImageResource(R.drawable.screenshot);
                break;
            case NONE:
                binding.activeSpeakerRenderer.cameraButton.setImageResource(R.drawable.videocam_off);
        }

        VideoTrack videoTrack = participant.getVideoTrack();
        if (videoTrack != null) {
            if(!participant.getMediaState().isVideoActive()){
                participant.clearSinks();
                binding.activeSpeakerRenderer.renderer.clearImage();
            } else updateTrack(videoTrack);
        }
    }

    private void activeSpeakerAbsent(){
        binding.activeSpeakerRenderer.nameTv.setVisibility(View.GONE);
        binding.activeSpeakerRenderer.hostImage.setVisibility(View.GONE);
        binding.activeSpeakerRenderer.microButton.setVisibility(View.GONE);
        binding.activeSpeakerRenderer.cameraButton.setVisibility(View.GONE);
        binding.activeSpeakerRenderer.renderer.clearImage();
    }

    public void updateTrack(@NonNull VideoTrack videoTrackNew){
        videoTrackNew.setEnabled(true);
        videoTrackNew.addSink(binding.activeSpeakerRenderer.renderer);
    }
}