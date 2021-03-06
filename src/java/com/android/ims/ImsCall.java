/*
 * Copyright (c) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ims;

import com.android.internal.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.provider.Settings;
import android.telecom.ConferenceParticipant;
import android.telecom.Connection;
import android.telecom.TelecomManager;
import android.telephony.Rlog;
import android.util.Log;
import android.widget.Toast;

import com.android.ims.internal.CallGroup;
import com.android.ims.internal.CallGroupManager;
import com.android.ims.internal.ICall;
import com.android.ims.internal.ImsCallSession;
import com.android.ims.internal.ImsStreamMediaSession;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Handles an IMS voice / video call over LTE. You can instantiate this class with
 * {@link ImsManager}.
 *
 * @hide
 */
public class ImsCall implements ICall {
    public static final int CALL_STATE_ACTIVE_TO_HOLD = 1;
    public static final int CALL_STATE_HOLD_TO_ACTIVE = 2;

    // Mode of USSD message
    public static final int USSD_MODE_NOTIFY = 0;
    public static final int USSD_MODE_REQUEST = 1;

    private static final String TAG = "ImsCall";
    private static final boolean FORCE_DEBUG = false; /* STOPSHIP if true */
    private static final boolean DBG = FORCE_DEBUG || Rlog.isLoggable(TAG, Log.DEBUG);
    private static final boolean VDBG = FORCE_DEBUG || Rlog.isLoggable(TAG, Log.VERBOSE);

    private List<ConferenceParticipant> mConferenceParticipants;
    private boolean mIsCEPPresent = false;
    /**
     * Listener for events relating to an IMS call, such as when a call is being
     * recieved ("on ringing") or a call is outgoing ("on calling").
     * <p>Many of these events are also received by {@link ImsCallSession.Listener}.</p>
     */
    public static class Listener {
        /**
         * Called when a request is sent out to initiate a new call
         * and 1xx response is received from the network.
         * The default implementation calls {@link #onCallStateChanged}.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallProgressing(ImsCall call) {
            onCallStateChanged(call);
        }

        /**
         * Called when the call is established.
         * The default implementation calls {@link #onCallStateChanged}.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallStarted(ImsCall call) {
            onCallStateChanged(call);
        }

        /**
         * Called when the call setup is failed.
         * The default implementation calls {@link #onCallError}.
         *
         * @param call the call object that carries out the IMS call
         * @param reasonInfo detailed reason of the call setup failure
         */
        public void onCallStartFailed(ImsCall call, ImsReasonInfo reasonInfo) {
            onCallError(call, reasonInfo);
        }

        /**
         * Called when the call is terminated.
         * The default implementation calls {@link #onCallStateChanged}.
         *
         * @param call the call object that carries out the IMS call
         * @param reasonInfo detailed reason of the call termination
         */
        public void onCallTerminated(ImsCall call, ImsReasonInfo reasonInfo) {
            // Store the call termination reason

            onCallStateChanged(call);
        }

        /**
         * Called when the call is in hold.
         * The default implementation calls {@link #onCallStateChanged}.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallHeld(ImsCall call) {
            onCallStateChanged(call);
        }

        /**
         * Called when the call hold is failed.
         * The default implementation calls {@link #onCallError}.
         *
         * @param call the call object that carries out the IMS call
         * @param reasonInfo detailed reason of the call hold failure
         */
        public void onCallHoldFailed(ImsCall call, ImsReasonInfo reasonInfo) {
            onCallError(call, reasonInfo);
        }

        /**
         * Called when the call hold is received from the remote user.
         * The default implementation calls {@link #onCallStateChanged}.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallHoldReceived(ImsCall call) {
            onCallStateChanged(call);
        }

        /**
         * Called when the call is in call.
         * The default implementation calls {@link #onCallStateChanged}.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallResumed(ImsCall call) {
            onCallStateChanged(call);
        }

        /**
         * Called when the call resume is failed.
         * The default implementation calls {@link #onCallError}.
         *
         * @param call the call object that carries out the IMS call
         * @param reasonInfo detailed reason of the call resume failure
         */
        public void onCallResumeFailed(ImsCall call, ImsReasonInfo reasonInfo) {
            onCallError(call, reasonInfo);
        }

        /**
         * Called when the call resume is received from the remote user.
         * The default implementation calls {@link #onCallStateChanged}.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallResumeReceived(ImsCall call) {
            onCallStateChanged(call);
        }

        /**
         * Called when the call is in call.
         * The default implementation calls {@link #onCallStateChanged}.
         *
         * @param call the call object that carries out the IMS call
         * @param newCall the call object that is merged with an active & hold call
         */
        public void onCallMerged(ImsCall call) {
            onCallStateChanged(call);
        }

        /**
         * Called when the call merge is failed.
         * The default implementation calls {@link #onCallError}.
         *
         * @param call the call object that carries out the IMS call
         * @param reasonInfo detailed reason of the call merge failure
         */
        public void onCallMergeFailed(ImsCall call, ImsReasonInfo reasonInfo) {
            onCallError(call, reasonInfo);
        }

        /**
         * Called when the call is updated (except for hold/unhold).
         * The default implementation calls {@link #onCallStateChanged}.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallUpdated(ImsCall call) {
            onCallStateChanged(call);
        }

        /**
         * Called when the call update is failed.
         * The default implementation calls {@link #onCallError}.
         *
         * @param call the call object that carries out the IMS call
         * @param reasonInfo detailed reason of the call update failure
         */
        public void onCallUpdateFailed(ImsCall call, ImsReasonInfo reasonInfo) {
            onCallError(call, reasonInfo);
        }

        /**
         * Called when the call update is received from the remote user.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallUpdateReceived(ImsCall call) {
            // no-op
        }

        /**
         * Called when the call is extended to the conference call.
         * The default implementation calls {@link #onCallStateChanged}.
         *
         * @param call the call object that carries out the IMS call
         * @param newCall the call object that is extended to the conference from the active call
         */
        public void onCallConferenceExtended(ImsCall call, ImsCall newCall) {
            onCallStateChanged(call);
        }

        /**
         * Called when the conference extension is failed.
         * The default implementation calls {@link #onCallError}.
         *
         * @param call the call object that carries out the IMS call
         * @param reasonInfo detailed reason of the conference extension failure
         */
        public void onCallConferenceExtendFailed(ImsCall call,
                ImsReasonInfo reasonInfo) {
            onCallError(call, reasonInfo);
        }

        /**
         * Called when the conference extension is received from the remote user.
         *
         * @param call the call object that carries out the IMS call
         * @param newCall the call object that is extended to the conference from the active call
         */
        public void onCallConferenceExtendReceived(ImsCall call, ImsCall newCall) {
            onCallStateChanged(call);
        }

        /**
         * Called when the invitation request of the participants is delivered to
         * the conference server.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallInviteParticipantsRequestDelivered(ImsCall call) {
            // no-op
        }

        /**
         * Called when the invitation request of the participants is failed.
         *
         * @param call the call object that carries out the IMS call
         * @param reasonInfo detailed reason of the conference invitation failure
         */
        public void onCallInviteParticipantsRequestFailed(ImsCall call,
                ImsReasonInfo reasonInfo) {
            // no-op
        }

        /**
         * Called when the removal request of the participants is delivered to
         * the conference server.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallRemoveParticipantsRequestDelivered(ImsCall call) {
            // no-op
        }

        /**
         * Called when the removal request of the participants is failed.
         *
         * @param call the call object that carries out the IMS call
         * @param reasonInfo detailed reason of the conference removal failure
         */
        public void onCallRemoveParticipantsRequestFailed(ImsCall call,
                ImsReasonInfo reasonInfo) {
            // no-op
        }

        /**
         * Called when the conference state is updated.
         *
         * @param call the call object that carries out the IMS call
         * @param state state of the participant who is participated in the conference call
         */
        public void onCallConferenceStateUpdated(ImsCall call, ImsConferenceState state) {
            // no-op
        }

        /**
         * Called when the state of IMS conference participant(s) has changed.
         *
         * @param call the call object that carries out the IMS call.
         * @param participants the participant(s) and their new state information.
         */
        public void onConferenceParticipantsStateChanged(ImsCall call,
                List<ConferenceParticipant> participants) {
            // no-op
        }

        /**
         * Called when the USSD message is received from the network.
         *
         * @param mode mode of the USSD message (REQUEST / NOTIFY)
         * @param ussdMessage USSD message
         */
        public void onCallUssdMessageReceived(ImsCall call,
                int mode, String ussdMessage) {
            // no-op
        }

        /**
         * Called when an error occurs. The default implementation is no op.
         * overridden. The default implementation is no op. Error events are
         * not re-directed to this callback and are handled in {@link #onCallError}.
         *
         * @param call the call object that carries out the IMS call
         * @param reasonInfo detailed reason of this error
         * @see ImsReasonInfo
         */
        public void onCallError(ImsCall call, ImsReasonInfo reasonInfo) {
            // no-op
        }

        /**
         * Called when an event occurs and the corresponding callback is not
         * overridden. The default implementation is no op. Error events are
         * not re-directed to this callback and are handled in {@link #onCallError}.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallStateChanged(ImsCall call) {
            // no-op
        }

        /**
         * Called when the call moves the hold state to the conversation state.
         * For example, when merging the active & hold call, the state of all the hold call
         * will be changed from hold state to conversation state.
         * This callback method can be invoked even though the application does not trigger
         * any operations.
         *
         * @param call the call object that carries out the IMS call
         * @param state the detailed state of call state changes;
         *      Refer to CALL_STATE_* in {@link ImsCall}
         */
        public void onCallStateChanged(ImsCall call, int state) {
            // no-op
        }

        /**
         * Called when the call supp service is received
         * The default implementation calls {@link #onCallStateChanged}.
         *
         * @param call the call object that carries out the IMS call
         */
        public void onCallSuppServiceReceived(ImsCall call,
            ImsSuppServiceNotification suppServiceInfo) {
        }

        /**
         * Called when handover occurs from one access technology to another.
         *
         * @param session IMS session object
         * @param srcAccessTech original access technology
         * @param targetAccessTech new access technology
         * @param reasonInfo
         */
        public void onCallHandover(ImsCall imsCall, int srcAccessTech, int targetAccessTech,
            ImsReasonInfo reasonInfo) {
        }

