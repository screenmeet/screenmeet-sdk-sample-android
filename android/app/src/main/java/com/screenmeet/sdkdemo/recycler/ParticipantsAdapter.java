package com.screenmeet.sdkdemo.recycler;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.screenmeet.sdk.Identity;
import com.screenmeet.sdk.Participant;
import com.screenmeet.sdkdemo.R;
import com.screenmeet.sdkdemo.databinding.ParticipantLayoutBinding;

import org.jetbrains.annotations.NotNull;
import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;

public class ParticipantsAdapter extends RecyclerView.Adapter<ParticipantsAdapter.ViewHolder> {

    private final ArrayList<Participant> participants;
    private final EglBase eglBase;

    private String activeParticipantId = "";
    private int recyclerSize = 0;

    public ParticipantsAdapter(ArrayList<Participant> participants, EglBase eglBase) {
        this.participants = participants;
        this.eglBase = eglBase;
    }

    @NotNull
    @Override
    public ParticipantsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        recyclerSize = parent.getMeasuredWidth();

        ParticipantLayoutBinding binding = ParticipantLayoutBinding.inflate(inflater);
        binding.surfaceViewRenderer.init(eglBase.getEglBaseContext(), null);
        binding.surfaceViewRenderer.setZOrderMediaOverlay(true);
        binding.surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        return new ViewHolder(binding.getRoot());
    }

    public void add(Participant participant) {
        participants.add(participant);
        notifyItemInserted(participants.size() - 1);
    }

    public void remove(Participant participant) {
        int index = participants.indexOf(participant);
        if(index != -1){
            participants.remove(index);
            if(activeParticipantId.equals(participant.getId())){
                activeParticipantId = "";
            }
            notifyItemRemoved(index);
        }
    }

    public void update(Participant participant) {
        int index = participants.indexOf(participant);
        if(index != -1){
            participants.set(index, participant);
            notifyItemChanged(index);
        }
    }

    public Participant updateActiveSpeaker(Participant participant) {
        int index = participants.indexOf(participant);
        if(index != -1){
            activeParticipantId = participant.getId();
            notifyDataSetChanged();
            return participants.get(index);
        } else return null;
    }

    @Nullable
    public Participant getActiveSpeaker() {
        for (Participant p : participants) {
            if(p.getId().equals(activeParticipantId)) return p;
        }
        return null;
    }

    public boolean activeSpeakerPresent() {
        return !activeParticipantId.isEmpty();
    }

    public boolean isActiveSpeaker(Participant participant) {
        Participant activeSpeaker = getActiveSpeaker();
        if (activeSpeaker != null) {
            return activeSpeaker.getId().equals(participant.getId());
        } else return false;
    }

    @Override
    public void onBindViewHolder(@NotNull ParticipantsAdapter.ViewHolder holder, int position) {
        Participant participant = participants.get(position);
        holder.displayParticipant(participant, recyclerSize, activeParticipantId);
    }

    // Returns the total count of items in the list
    @Override
    public int getItemCount() {
        return participants.size();
    }

    public void dispose(){
        for (Participant p: participants) {
            p.clearSinks();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public TextView nameTv;
        public ImageView micButton;
        public ImageView cameraButton;
        public ImageView hostImage;
        public SurfaceViewRenderer renderer;

        public ViewHolder(View itemView) {
            super(itemView);

            nameTv = itemView.findViewById(R.id.nameTv);
            micButton = itemView.findViewById(R.id.microButton);
            cameraButton = itemView.findViewById(R.id.cameraButton);
            hostImage = itemView.findViewById(R.id.hostImage);
            renderer = itemView.findViewById(R.id.surfaceViewRenderer);
        }

        void displayParticipant(Participant participant, int recyclerSize, String activeParticipantId){
            if(participant.getId().equals(activeParticipantId)){
                itemView.setVisibility(View.GONE);
                itemView.setLayoutParams(new RecyclerView.LayoutParams(0, 0));
                return;
            }

            itemView.setVisibility(View.VISIBLE);
            itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, recyclerSize));

            nameTv.setText(participant.getIdentity().getName());

            if(participant.getIdentity().getRole() == Identity.Role.HOST){
                hostImage.setVisibility(View.VISIBLE);
            } else hostImage.setVisibility(View.GONE);

            if(participant.getMediaState().isAudioActive()){
                micButton.setImageResource(R.drawable.mic);
            } else micButton.setImageResource(R.drawable.mic_off);

            switch (participant.getMediaState().getVideoState().getSource()){
                case BACK_CAMERA:
                case FRONT_CAMERA:
                case CUSTOM_CAMERA:
                    cameraButton.setImageResource(R.drawable.videocam);
                    updateTrack(participant);
                    break;
                case SCREEN:
                    cameraButton.setImageResource(R.drawable.screenshot);
                    updateTrack(participant);
                    break;
                case NONE:
                    cameraButton.setImageResource(R.drawable.videocam_off);
                    participant.clearSinks();
            }

            participant.logSinks();
        }

        public void updateTrack(Participant participant){
            participant.clearSinks();

            VideoTrack videoTrack = participant.getVideoTrack();
            if (videoTrack != null) {
                videoTrack.setEnabled(true);
                videoTrack.addSink(renderer);
            }
        }
    }
}