        /**
         * Called when handover from one access technology to another fails.
         *
         * @param session IMS session object
         * @param srcAccessTech original access technology
         * @param targetAccessTech new access technology
         * @param reasonInfo
         */
        public void onCallHandoverFailed(ImsCall imsCall, int srcAccessTech, int targetAccessTech,
            ImsReasonInfo reasonInfo) {
        }

        /*
         * Called when TTY mode of remote party changed
         *
         * @param call the call object that carries out the IMS call
         * @param mode TTY mode of remote party
         */
        public void onCallSessionTtyModeReceived(ImsCall call, int mode) {
            // no-op
        }
    }



    // List of update operation for IMS call control
    private static final int UPDATE_NONE = 0;
    private static final int UPDATE_HOLD = 1;
    private static final int UPDATE_HOLD_MERGE = 2;
    private static final int UPDATE_RESUME = 3;
    private static final int UPDATE_MERGE = 4;
    private static final int UPDATE_EXTEND_TO_CONFERENCE = 5;
    private static final int UPDATE_UNSPECIFIED = 6;

    // For synchronization of private variables
    private Object mLockObj = new Object();
    private Context mContext;

    // true if the call is established & in the conversation state
    private boolean mInCall = false;
    // true if the call is on hold
    // If it is triggered by the local, mute the call. Otherwise, play local hold tone
    // or network generated media.
    private boolean mHold = false;
    // true if the call is on mute
    private boolean mMute = false;
    // It contains the exclusive call update request. Refer to UPDATE_*.
    private int mUpdateRequest = UPDATE_NONE;

    private ImsCall.Listener mListener = null;
    // It is for managing the multiple calls
    // when the multiparty call is extended to the conference.
    private CallGroup mCallGroup = null;

    // Wrapper call session to interworking the IMS service (server).
    private ImsCallSession mSession = null;
    // Call profile of the current session.
    // It can be changed at anytime when the call is updated.
    private ImsCallProfile mCallProfile = null;
    // Call profile to be updated after the application's action (accept/reject)
    // to the call update. After the application's action (accept/reject) is done,
    // it will be set to null.
    private ImsCallProfile mProposedCallProfile = null;
    private ImsReasonInfo mLastReasonInfo = null;

    // Media session to control media (audio/video) operations for an IMS call
    private ImsStreamMediaSession mMediaSession = null;

    // The temporary ImsCallSession that could represent the merged call once
    // we receive notification that the merge was successful.
    private ImsCallSession mTransientConferenceSession = null;
    // While a merge is progressing, we bury any session termination requests
    // made on the original ImsCallSession until we have closure on the merge request
    // If the request ultimately fails, we need to act on the termination request
    // that we buried temporarily. We do this because we feel that timing issues could
    // cause the termination request to occur just because the merge is succeeding.
    private boolean mSessionEndDuringMerge = false;
    // Just like mSessionEndDuringMerge, we need to keep track of the reason why the
    // termination request was made on the original session in case we need to act
    // on it in the case of a merge failure.
    private ImsReasonInfo mSessionEndDuringMergeReasonInfo = null;
    private boolean mIsMerged = false;

    /**
     * Create an IMS call object.
     *
     * @param context the context for accessing system services
     * @param profile the call profile to make/take a call
     */
    public ImsCall(Context context, ImsCallProfile profile) {
        mContext = context;
        mCallProfile = profile;
    }

    public void updateHoldValues() {
        mHold = true;
    }

    /**
     * Closes this object. This object is not usable after being closed.
     */
    @Override
    public void close() {
        synchronized(mLockObj) {
            destroyCallGroup();

            if (mSession != null) {
                mSession.close();
                mSession = null;
            }

            mCallProfile = null;
            mProposedCallProfile = null;
            mLastReasonInfo = null;
            mMediaSession = null;
        }
    }

    /**
     * Checks if the call has a same remote user identity or not.
     *
     * @param userId the remote user identity
     * @return true if the remote user identity is equal; otherwise, false
     */
    @Override
    public boolean checkIfRemoteUserIsSame(String userId) {
        if (userId == null) {
            return false;
        }

        return userId.equals(mCallProfile.getCallExtra(ImsCallProfile.EXTRA_REMOTE_URI, ""));
    }

    /**
     * Checks if the call is equal or not.
     *
     * @param call the call to be compared
     * @return true if the call is equal; otherwise, false
     */
    @Override
    public boolean equalsTo(ICall call) {
        if (call == null) {
            return false;
        }

        if (call instanceof ImsCall) {
            return this.equals((ImsCall)call);
        }

        return false;
    }

    /**
     * Gets the negotiated (local & remote) call profile.
     *
     * @return a {@link ImsCallProfile} object that has the negotiated call profile
     */
    public ImsCallProfile getCallProfile() {
        synchronized(mLockObj) {
            return mCallProfile;
        }
    }

    /**
     * Gets the local call profile (local capabilities).
     *
     * @return a {@link ImsCallProfile} object that has the local call profile
     */
    public ImsCallProfile getLocalCallProfile() throws ImsException {
        synchronized(mLockObj) {
            if (mSession == null) {
                throw new ImsException("No call session",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            try {
                return mSession.getLocalCallProfile();
            } catch (Throwable t) {
                loge("getLocalCallProfile :: ", t);
                throw new ImsException("getLocalCallProfile()", t, 0);
            }
        }
    }

    /**
     * Gets the local call profile (local capabilities).
     *
     * @return a {@link ImsCallProfile} object that has the local call profile
     */
    public ImsCallProfile getRemoteCallProfile() throws ImsException {
        synchronized(mLockObj) {
            if (mSession == null) {
                throw new ImsException("No call session",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            try {
                return mSession.getRemoteCallProfile();
            } catch (Throwable t) {
                loge("getLocalCallProfile :: ", t);
                throw new ImsException("getRemoteCallProfile()", t, 0);
            }
        }
    }

    /**
     * Gets the call profile proposed by the local/remote user.
     *
     * @return a {@link ImsCallProfile} object that has the proposed call profile
     */
    public ImsCallProfile getProposedCallProfile() {
        synchronized(mLockObj) {
            if (!isInCall()) {
                return null;
            }

            return mProposedCallProfile;
        }
    }

    /**
     * Gets the list of conference participants currently
     * associated with this call.
     *
     * @return The list of conference participants.
     */
    public List<ConferenceParticipant> getConferenceParticipants() {
        synchronized(mLockObj) {
            log("getConferenceParticipants :: mConferenceParticipants"
                    + mConferenceParticipants);
            return mConferenceParticipants;
        }
    }

    /**
     * Gets the state of the {@link ImsCallSession} that carries this call.
     * The value returned must be one of the states in {@link ImsCallSession#State}.
     *
     * @return the session state
     */
    public int getState() {
        synchronized(mLockObj) {
            if (mSession == null) {
                return ImsCallSession.State.IDLE;
            }

            return mSession.getState();
        }
    }

    /**
     * Gets the {@link ImsCallSession} that carries this call.
     *
     * @return the session object that carries this call
     * @hide
     */
    public ImsCallSession getCallSession() {
        synchronized(mLockObj) {
            return mSession;
        }
    }

    /**
     * Gets the {@link ImsStreamMediaSession} that handles the media operation of this call.
     * Almost interface APIs are for the VT (Video Telephony).
     *
     * @return the media session object that handles the media operation of this call
     * @hide
     */
    public ImsStreamMediaSession getMediaSession() {
        synchronized(mLockObj) {
            return mMediaSession;
        }
    }

    /**
     * Gets the specified property of this call.
     *
     * @param name key to get the extra call information defined in {@link ImsCallProfile}
     * @return the extra call information as string
     */
    public String getCallExtra(String name) throws ImsException {
        // Lookup the cache

        synchronized(mLockObj) {
            // If not found, try to get the property from the remote
            if (mSession == null) {
                throw new ImsException("No call session",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            try {
                return mSession.getProperty(name);
            } catch (Throwable t) {
                loge("getCallExtra :: ", t);
                throw new ImsException("getCallExtra()", t, 0);
            }
        }
    }

    /**
     * Gets the call substate.
     *
     * @return int callsubstate
     */
    public int getCallSubstate() throws ImsException {
        synchronized(mLockObj) {
            if (mSession == null) {
                throw new ImsException("No call session",
                    ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }
            try {
                return mSession.getCallSubstate();
            } catch (Throwable t) {
                loge("getCallSubstate :: ", t);
                throw new ImsException("getCallSubstate()", t, 0);
            }
        }
    }

    /**
     * Gets the last reason information when the call is not established, cancelled or terminated.
     *
     * @return the last reason information
     */
    public ImsReasonInfo getLastReasonInfo() {
        synchronized(mLockObj) {
            return mLastReasonInfo;
        }
    }

    /**
     * Checks if the call has a pending update operation.
     *
     * @return true if the call has a pending update operation
     */
    public boolean hasPendingUpdate() {
        synchronized(mLockObj) {
            return (mUpdateRequest != UPDATE_NONE);
        }
    }

    /**
     * Checks if the call is established.
     *
     * @return true if the call is established
     */
    public boolean isInCall() {
        synchronized(mLockObj) {
            return mInCall;
        }
    }

    /**
     * Checks if the call is muted.
     *
     * @return true if the call is muted
     */
    public boolean isMuted() {
        synchronized(mLockObj) {
            return mMute;
        }
    }

    /**
     * Checks if the call is on hold.
     *
     * @return true if the call is on hold
     */
    public boolean isOnHold() {
        synchronized(mLockObj) {
            return mHold;
        }
    }

    /**
     * Determines if the call is a multiparty call.
     *
     * @return {@code True} if the call is a multiparty call.
     */
    public boolean isMultiparty() {
        synchronized(mLockObj) {
            if (mSession == null) {
                return false;
            }

            return mSession.isMultiparty();
        }
    }

    /**
     * Marks whether an IMS call is merged. This should be {@code true} if the call becomes the
     * member of a {@code CallGroup} of which it is not the owner. This should be set back to
     * {@code false} if a merge fails.
     *
     * @param isMerged Whether the call is merged.
     */
    public void setIsMerged(boolean isMerged) {
        mIsMerged = isMerged;
    }

    /**
     * @return {@code true} if the call is the member of a {@code CallGroup} of which it is not the
     *     owner, {@code false} otherwise.
     */
    public boolean isMerged() {
        return mIsMerged;
    }

    /**
     * Sets the listener to listen to the IMS call events.
     * The method calls {@link #setListener setListener(listener, false)}.
     *
     * @param listener to listen to the IMS call events of this object; null to remove listener
     * @see #setListener(Listener, boolean)
     */
    public void setListener(ImsCall.Listener listener) {
        setListener(listener, false);
    }

    /**
     * Sets the listener to listen to the IMS call events.
     * A {@link ImsCall} can only hold one listener at a time. Subsequent calls
     * to this method override the previous listener.
     *
     * @param listener to listen to the IMS call events of this object; null to remove listener
     * @param callbackImmediately set to true if the caller wants to be called
     *        back immediately on the current state
     */
    public void setListener(ImsCall.Listener listener, boolean callbackImmediately) {
        boolean inCall;
        boolean onHold;
        int state;
        ImsReasonInfo lastReasonInfo;

        synchronized(mLockObj) {
            mListener = listener;

            if ((listener == null) || !callbackImmediately) {
                return;
            }

            inCall = mInCall;
            onHold = mHold;
            state = getState();
            lastReasonInfo = mLastReasonInfo;
        }

        try {
            if (lastReasonInfo != null) {
                listener.onCallError(this, lastReasonInfo);
            } else if (inCall) {
                if (onHold) {
                    listener.onCallHeld(this);
                } else {
                    listener.onCallStarted(this);
                }
            } else {
                switch (state) {
                    case ImsCallSession.State.ESTABLISHING:
                        listener.onCallProgressing(this);
                        break;
                    case ImsCallSession.State.TERMINATED:
                        listener.onCallTerminated(this, lastReasonInfo);
                        break;
                    default:
                        // Ignore it. There is no action in the other state.
                        break;
                }
            }
        } catch (Throwable t) {
            loge("setListener()", t);
        }
    }

    /**
     * Mutes or unmutes the mic for the active call.
     *
     * @param muted true if the call is muted, false otherwise
     */
    public void setMute(boolean muted) throws ImsException {
        synchronized(mLockObj) {
            if (mMute != muted) {
                mMute = muted;

                try {
                    mSession.setMute(muted);
                } catch (Throwable t) {
                    loge("setMute :: ", t);
                    throwImsException(t, 0);
                }
            }
        }
    }

     /**
      * Attaches an incoming call to this call object.
      *
      * @param session the session that receives the incoming call
      * @throws ImsException if the IMS service fails to attach this object to the session
      */
     public void attachSession(ImsCallSession session) throws ImsException {
         if (DBG) {
             log("attachSession :: session=" + session);
         }

         synchronized(mLockObj) {
             mSession = session;

             try {
                 mSession.setListener(createCallSessionListener());
             } catch (Throwable t) {
                 loge("attachSession :: ", t);
                 throwImsException(t, 0);
             }
         }
     }

    /**
     * Initiates an IMS call with the call profile which is provided
     * when creating a {@link ImsCall}.
     *
     * @param session the {@link ImsCallSession} for carrying out the call
     * @param callee callee information to initiate an IMS call
     * @throws ImsException if the IMS service fails to initiate the call
     */
    public void start(ImsCallSession session, String callee)
            throws ImsException {
        if (DBG) {
            log("start(1) :: session=" + session + ", callee=" + callee);
        }

        synchronized(mLockObj) {
            mSession = session;

            try {
                session.setListener(createCallSessionListener());
                session.start(callee, mCallProfile);
            } catch (Throwable t) {
                loge("start(1) :: ", t);
                throw new ImsException("start(1)", t, 0);
            }
        }
    }

    /**
     * Initiates an IMS conferenca call with the call profile which is provided
     * when creating a {@link ImsCall}.
     *
     * @param session the {@link ImsCallSession} for carrying out the call
     * @param participants participant list to initiate an IMS conference call
     * @throws ImsException if the IMS service fails to initiate the call
     */
    public void start(ImsCallSession session, String[] participants)
            throws ImsException {
        if (DBG) {
            log("start(n) :: session=" + session + ", callee=" + participants);
        }

        synchronized(mLockObj) {
            mSession = session;

            try {
                session.setListener(createCallSessionListener());
                session.start(participants, mCallProfile);
            } catch (Throwable t) {
                loge("start(n) :: ", t);
                throw new ImsException("start(n)", t, 0);
            }
        }
    }

    /**
     * Accepts a call.
     *
     * @see Listener#onCallStarted
     *
     * @param callType The call type the user agreed to for accepting the call.
     * @throws ImsException if the IMS service fails to accept the call
     */
    public void accept(int callType) throws ImsException {
        if (DBG) {
            log("accept :: session=" + mSession);
        }

        accept(callType, new ImsStreamMediaProfile());
    }

    /**
     * Accepts a call.
     *
     * @param callType call type to be answered in {@link ImsCallProfile}
     * @param profile a media profile to be answered (audio/audio & video, direction, ...)
     * @see Listener#onCallStarted
     * @throws ImsException if the IMS service fails to accept the call
     */
    public void accept(int callType, ImsStreamMediaProfile profile) throws ImsException {
        if (DBG) {
            log("accept :: session=" + mSession
                    + ", callType=" + callType + ", profile=" + profile);
        }

        synchronized(mLockObj) {
            if (mSession == null) {
                throw new ImsException("No call to answer",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            try {
                mSession.accept(callType, profile);
            } catch (Throwable t) {
                loge("accept :: ", t);
                throw new ImsException("accept()", t, 0);
            }

            if (mInCall && (mProposedCallProfile != null)) {
                if (DBG) {
                    log("accept :: call profile will be updated");
                }

                mCallProfile = mProposedCallProfile;
                mProposedCallProfile = null;
            }

            // Other call update received
            if (mInCall && (mUpdateRequest == UPDATE_UNSPECIFIED)) {
                mUpdateRequest = UPDATE_NONE;
            }
        }
    }

    /**
     * Deflects a call.
     *
     * @param number number to be deflected to.
     * @throws ImsException if the IMS service fails to accept the call
     */
    public void deflect(String number) throws ImsException {
        if (DBG) {
            log("deflect :: session=" + mSession
                    + ", number=" + number);
        }

        synchronized(mLockObj) {
            if (mSession == null) {
                throw new ImsException("No call to deflect",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            try {
                mSession.deflect(number);
            } catch (Throwable t) {
                loge("deflect :: ", t);
                throw new ImsException("deflect()", t, 0);
            }
        }
    }

    /**
     * Rejects a call.
     *
     * @param reason reason code to reject an incoming call
     * @see Listener#onCallStartFailed
     * @throws ImsException if the IMS service fails to accept the call
     */
    public void reject(int reason) throws ImsException {
        if (DBG) {
            log("reject :: session=" + mSession + ", reason=" + reason);
        }

        synchronized(mLockObj) {
            if (mSession != null) {
                mSession.reject(reason);
            }

            if (mInCall && (mProposedCallProfile != null)) {
                if (DBG) {
                    log("reject :: call profile is not updated; destroy it...");
                }

                mProposedCallProfile = null;
            }

            // Other call update received
            if (mInCall && (mUpdateRequest == UPDATE_UNSPECIFIED)) {
                mUpdateRequest = UPDATE_NONE;
            }
        }
    }

    /**
     * Terminates an IMS call.
     *
     * @param reason reason code to terminate a call
     * @throws ImsException if the IMS service fails to terminate the call
     */
    public void terminate(int reason) throws ImsException {
        if (DBG) {
            log("terminate :: session=" + mSession + ", reason=" + reason);
        }

        synchronized(mLockObj) {
            mHold = false;
            mInCall = false;
            CallGroup callGroup = getCallGroup();

            if (mSession != null) {
                if (callGroup != null && !callGroup.isOwner(ImsCall.this)) {
                    log("terminate owner of the call group");
                    ImsCall owner = (ImsCall) callGroup.getOwner();
                    if (owner != null) {
                        owner.terminate(reason);
                        return;
                    }
                }
                mSession.terminate(reason);
            }
        }
    }


    /**
     * Puts a call on hold. When succeeds, {@link Listener#onCallHeld} is called.
     *
     * @see Listener#onCallHeld, Listener#onCallHoldFailed
     * @throws ImsException if the IMS service fails to hold the call
     */
    public void hold() throws ImsException {
        if (DBG) {
            log("hold :: session=" + mSession);
        }

        // perform operation on owner before doing any local checks: local
        // call may not have its status updated
        synchronized (mLockObj) {
            CallGroup callGroup = mCallGroup;
            if (callGroup != null && !callGroup.isOwner(ImsCall.this)) {
                log("hold owner of the call group");
                ImsCall owner = (ImsCall) callGroup.getOwner();
                if (owner != null) {
                    owner.hold();
                    return;
                }
            }
        }

        if (isOnHold()) {
            if (DBG) {
                log("hold :: call is already on hold");
            }
            return;
        }

        synchronized(mLockObj) {
            if (mUpdateRequest != UPDATE_NONE) {
                loge("hold :: update is in progress; request=" + mUpdateRequest);
                throw new ImsException("Call update is in progress",
                        ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE);
            }

            if (mSession == null) {
                loge("hold :: ");
                throw new ImsException("No call session",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            mSession.hold(createHoldMediaProfile());
            // FIXME: update the state on the callback?
            mHold = true;
            mUpdateRequest = UPDATE_HOLD;
        }
    }

    /**
     * Continues a call that's on hold. When succeeds, {@link Listener#onCallResumed} is called.
     *
     * @see Listener#onCallResumed, Listener#onCallResumeFailed
     * @throws ImsException if the IMS service fails to resume the call
     */
    public void resume() throws ImsException {
        if (DBG) {
            log("resume :: session=" + mSession);
        }

        // perform operation on owner before doing any local checks: local
        // call may not have its status updated
        synchronized (mLockObj) {
            CallGroup callGroup = mCallGroup;
            if (callGroup != null && !callGroup.isOwner(ImsCall.this)) {
                log("resume owner of the call group");
                ImsCall owner = (ImsCall) callGroup.getOwner();
                if (owner != null) {
                    owner.resume();
                    return;
                }
            }
        }

        if (!isOnHold()) {
            if (DBG) {
                log("resume :: call is in conversation");
            }
            return;
        }

        synchronized(mLockObj) {
            if (mUpdateRequest != UPDATE_NONE) {
                loge("resume :: update is in progress; request=" + mUpdateRequest);
                throw new ImsException("Call update is in progress",
                        ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE);
            }

            if (mSession == null) {
                loge("resume :: ");
                throw new ImsException("No call session",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            mSession.resume(createResumeMediaProfile());
            // FIXME: update the state on the callback?
            mHold = false;
            mUpdateRequest = UPDATE_RESUME;
        }
    }

    /**
     * Merges the active & hold call.
     *
     * @see Listener#onCallMerged, Listener#onCallMergeFailed
     * @throws ImsException if the IMS service fails to merge the call
     */
    public void merge() throws ImsException {
        if (DBG) {
            log("merge :: session=" + mSession);
        }

        synchronized(mLockObj) {
            if (mUpdateRequest != UPDATE_NONE) {
                loge("merge :: update is in progress; request=" + mUpdateRequest);
                throw new ImsException("Call update is in progress",
                        ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE);
            }

            if (mSession == null) {
                loge("merge :: ");
                throw new ImsException("No call session",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            // if skipHoldBeforeMerge = true, IMS service implementation will
            // merge without explicitly holding the call.
            if (mHold || (mContext.getResources().getBoolean(
                    com.android.internal.R.bool.skipHoldBeforeMerge))) {
                mSession.merge();

                // Check to see if there is an owner to a valid call group.  If this is the
                // case, then we already have a conference call.
                if (mCallGroup != null) {
                    if (mCallGroup.getOwner() == null) {
                        // We only set UPDATE_MERGE when we are adding the first
                        // calls to the Conference.  If there is already a conference
                        // no special handling is needed.The existing conference
                        // session will just go active and any other sessions will be terminated
                        // if needed.  There will be no merge failed callback.
                        mUpdateRequest = UPDATE_MERGE;
                    } else {
                        setIsMerged(true);
                    }
                }
            } else {
                // This code basically says, we need to explicitly hold before requesting a merge
                // when we get the callback that the hold was successful (or failed), we should
                // automatically request a merge.
                mSession.hold(createHoldMediaProfile());
                mHold = true;
                mUpdateRequest = UPDATE_HOLD_MERGE;
            }
        }
    }

    /**
     * Merges the active & hold call.
     *
     * @param bgCall the background (holding) call
     * @see Listener#onCallMerged, Listener#onCallMergeFailed
     * @throws ImsException if the IMS service fails to merge the call
     */
    public void merge(ImsCall bgCall) throws ImsException {
        if (DBG) {
            log("merge(1) :: session=" + mSession);
        }

        if (bgCall == null) {
            throw new ImsException("No background call",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_ARGUMENT);
        }

        synchronized(mLockObj) {
            createCallGroup(bgCall);
        }

        updateConferenceParticipantsList(bgCall);

        merge();
    }

    /**
     * Updates the current call's properties (ex. call mode change: video upgrade / downgrade).
     */
    public void update(int callType, ImsStreamMediaProfile mediaProfile) throws ImsException {
        if (DBG) {
            log("update :: session=" + mSession);
        }

        if (isOnHold()) {
            if (DBG) {
                log("update :: call is on hold");
            }
            throw new ImsException("Not in a call to update call",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE);
        }

        synchronized(mLockObj) {
            if (mUpdateRequest != UPDATE_NONE) {
                if (DBG) {
                    log("update :: update is in progress; request=" + mUpdateRequest);
                }
                throw new ImsException("Call update is in progress",
                        ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE);
            }

            if (mSession == null) {
                loge("update :: ");
                throw new ImsException("No call session",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            mSession.update(callType, mediaProfile);
            mUpdateRequest = UPDATE_UNSPECIFIED;
        }
    }

    /**
     * Extends this call (1-to-1 call) to the conference call
     * inviting the specified participants to.
     *
     */
    public void extendToConference(String[] participants) throws ImsException {
        if (DBG) {
            log("extendToConference :: session=" + mSession);
        }

        if (isOnHold()) {
            if (DBG) {
                log("extendToConference :: call is on hold");
            }
            throw new ImsException("Not in a call to extend a call to conference",
                    ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE);
        }

        synchronized(mLockObj) {
            if (mUpdateRequest != UPDATE_NONE) {
                if (DBG) {
                    log("extendToConference :: update is in progress; request=" + mUpdateRequest);
                }
                throw new ImsException("Call update is in progress",
                        ImsReasonInfo.CODE_LOCAL_ILLEGAL_STATE);
            }

            if (mSession == null) {
                loge("extendToConference :: ");
                throw new ImsException("No call session",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            mSession.extendToConference(participants);
            mUpdateRequest = UPDATE_EXTEND_TO_CONFERENCE;
        }
    }

    /**
     * Requests the conference server to invite an additional participants to the conference.
     *
     */
    public void inviteParticipants(String[] participants) throws ImsException {
        if (DBG) {
            log("inviteParticipants :: session=" + mSession);
        }

        synchronized(mLockObj) {
            if (mSession == null) {
                loge("inviteParticipants :: ");
                throw new ImsException("No call session",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            mSession.inviteParticipants(participants);
        }
    }

    /**
     * Requests the conference server to remove the specified participants from the conference.
     *
     */
    public void removeParticipants(String[] participants) throws ImsException {
        if (DBG) {
            log("removeParticipants :: session=" + mSession);
        }
        synchronized(mLockObj) {
            if (mSession == null) {
                loge("removeParticipants :: ");
                throw new ImsException("No call session",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            mSession.removeParticipants(participants);

            if (!mIsCEPPresent && participants != null && mConferenceParticipants != null) {
                for (String participant : participants) {
                    log ("Looping for participant " + participant);
                    for (ConferenceParticipant c : mConferenceParticipants) {
                        log ("Check handle for c = " + c.getHandle());
                        if (participant != null && Uri.parse(participant).equals(c.getHandle())) {
                            log ("Remove participant " + participant);
                            mConferenceParticipants.remove(c);
                            break;
                        }
                    }
                }
                if (mListener != null) {
                    try {
                        mListener.onConferenceParticipantsStateChanged(this,
                                mConferenceParticipants);
                    } catch (Throwable t) {
                        loge("removeparticipants :: ", t);
                    }
                }
            }
        }
    }


    /**
     * Sends a DTMF code. According to <a href="http://tools.ietf.org/html/rfc2833">RFC 2833</a>,
     * event 0 ~ 9 maps to decimal value 0 ~ 9, '*' to 10, '#' to 11, event 'A' ~ 'D' to 12 ~ 15,
     * and event flash to 16. Currently, event flash is not supported.
     *
     * @param char that represents the DTMF digit to send.
     */
    public void sendDtmf(char c) {
        sendDtmf(c, null);
    }

    /**
     * Sends a DTMF code. According to <a href="http://tools.ietf.org/html/rfc2833">RFC 2833</a>,
     * event 0 ~ 9 maps to decimal value 0 ~ 9, '*' to 10, '#' to 11, event 'A' ~ 'D' to 12 ~ 15,
     * and event flash to 16. Currently, event flash is not supported.
     *
     * @param c that represents the DTMF to send. '0' ~ '9', 'A' ~ 'D', '*', '#' are valid inputs.
     * @param result the result message to send when done.
     */
    public void sendDtmf(char c, Message result) {
        if (DBG) {
            log("sendDtmf :: session=" + mSession + ", code=" + c);
        }

        synchronized(mLockObj) {
            if (mSession != null) {
                mSession.sendDtmf(c);
            }
        }

        if (result != null) {
            result.sendToTarget();
        }
    }

    /**
     * Start a DTMF code. According to <a href="http://tools.ietf.org/html/rfc2833">RFC 2833</a>,
     * event 0 ~ 9 maps to decimal value 0 ~ 9, '*' to 10, '#' to 11, event 'A' ~ 'D' to 12 ~ 15,
     * and event flash to 16. Currently, event flash is not supported.
     *
     * @param c that represents the DTMF to send. '0' ~ '9', 'A' ~ 'D', '*', '#' are valid inputs.
     */
    public void startDtmf(char c) {
        if (DBG) {
            log("startDtmf :: session=" + mSession + ", code=" + c);
        }

        synchronized(mLockObj) {
            if (mSession != null) {
                mSession.startDtmf(c);
            }
        }
    }

    /**
     * Stop a DTMF code.
     */
    public void stopDtmf() {
        if (DBG) {
            log("stopDtmf :: session=" + mSession);
        }

        synchronized(mLockObj) {
            if (mSession != null) {
                mSession.stopDtmf();
            }
        }
    }

    /**
     * Sends an USSD message.
     *
     * @param ussdMessage USSD message to send
     */
    public void sendUssd(String ussdMessage) throws ImsException {
        if (DBG) {
            log("sendUssd :: session=" + mSession + ", ussdMessage=" + ussdMessage);
        }

        synchronized(mLockObj) {
            if (mSession == null) {
                loge("sendUssd :: ");
                throw new ImsException("No call session",
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED);
            }

            mSession.sendUssd(ussdMessage);
        }
    }

    private void clear(ImsReasonInfo lastReasonInfo) {
        mInCall = false;
        mHold = false;
        mUpdateRequest = UPDATE_NONE;
        mLastReasonInfo = lastReasonInfo;
        destroyCallGroup();
    }

    private void createCallGroup(ImsCall neutralReferrer) {
        CallGroup referrerCallGroup = neutralReferrer.getCallGroup();

        if (mCallGroup == null) {
            if (referrerCallGroup == null) {
                mCallGroup = CallGroupManager.getInstance().createCallGroup(new ImsCallGroup());
                neutralReferrer.setCallGroup(mCallGroup);
            } else {
                mCallGroup = referrerCallGroup;
            }

            if (mCallGroup != null) {
                mCallGroup.setNeutralReferrer(neutralReferrer);
            }
        } else {
            mCallGroup.setNeutralReferrer(neutralReferrer);

            if ((referrerCallGroup != null)
                    && (mCallGroup != referrerCallGroup)) {
                loge("fatal :: call group is mismatched; call is corrupted...");
            }
        }
    }

    private void destroyCallGroup() {
        if (mCallGroup == null) {
            return;
        }

        mCallGroup.removeReferrer(this);

        if (!mCallGroup.hasReferrer()) {
            CallGroupManager.getInstance().destroyCallGroup(mCallGroup);
        }

        mCallGroup = null;
    }

    public CallGroup getCallGroup() {
        synchronized(mLockObj) {
            return mCallGroup;
        }
    }

    public void setCallGroup(CallGroup callGroup) {
        synchronized(mLockObj) {
            mCallGroup = callGroup;
        }
    }

    /**
     * Creates an IMS call session listener.
     */
    private ImsCallSession.Listener createCallSessionListener() {
        return new ImsCallSessionListenerProxy();
    }

    private ImsCall createNewCall(ImsCallSession session, ImsCallProfile profile) {
        ImsCall call = new ImsCall(mContext, profile);

        try {
            call.attachSession(session);
        } catch (ImsException e) {
            if (call != null) {
                call.close();
                call = null;
            }
        }

        // Do additional operations...

        return call;
    }

    private ImsStreamMediaProfile createHoldMediaProfile() {
        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile();

        if (mCallProfile == null) {
            return mediaProfile;
        }

        mediaProfile.mAudioQuality = mCallProfile.mMediaProfile.mAudioQuality;
        mediaProfile.mVideoQuality = mCallProfile.mMediaProfile.mVideoQuality;
        mediaProfile.mAudioDirection = ImsStreamMediaProfile.DIRECTION_SEND;

        if (mediaProfile.mVideoQuality != ImsStreamMediaProfile.VIDEO_QUALITY_NONE) {
            mediaProfile.mVideoDirection = ImsStreamMediaProfile.DIRECTION_SEND;
        }

        return mediaProfile;
    }

    private ImsStreamMediaProfile createResumeMediaProfile() {
        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile();

        if (mCallProfile == null) {
            return mediaProfile;
        }

        mediaProfile.mAudioQuality = mCallProfile.mMediaProfile.mAudioQuality;
        mediaProfile.mVideoQuality = mCallProfile.mMediaProfile.mVideoQuality;
        mediaProfile.mAudioDirection = ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE;

        if (mediaProfile.mVideoQuality != ImsStreamMediaProfile.VIDEO_QUALITY_NONE) {
            mediaProfile.mVideoDirection = ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE;
        }

        return mediaProfile;
    }

    private void enforceConversationMode() {
        if (mInCall) {
            mHold = false;
            mUpdateRequest = UPDATE_NONE;
        }
    }

    private void mergeInternal() {
        if (DBG) {
            log("mergeInternal :: session=" + mSession);
        }

        mSession.merge();
        mUpdateRequest = UPDATE_MERGE;
    }

    private void notifyConferenceSessionTerminated(ImsReasonInfo reasonInfo) {
        ImsCall.Listener listener;
        if (mCallGroup.isOwner(ImsCall.this)) {
            log("Group Owner! Size of referrers list = " + mCallGroup.getReferrers().size());
            while (mCallGroup.hasReferrer()) {
                ImsCall call = (ImsCall) mCallGroup.getReferrers().get(0);
                log("onCallTerminated to be called for the call:: " + call);

                if (call == null) {
                    continue;
                }

                listener = call.mListener;
                call.clear(reasonInfo);

                if (listener != null) {
                    try {
                        listener.onCallTerminated(call, reasonInfo);
                    } catch (Throwable t) {
                        loge("notifyConferenceSessionTerminated :: ", t);
                    }
                }
            }
        }

        listener = mListener;
        clear(reasonInfo);

        if (listener != null) {
            try {
                listener.onCallTerminated(this, reasonInfo);
            } catch (Throwable t) {
                loge("notifyConferenceSessionTerminated :: ", t);
            }
        }
    }

    private void notifyConferenceStateUpdatedThroughGroupOwner(int update) {
        ImsCall.Listener listener;

        if (mCallGroup.isOwner(ImsCall.this)) {
            log("Group Owner! Size of referrers list = " + mCallGroup.getReferrers().size());
            for (ICall icall : mCallGroup.getReferrers()) {
                ImsCall call = (ImsCall) icall;
                log("notifyConferenceStateUpdatedThroughGroupOwner to be called for the call:: " +
                        call);

                if (call == null) {
                    continue;
                }

                listener = call.mListener;

                if (listener != null) {
                    try {
                        switch (update) {
                            case UPDATE_HOLD:
                                listener.onCallHeld(call);
                                break;
                            case UPDATE_RESUME:
                                listener.onCallResumed(call);
                                break;
                            default:
                                loge("notifyConferenceStateUpdatedThroughGroupOwner :: not " +
                                        "handled update " + update);
                        }
                    } catch (Throwable t) {
                        loge("notifyConferenceStateUpdatedThroughGroupOwner :: ", t);
                    }
                }
            }
        }
    }

    private void notifyConferenceStateUpdated(ImsConferenceState state) {
        Set<Entry<String, Bundle>> participants = state.mParticipants.entrySet();

        if (participants == null) {
            return;
        }

        Iterator<Entry<String, Bundle>> iterator = participants.iterator();
        List<ConferenceParticipant> conferenceParticipants = new ArrayList<>(participants.size());
        while (iterator.hasNext()) {
            Entry<String, Bundle> entry = iterator.next();

            String key = entry.getKey();
            Bundle confInfo = entry.getValue();
            String status = confInfo.getString(ImsConferenceState.STATUS);
            String user = confInfo.getString(ImsConferenceState.USER);
            String displayName = confInfo.getString(ImsConferenceState.DISPLAY_TEXT);
            String endpoint = confInfo.getString(ImsConferenceState.ENDPOINT);

            if (DBG) {
                log("notifyConferenceStateUpdated :: key=" + key +
                        ", status=" + status +
                        ", user=" + user +
                        ", displayName= " + displayName +
                        ", endpoint=" + endpoint);
            }

            /**
             *  The lines below are not necessary as the owner of call group
             *  is not set until merge is complete. As a result when there is
             *  CEP update received before merge is complete, the update is
             *  lost due to the check below. Moreover, the call group itself
             *  is removed for subsequent release.
             */
            //if ((mCallGroup != null) && (!mCallGroup.isOwner(ImsCall.this))) {
            //    continue;
            //}
            // Attempt to find the participant in the call group if it exists.
            ImsCall referrer = null;
            if (mCallGroup != null && endpoint != null && !endpoint.isEmpty()) {
                referrer = (ImsCall) mCallGroup.getReferrer(endpoint);
            }

            // Participant is not being represented by an ImsCall, so handle as generic participant.
            // Notify the {@code ImsPhoneCallTracker} of the participant state change so that it
            // can be passed up to the {@code TelephonyConferenceController}.
            if (referrer == null) {
                Uri handle = Uri.parse(user);
                if (endpoint == null) {
                    endpoint = "";
                }

                Uri endpointUri = Uri.parse(endpoint);
                int connectionState = ImsConferenceState.getConnectionStateForStatus(status);

                if (connectionState != Connection.STATE_DISCONNECTED) {
                    ConferenceParticipant conferenceParticipant = new ConferenceParticipant(handle,
                            displayName, endpointUri, connectionState);
                    conferenceParticipants.add(conferenceParticipant);
                }
                continue;
            }

            if (referrer.mListener == null) {
                continue;
            }

            try {
                if (status.equals(ImsConferenceState.STATUS_ALERTING)) {
                    referrer.mListener.onCallProgressing(referrer);
                }
                else if (status.equals(ImsConferenceState.STATUS_CONNECT_FAIL)) {
                    referrer.mListener.onCallStartFailed(referrer, new ImsReasonInfo());
                }
                else if (status.equals(ImsConferenceState.STATUS_ON_HOLD)) {
                    referrer.mListener.onCallHoldReceived(referrer);
                }
                else if (status.equals(ImsConferenceState.STATUS_CONNECTED)) {
                    referrer.mListener.onCallStarted(referrer);
                }
                else if (status.equals(ImsConferenceState.STATUS_DISCONNECTED)) {
                    referrer.clear(new ImsReasonInfo());
                    referrer.mListener.onCallTerminated(referrer, referrer.mLastReasonInfo);
                }
            } catch (Throwable t) {
                loge("notifyConferenceStateUpdated :: ", t);
            }
        }

        synchronized(mLockObj) {
            // Replace the participants list with the one received from latest CEP indication.
            mConferenceParticipants = conferenceParticipants;
            mIsCEPPresent = true;
            if (mListener != null) {
                try {
                    mListener.onConferenceParticipantsStateChanged(this, mConferenceParticipants);
                } catch (Throwable t) {
                    loge("notifyConferenceStateUpdated :: ", t);
                }
            }
        }
    }

    /**
     * Perform all cleanup and notification around the termination of a session.
     * Note that there are 2 distinct modes of operation.  The first is when
     * we receive a session termination on the primary session when we are
     * in the processing of merging.  The second is when we are not merging anything
     * and the call is terminated.
     *
     * @param reasonInfo The reason for the session termination
     */
    private void processCallTerminated(ImsReasonInfo reasonInfo) {
        if (DBG) {
            String sessionString = mSession != null ? mSession.toString() : "null";
            String transientSessionString = mTransientConferenceSession != null ?
                    mTransientConferenceSession.toString() : "null";
            String reasonString = reasonInfo != null ? reasonInfo.toString() : "null";
            log("processCallTerminated :: session=" + sessionString + " transientSession=" +
                    transientSessionString + " reason=" + reasonString);
        }

        ImsCall.Listener listener = null;

        synchronized(ImsCall.this) {
            if (mUpdateRequest == UPDATE_MERGE) {
                // Since we are in the process of a merge, this trigger means something
                // else because it is probably due to the merge happening vs. the
                // session is really terminated. Let's flag this and revisit if
                // the merge() ends up failing because we will need to take action on the
                // mSession in that case since the termination was not due to the merge
                // succeeding.
                if (DBG) {
                    log("processCallTerminated :: burying termination during ongoing merge.");
                }
                mSessionEndDuringMerge = true;
                mSessionEndDuringMergeReasonInfo = reasonInfo;

                // Since this call is the foreground call that sent the merge
                // request, ending signifies that the call successfully got
                // merged into the conference call only if mTransientConferenceSession exists.
                // Else this is a genuine call end of this session & callTerminated will be invoked.
                if (mTransientConferenceSession != null) {
                    processMergeComplete();
                    return;
                }
            }

            // If this condition is satisfied, this call is either a part of
            // a conference call or a call that is about to be merged into an
            // existing conference call.
            if (mCallGroup != null) {
                notifyConferenceSessionTerminated(reasonInfo);
            } else {
                listener = mListener;
                clear(reasonInfo);
            }
            mIsCEPPresent = false;
        }

        if (listener != null) {
            try {
                listener.onCallTerminated(ImsCall.this, reasonInfo);
            } catch (Throwable t) {
                loge("callSessionTerminated :: ", t);
            }
        }
    }

    /**
     * This function determines if the ImsCallSession is our actual ImsCallSession or if is
     * the transient session used in the process of creating a conference. This function should only
     * be called within  callbacks that are not directly related to conference merging but might
     * potentially still be called on the transient ImsCallSession sent to us from
     * callSessionMergeStarted() when we don't really care. In those situations, we probably don't
     * want to take any action so we need to know that we can return early.
     *
     * @param session - The {@link ImsCallSession} that the function needs to analyze
     * @return true if this is the transient {@link ImsCallSession}, false otherwise.
     */
    private boolean isTransientConferenceSession(ImsCallSession session) {
        if (session != null && session != mSession && session == mTransientConferenceSession) {
            return true;
        }
        return false;
    }

    /**
     * We received a callback from ImsCallSession that a merge was complete. Clean up all
     * internal state to represent this state change. This function will be called when
     * the transient conference session goes active or we get an explicit merge complete
     * callback on the transient session.
     *
     */
    private void processMergeComplete() {
        if (DBG) {
            String sessionString = mSession != null ? mSession.toString() : "null";
            String transientSessionString = mTransientConferenceSession != null ?
                    mTransientConferenceSession.toString() : "null";
            log("processMergeComplete :: session=" + sessionString + " transientSession=" +
                    transientSessionString);
        }

        ImsCall.Listener listener;
        synchronized(ImsCall.this) {
            listener = mListener;
            if (mTransientConferenceSession != null) {
                // Swap out the underlying sessions after shutting down the existing session.
                mSession.setListener(null);
                mSession = mTransientConferenceSession;
                mTransientConferenceSession = null;
                // We need to set ourselves as the owner of the call group to indicate that
                // a conference call is in progress.
                mCallGroup.setOwner(ImsCall.this);
                listener = mListener;

                // Remove the call group's neutral referrer as its not needed anyways
                // If we retain this referrer, it causes issues during conference
                // success scenario with one refer failing.
                ImsCall neutralReferrer = (ImsCall) mCallGroup.getNeutralReferrer();
                if (neutralReferrer != null) {
                    mCallGroup.removeReferrer(neutralReferrer);
                    neutralReferrer.mCallGroup = null;
                }
            } else {
                // This is an interesting state that needs to be logged since we
                // should only be going through this workflow for new conference calls
                // and not merges into existing conferences (which a null transient
                // session would imply)
                log("processMergeComplete :: ERROR no transient session");
            }
            // Clear some flags.  If the merge eventually worked, we can safely
            // ignore the call terminated message for the old session since we closed it already.
            mSessionEndDuringMerge = false;
            mSessionEndDuringMergeReasonInfo = null;
            mUpdateRequest = UPDATE_NONE;
        }
        if (listener != null) {
            try {
                listener.onCallMerged(ImsCall.this);
            } catch (Throwable t) {
                loge("processMergeComplete :: ", t);
            }
            synchronized(mLockObj) {
                if (mConferenceParticipants != null && !mConferenceParticipants.isEmpty()
                        && listener != null) {
                    try {
                        listener.onConferenceParticipantsStateChanged(this,
                                mConferenceParticipants);
                    } catch (Throwable t) {
                        loge("processMergeComplete :: ", t);
                    }
                }
            }
        }

        return;
    }

    /**
     * We received a callback from ImsCallSession that a merge failed. Clean up all
     * internal state to represent this state change.
     *
     * @param reasonInfo The {@link ImsReasonInfo} why the merge failed.
     */
    private void processMergeFailed(ImsReasonInfo reasonInfo) {
        if (DBG) {
            String sessionString = mSession != null ? mSession.toString() : "null";
            String transientSessionString = mTransientConferenceSession != null ?
                    mTransientConferenceSession.toString() : "null";
            String reasonString = reasonInfo != null ? reasonInfo.toString() : "null";
            log("processMergeFailed :: session=" + sessionString + " transientSession=" +
                    transientSessionString + " reason=" + reasonString);
        }

        ImsCall.Listener listener;
        boolean notifyFailure = false;
        ImsReasonInfo notifyFailureReasonInfo = null;

        synchronized(ImsCall.this) {
            listener = mListener;
            if (mTransientConferenceSession != null) {
                // Clean up any work that we performed on the transient session.
                mTransientConferenceSession.setListener(null);
                mTransientConferenceSession = null;
                listener = mListener;
                if (mSessionEndDuringMerge) {
                    // Set some local variables that will send out a notification about a
                    // previously buried termination callback for our primary session now that
                    // we know that this is not due to the conference call merging succesfully.
                    if (DBG) {
                        log("processMergeFailed :: following up on a terminate during the merge");
                    }
                    notifyFailure = true;
                    notifyFailureReasonInfo = mSessionEndDuringMergeReasonInfo;
                }
            } else {
                // This is an interesting state that needs to be logged since we
                // should only be going through this workflow for new conference calls
                // and not merges into existing conferences (which a null transient
                // session would imply)
                log("processMergeFailed - ERROR no transient session");
            }
            mSessionEndDuringMerge = false;
            mSessionEndDuringMergeReasonInfo = null;
            mUpdateRequest = UPDATE_NONE;
            setIsMerged(false);
        }
        if (listener != null) {
            try {
                // TODO: are both of these callbacks necessary?
                listener.onCallMergeFailed(ImsCall.this, reasonInfo);
                if (notifyFailure) {
                    processCallTerminated(notifyFailureReasonInfo);
                }
            } catch (Throwable t) {
                loge("processMergeFailed :: ", t);
            }
        }
        return;
    }

    private void notifyError(int reason, int statusCode, String message) {
    }

    private void throwImsException(Throwable t, int code) throws ImsException {
        if (t instanceof ImsException) {
            throw (ImsException) t;
        } else {
            throw new ImsException(String.valueOf(code), t, code);
        }
    }

    private void updateConferenceParticipantsList(ImsCall bgCall) {
        if (bgCall == null) return;
        ImsCall confCall = this;
        ImsCall childCall = bgCall;
        if (bgCall.isMultiparty()) {
            // BG call is a conference, so add this call to it's participants list
            log("updateConferenceParticipantsList: BG call is conference");
            confCall = bgCall;
            childCall = this;
        } else if (!this.isMultiparty()) {
            // Both are single calls. Treat this call as conference call and
            // add itself as first participant.
            log("updateConferenceParticipantsList: Make this call as conference and add child");
            addToConferenceParticipantList(this);
        }
        confCall.addToConferenceParticipantList(childCall);
    }

    private void addToConferenceParticipantList(ImsCall childCall) {
        if (childCall == null) return;

        ImsCallProfile profile = childCall.getCallProfile();
        if (profile == null) {
            loge("addToConferenceParticipantList: null profile for childcall");
            return;
        }
        String handle = profile.getCallExtra(ImsCallProfile.EXTRA_OI, null);
        String name = profile.getCallExtra(ImsCallProfile.EXTRA_CNA, "");
        if (handle == null) {
            loge("addToConferenceParticipantList: Invalid number for childcall");
            return;
        }
        Uri userUri = Uri.parse(handle);
        ConferenceParticipant participant = new ConferenceParticipant(userUri,
                name, userUri, Connection.STATE_ACTIVE);
        synchronized(mLockObj) {
            if (mConferenceParticipants == null) {
                mConferenceParticipants = new ArrayList<ConferenceParticipant>();
            }
            if (DBG) log("Adding participant: " + participant + " to list");
            mConferenceParticipants.add(participant);
            if (isMultiparty() && !mIsCEPPresent && !mConferenceParticipants.isEmpty()
                && mListener != null) {
                try {
                    mListener.onConferenceParticipantsStateChanged(this,
                        mConferenceParticipants);
                } catch (Throwable t) {
                    loge("notifyConferenceStateUpdated :: ", t);
                }
            }
        }
    }

    private void log(String s) {
        Rlog.d(TAG, s);
    }

    private void loge(String s) {
        Rlog.e(TAG, s);
    }

    private void loge(String s, Throwable t) {
        Rlog.e(TAG, s, t);
    }

    private class ImsCallSessionListenerProxy extends ImsCallSession.Listener {
        @Override
        public void callSessionProgressing(ImsCallSession session, ImsStreamMediaProfile profile) {
            if (isTransientConferenceSession(session)) {
                log("callSessionProgressing :: not supported for transient conference session=" +
                        session);
                return;
            }

            if (DBG) {
                log("callSessionProgressing :: session=" + session + ", profile=" + profile);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mCallProfile.mMediaProfile.copyFrom(profile);
            }

            if (listener != null) {
                try {
                    listener.onCallProgressing(ImsCall.this);
                } catch (Throwable t) {
                    loge("callSessionProgressing :: ", t);
                }
            }
        }

        @Override
        public void callSessionStarted(ImsCallSession session, ImsCallProfile profile) {
            if (DBG) {
                log("callSessionStarted :: session=" + session + ", profile=" + profile);
            }

            if (isTransientConferenceSession(session)) {
                log("callSessionStarted :: transient conference session resumed session=" +
                        session);
                return;
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mCallProfile = profile;
            }

            if (listener != null) {
                try {
                    listener.onCallStarted(ImsCall.this);
                } catch (Throwable t) {
                    loge("callSessionStarted :: ", t);
                }
            }
        }

        @Override
        public void callSessionStartFailed(ImsCallSession session, ImsReasonInfo reasonInfo) {
            if (isTransientConferenceSession(session)) {
                log("callSessionStartFailed :: not supported for transient conference session=" +
                        session);
                return;
            }

            if (DBG) {
                log("callSessionStartFailed :: session=" + session +
                        ", reasonInfo=" + reasonInfo);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mLastReasonInfo = reasonInfo;
            }

            if (listener != null) {
                try {
                    listener.onCallStartFailed(ImsCall.this, reasonInfo);
                } catch (Throwable t) {
                    loge("callSessionStarted :: ", t);
                }
            }
        }

        @Override
        public void callSessionTerminated(ImsCallSession session, ImsReasonInfo reasonInfo) {
            if (mSession != session) {
                if (isTransientConferenceSession(session)) {
                    processMergeFailed(reasonInfo);
                    log("callSessionTerminated :: for transient session");
                }
                return;
            }

            if (DBG) {
                log("callSessionTerminated :: session=" + session + ", reasonInfo=" + reasonInfo);
            }

            processCallTerminated(reasonInfo);
        }

        @Override
        public void callSessionHeld(ImsCallSession session, ImsCallProfile profile) {
            if (isTransientConferenceSession(session)) {
                log("callSessionHeld :: not supported for transient conference session=" + session);
                return;
            }

            if (DBG) {
                log("callSessionHeld :: session=" + session + ", profile=" + profile);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                mCallProfile = profile;

                if (mUpdateRequest == UPDATE_HOLD_MERGE) {
                    mergeInternal();
                    return;
                }

                mUpdateRequest = UPDATE_NONE;
                listener = mListener;
            }

            if (listener != null) {
                try {
                    listener.onCallHeld(ImsCall.this);
                } catch (Throwable t) {
                    loge("callSessionHeld :: ", t);
                }
            }

            if (mCallGroup != null) {
                notifyConferenceStateUpdatedThroughGroupOwner(UPDATE_HOLD);
            }
        }

        @Override
        public void callSessionHoldFailed(ImsCallSession session, ImsReasonInfo reasonInfo) {
            if (isTransientConferenceSession(session)) {
                log("callSessionHoldFailed :: not supported for transient conference session=" +
                        session);
                return;
            }

            if (DBG) {
                log("callSessionHoldFailed :: session=" + session +
                        ", reasonInfo=" + reasonInfo);
            }

            synchronized (mLockObj) {
                mHold = false;
            }

            boolean isHoldForMerge = false;
            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                if (mUpdateRequest == UPDATE_HOLD_MERGE) {
                    isHoldForMerge = true;
                }

                mUpdateRequest = UPDATE_NONE;
                listener = mListener;
            }

            if (isHoldForMerge) {
                // Is hold for merge implemented/supported? If so we need to take a close look
                // at this workflow to make sure that we handle the case where
                // callSessionMergeFailed() does the right thing because we have not actually
                // started the merge yet.
                callSessionMergeFailed(session, reasonInfo);
                return;
            }

            if (listener != null) {
                try {
                    listener.onCallHoldFailed(ImsCall.this, reasonInfo);
                } catch (Throwable t) {
                    loge("callSessionHoldFailed :: ", t);
                }
            }
        }

        @Override
        public void callSessionHoldReceived(ImsCallSession session, ImsCallProfile profile) {
            if (isTransientConferenceSession(session)) {
                log("callSessionHoldReceived :: not supported for transient conference session=" +
                        session);
                return;
            }

            if (DBG) {
                log("callSessionHoldReceived :: session=" + session + ", profile=" + profile);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mCallProfile = profile;
            }

            if (listener != null) {
                try {
                    listener.onCallHoldReceived(ImsCall.this);
                } catch (Throwable t) {
                    loge("callSessionHoldReceived :: ", t);
                }
            }
        }

        @Override
        public void callSessionResumed(ImsCallSession session, ImsCallProfile profile) {
            if (isTransientConferenceSession(session)) {
                log("callSessionResumed :: not supported for transient conference session=" +
                        session);
                return;
            }

            if (DBG) {
                log("callSessionResumed :: session=" + session + ", profile=" + profile);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mCallProfile = profile;
                mUpdateRequest = UPDATE_NONE;
                mHold = false;
            }

            if (listener != null) {
                try {
                    listener.onCallResumed(ImsCall.this);
                } catch (Throwable t) {
                    loge("callSessionResumed :: ", t);
                }
            }

            if (mCallGroup != null) {
                notifyConferenceStateUpdatedThroughGroupOwner(UPDATE_RESUME);
            }
        }

        @Override
        public void callSessionResumeFailed(ImsCallSession session, ImsReasonInfo reasonInfo) {
            if (isTransientConferenceSession(session)) {
                log("callSessionResumeFailed :: not supported for transient conference session=" +
                        session);
                return;
            }

            if (DBG) {
                log("callSessionResumeFailed :: session=" + session +
                        ", reasonInfo=" + reasonInfo);
            }

            synchronized (mLockObj) {
                mHold = true;
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mUpdateRequest = UPDATE_NONE;
            }

            if (listener != null) {
                try {
                    listener.onCallResumeFailed(ImsCall.this, reasonInfo);
                } catch (Throwable t) {
                    loge("callSessionResumeFailed :: ", t);
                }
            }
        }

        @Override
        public void callSessionResumeReceived(ImsCallSession session, ImsCallProfile profile) {
            if (isTransientConferenceSession(session)) {
                log("callSessionResumeReceived :: not supported for transient conference session=" +
                        session);
                return;
            }

            if (DBG) {
                log("callSessionResumeReceived :: session=" + session +
                        ", profile=" + profile);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mCallProfile = profile;
            }

            if (listener != null) {
                try {
                    listener.onCallResumeReceived(ImsCall.this);
                } catch (Throwable t) {
                    loge("callSessionResumeReceived :: ", t);
                }
            }
        }

        @Override
        public void callSessionMergeStarted(ImsCallSession session,
                ImsCallSession newSession, ImsCallProfile profile) {
            if (DBG) {
                String sessionString = session == null ? "null" : session.toString();
                String newSessionString = newSession == null ? "null" : newSession.toString();
                log("callSessionMergeStarted :: session=" + sessionString
                        + ", newSession=" + newSessionString + ", profile=" + profile);
            }

            if (mUpdateRequest != UPDATE_MERGE) {
                // Odd, we are not in the midst of merging anything.
                if (DBG) {
                    log("callSessionMergeStarted :: no merge in progress.");
                }
                return;
            }

            // There are 2 ways that we can go here.  If the session that supplied the params
            // is not null, then it is the new session that represents the new conference
            // if the merge succeeds. If it is null, the merge is happening on our current
            // ImsCallSession.
            if (session == null) {
                // Everything is already set up and we just need to make sure
                // that we properly respond to all the future callbacks about
                // this merge.
                if (DBG) {
                    log("callSessionMergeStarted :: merging into existing ImsCallSession");
                }
                return;
            }

            if (DBG) {
                log("callSessionMergeStarted ::  setting our transient ImsCallSession");
            }

            // If we are here, this means that we are creating a new conference and
            // we need to do some extra work around managing a new ImsCallSession that
            // could represent our new ImsCallSession if the merge succeeds.
            synchronized(ImsCall.this) {
                // Keep track of this session for future callbacks to indicate success
                // or failure of this merge.
                mTransientConferenceSession = newSession;
                mTransientConferenceSession.setListener(createCallSessionListener());
            }

            return;
        }

        @Override
        public void callSessionMergeComplete(ImsCallSession session) {
            if (DBG) {
                String sessionString = session == null ? "null" : session.toString();
                log("callSessionMergeComplete :: session=" + sessionString);
            }
            if (mUpdateRequest != UPDATE_MERGE) {
                // Odd, we are not in the midst of merging anything.
                if (DBG) {
                    log("callSessionMergeComplete :: no merge in progress.");
                }
                return;
            }
            // Let's let our parent ImsCall now that we received notification that
            // the merge was completed so we can set up our internal state properly
            processMergeComplete();
        }

        @Override
        public void callSessionMergeFailed(ImsCallSession session, ImsReasonInfo reasonInfo) {
            if (DBG) {
                String sessionString = session == null? "null" : session.toString();
                String reasonInfoString = reasonInfo == null ? "null" : reasonInfo.toString();
                log("callSessionMergeFailed :: session=" + sessionString +
                        ", reasonInfo=" + reasonInfoString);
            }

            ImsCall neutralReferrer = (ImsCall) mCallGroup.getNeutralReferrer();
            if (neutralReferrer != null) {
                mCallGroup.removeReferrer(neutralReferrer);
                neutralReferrer.mCallGroup = null;
            }
            destroyCallGroup();

            // Let's tell our parent ImsCall that the merge has failed and we need to clean
            // up any temporary, transient state.
            processMergeFailed(reasonInfo);
        }

        @Override
        public void callSessionUpdated(ImsCallSession session, ImsCallProfile profile) {
            if (isTransientConferenceSession(session)) {
                log("callSessionUpdated :: not supported for transient conference session=" +
                        session);
                return;
            }

            if (DBG) {
                log("callSessionUpdated :: session=" + session + ", profile=" + profile);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mCallProfile = profile;
            }

            if (listener != null) {
                try {
                    listener.onCallUpdated(ImsCall.this);
                } catch (Throwable t) {
                    loge("callSessionUpdated :: ", t);
                }
            }
        }

        @Override
        public void callSessionUpdateFailed(ImsCallSession session, ImsReasonInfo reasonInfo) {
            if (isTransientConferenceSession(session)) {
                log("callSessionUpdateFailed :: not supported for transient conference session=" +
                        session);
                return;
            }

            if (DBG) {
                log("callSessionUpdateFailed :: session=" + session +
                        ", reasonInfo=" + reasonInfo);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mUpdateRequest = UPDATE_NONE;
            }

            if (listener != null) {
                try {
                    listener.onCallUpdateFailed(ImsCall.this, reasonInfo);
                } catch (Throwable t) {
                    loge("callSessionUpdateFailed :: ", t);
                }
            }
        }

        @Override
        public void callSessionUpdateReceived(ImsCallSession session, ImsCallProfile profile) {
            if (isTransientConferenceSession(session)) {
                log("callSessionUpdateReceived :: not supported for transient conference " +
                        "session=" + session);
                return;
            }

            if (DBG) {
                log("callSessionUpdateReceived :: session=" + session +
                        ", profile=" + profile);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mProposedCallProfile = profile;
                mUpdateRequest = UPDATE_UNSPECIFIED;
            }

            if (listener != null) {
                try {
                    listener.onCallUpdateReceived(ImsCall.this);
                } catch (Throwable t) {
                    loge("callSessionUpdateReceived :: ", t);
                }
            }
        }

        @Override
        public void callSessionConferenceExtended(ImsCallSession session, ImsCallSession newSession,
                ImsCallProfile profile) {
            if (isTransientConferenceSession(session)) {
                log("callSessionConferenceExtended :: not supported for transient conference " +
                        "session=" + session);
                return;
            }

            if (DBG) {
                log("callSessionConferenceExtended :: session=" + session
                        + ", newSession=" + newSession + ", profile=" + profile);
            }

            ImsCall newCall = createNewCall(newSession, profile);

            if (newCall == null) {
                callSessionConferenceExtendFailed(session, new ImsReasonInfo());
                return;
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mUpdateRequest = UPDATE_NONE;
            }

            if (listener != null) {
                try {
                    listener.onCallConferenceExtended(ImsCall.this, newCall);
                } catch (Throwable t) {
                    loge("callSessionConferenceExtended :: ", t);
                }
            }
        }

        @Override
        public void callSessionConferenceExtendFailed(ImsCallSession session,
                ImsReasonInfo reasonInfo) {
            if (isTransientConferenceSession(session)) {
                log("callSessionConferenceExtendFailed :: not supported for transient " +
                        "conference session=" + session);
                return;
            }

            if (DBG) {
                log("callSessionConferenceExtendFailed :: session=" + session +
                        ", reasonInfo=" + reasonInfo);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
                mUpdateRequest = UPDATE_NONE;
            }

            if (listener != null) {
                try {
                    listener.onCallConferenceExtendFailed(ImsCall.this, reasonInfo);
                } catch (Throwable t) {
                    loge("callSessionConferenceExtendFailed :: ", t);
                }
            }
        }

        @Override
        public void callSessionConferenceExtendReceived(ImsCallSession session,
                ImsCallSession newSession, ImsCallProfile profile) {
            if (isTransientConferenceSession(session)) {
                log("callSessionConferenceExtendReceived :: not supported for transient " +
                        "conference session=" + session);
                return;
            }

            if (DBG) {
                log("callSessionConferenceExtendReceived :: session=" + session
                        + ", newSession=" + newSession + ", profile=" + profile);
            }

            ImsCall newCall = createNewCall(newSession, profile);

            if (newCall == null) {
                // Should all the calls be terminated...???
                return;
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
            }

            if (listener != null) {
                try {
                    listener.onCallConferenceExtendReceived(ImsCall.this, newCall);
                } catch (Throwable t) {
                    loge("callSessionConferenceExtendReceived :: ", t);
                }
            }
        }

        @Override
        public void callSessionInviteParticipantsRequestDelivered(ImsCallSession session) {
            if (isTransientConferenceSession(session)) {
                log("callSessionInviteParticipantsRequestDelivered :: not supported for " +
                        "conference session=" + session);
                return;
            }

            if (DBG) {
                log("callSessionInviteParticipantsRequestDelivered :: session=" + session);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
            }

            if (listener != null) {
                try {
                    listener.onCallInviteParticipantsRequestDelivered(ImsCall.this);
                } catch (Throwable t) {
                    loge("callSessionInviteParticipantsRequestDelivered :: ", t);
                }
            }
        }

        @Override
        public void callSessionInviteParticipantsRequestFailed(ImsCallSession session,
                ImsReasonInfo reasonInfo) {
            if (isTransientConferenceSession(session)) {
                log("callSessionInviteParticipantsRequestFailed :: not supported for " +
                        "conference session=" + session);
                return;
            }

            if (DBG) {
                log("callSessionInviteParticipantsRequestFailed :: session=" + session
                        + ", reasonInfo=" + reasonInfo);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
            }

            if (listener != null) {
                try {
                    listener.onCallInviteParticipantsRequestFailed(ImsCall.this, reasonInfo);
                } catch (Throwable t) {
                    loge("callSessionInviteParticipantsRequestFailed :: ", t);
                }
            }
        }

        @Override
        public void callSessionRemoveParticipantsRequestDelivered(ImsCallSession session) {
            if (isTransientConferenceSession(session)) {
                log("callSessionRemoveParticipantsRequestDelivered :: not supported for " +
                        "conference session=" + session);
                return;
            }

            if (DBG) {
                log("callSessionRemoveParticipantsRequestDelivered :: session=" + session);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
            }

            if (listener != null) {
                try {
                    listener.onCallRemoveParticipantsRequestDelivered(ImsCall.this);
                } catch (Throwable t) {
                    loge("callSessionRemoveParticipantsRequestDelivered :: ", t);
                }
            }
        }

        @Override
        public void callSessionRemoveParticipantsRequestFailed(ImsCallSession session,
                ImsReasonInfo reasonInfo) {
            if (isTransientConferenceSession(session)) {
                log("callSessionRemoveParticipantsRequestFailed :: not supported for " +
                        "conference session=" +session);
                return;
            }

            if (DBG) {
                log("callSessionRemoveParticipantsRequestFailed :: session=" + session
                        + ", reasonInfo=" + reasonInfo);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
            }

            if (listener != null) {
                try {
                    listener.onCallRemoveParticipantsRequestFailed(ImsCall.this, reasonInfo);
                } catch (Throwable t) {
                    loge("callSessionRemoveParticipantsRequestFailed :: ", t);
                }
            }
        }

        @Override
        public void callSessionConferenceStateUpdated(ImsCallSession session,
                ImsConferenceState state) {
            if (DBG) {
                log("callSessionConferenceStateUpdated :: session=" + session
                        + ", state=" + state);
            }

            conferenceStateUpdated(state);
        }

        @Override
        public void callSessionUssdMessageReceived(ImsCallSession session, int mode,
                String ussdMessage) {
            if (isTransientConferenceSession(session)) {
                log("callSessionUssdMessageReceived :: not supported for transient " +
                        "conference session=" + session);
                return;
            }

            if (DBG) {
                log("callSessionUssdMessageReceived :: session=" + session
                        + ", mode=" + mode + ", ussdMessage=" + ussdMessage);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
            }

            if (listener != null) {
                try {
                    listener.onCallUssdMessageReceived(ImsCall.this, mode, ussdMessage);
                } catch (Throwable t) {
                    loge("callSessionUssdMessageReceived :: ", t);
                }
            }
        }

        @Override
        public void callSessionSuppServiceReceived(ImsCallSession session,
                ImsSuppServiceNotification suppServiceInfo ) {
            if (isTransientConferenceSession(session)) {
                log("callSessionSuppServiceReceived :: not supported for transient conference"
                        + " session=" + session);
                return;
            }

            if (DBG) {
                log("callSessionSuppServiceReceived :: session=" + session +
                         ", suppServiceInfo" + suppServiceInfo);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
            }

            if (listener != null) {
                try {
                    listener.onCallSuppServiceReceived(ImsCall.this, suppServiceInfo);
                } catch (Throwable t) {
                    loge("callSessionSuppServiceReceived :: ", t);
                }
            }
        }

        public void callSessionHandover(ImsCallSession session, int srcAccessTech,
            int targetAccessTech, ImsReasonInfo reasonInfo) {
            if (DBG) {
                log("callSessionHandover :: session=" + session + ", srcAccessTech=" +
                    srcAccessTech + ", targetAccessTech=" + targetAccessTech + ", reasonInfo=" +
                    reasonInfo);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
            }

            if (listener != null) {
                try {
                    listener.onCallHandover(ImsCall.this, srcAccessTech, targetAccessTech,
                        reasonInfo);
                } catch (Throwable t) {
                    loge("callSessionHandover :: ", t);
                }
            }
        }

        @Override
        public void callSessionHandoverFailed(ImsCallSession session, int srcAccessTech,
            int targetAccessTech, ImsReasonInfo reasonInfo) {
            if (DBG) {
                log("callSessionHandoverFailed :: session=" + session + ", srcAccessTech=" +
                    srcAccessTech + ", targetAccessTech=" + targetAccessTech + ", reasonInfo=" +
                    reasonInfo);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
            }

            if (listener != null) {
                try {
                    listener.onCallHandoverFailed(ImsCall.this, srcAccessTech, targetAccessTech,
                        reasonInfo);
                } catch (Throwable t) {
                    loge("callSessionHandoverFailed :: ", t);
                }
            }
        }

        @Override
        public void callSessionTtyModeReceived(ImsCallSession session, int mode) {
            if (DBG) {
                log("callSessionTtyModeReceived :: session=" + session
                        + ", mode=" + mode);
            }

            ImsCall.Listener listener;

            synchronized(ImsCall.this) {
                listener = mListener;
            }

            if (listener != null) {
                try {
                    listener.onCallSessionTtyModeReceived(ImsCall.this, mode);
                } catch (Throwable t) {
                    loge("callSessionTtyModeReceived :: ", t);
                }
            }
        }
    }

    /**
     * Report a new conference state to the current {@link ImsCall} and inform listeners of the
     * change.  Marked as {@code VisibleForTesting} so that the
     * {@code com.android.internal.telephony.TelephonyTester} class can inject a test conference
     * event package into a regular ongoing IMS call.
     *
     * @param state The {@link ImsConferenceState}.
     */
    @VisibleForTesting
    public void conferenceStateUpdated(ImsConferenceState state) {
        Listener listener;

        synchronized(this) {
            notifyConferenceStateUpdated(state);
            listener = mListener;
        }

        if (listener != null) {
            try {
                listener.onCallConferenceStateUpdated(this, state);
            } catch (Throwable t) {
                loge("callSessionConferenceStateUpdated :: ", t);
            }
        }
    }

    /**
     * Provides a human-readable string representation of an update request.
     *
     * @param updateRequest The update request.
     * @return The string representation.
     */
    private String updateRequestToString(int updateRequest) {
        switch (updateRequest) {
            case UPDATE_NONE:
                return "NONE";
            case UPDATE_HOLD:
                return "HOLD";
            case UPDATE_HOLD_MERGE:
                return "HOLD_MERGE";
            case UPDATE_RESUME:
                return "RESUME";
            case UPDATE_MERGE:
                return "MERGE";
            case UPDATE_EXTEND_TO_CONFERENCE:
                return "EXTEND_TO_CONFERENCE";
            case UPDATE_UNSPECIFIED:
                return "UNSPECIFIED";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * Provides a string representation of the {@link ImsCall}.  Primarily intended for use in log
     * statements.
     *
     * @return String representation of call.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[ImsCall objId:");
        sb.append(System.identityHashCode(this));
        sb.append(" multiParty:");
        sb.append(isMultiparty()?"Y":"N");
        sb.append(" session:");
        sb.append(mSession);
        sb.append(" updateRequest:");
        sb.append(updateRequestToString(mUpdateRequest));
        sb.append(" transientSession:");
        sb.append(mTransientConferenceSession);
        sb.append("]");
        return sb.toString();
    }
}
