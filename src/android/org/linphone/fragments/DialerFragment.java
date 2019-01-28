package org.linphone.fragments;

/*
DialerFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.app.Fragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.linphone.LinphoneManager;
import org.linphone.LinphoneService;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.core.Core;
import org.linphone.mediastream.Log;
import org.linphone.ui.AddressAware;
import org.linphone.ui.AddressText;
import org.linphone.ui.CallButton;
import org.linphone.ui.EraseButton;

public class DialerFragment extends Fragment {
    private static DialerFragment instance;
    private static boolean isCallTransferOngoing = false;

    private AddressAware numpad;
    private AddressText mAddress;
    private CallButton mCall;
    private ImageView mAddContact;
    private OnClickListener addContactListener, cancelListener, transferListener;
    private boolean shouldEmptyAddressField = true;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialer, container, false);

        mAddress = view.findViewById(R.id.address);
        mAddress.setDialerFragment(this);

        EraseButton erase = view.findViewById(R.id.erase);
        erase.setAddressWidget(mAddress);

        mCall = view.findViewById(R.id.call);
        mCall.setAddressWidget(mAddress);
        if (LinphoneActivity.isInstanciated() && LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null && LinphoneManager.getLcIfManagerNotDestroyedOrNull().getCallsNb() > 0) {
            if (isCallTransferOngoing) {
                mCall.setImageResource(R.drawable.call_transfer);
            } else {
                mCall.setImageResource(R.drawable.call_add);
            }
        } else {
            if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null && LinphoneManager.getLcIfManagerNotDestroyedOrNull().getVideoActivationPolicy().getAutomaticallyInitiate()) {
                mCall.setImageResource(R.drawable.call_video_start);
            } else {
                mCall.setImageResource(R.drawable.call_audio_start);
            }
        }

        numpad = view.findViewById(R.id.numpad);
        if (numpad != null) {
            numpad.setAddressWidget(mAddress);
        }

        mAddContact = view.findViewById(R.id.add_contact);
        mAddContact.setEnabled(!(LinphoneActivity.isInstanciated() && LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null && LinphoneManager.getLc().getCallsNb() > 0));

        addContactListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                LinphoneActivity.instance().displayContactsForEdition(mAddress.getText().toString());
            }
        };
        cancelListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
            }
        };
        transferListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                Core lc = LinphoneManager.getLc();
                if (lc.getCurrentCall() == null) {
                    return;
                }
                lc.transferCall(lc.getCurrentCall(), mAddress.getText().toString());
                isCallTransferOngoing = false;
                LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
            }
        };

        resetLayout(isCallTransferOngoing);

        if (getArguments() != null) {
            shouldEmptyAddressField = false;
            String number = getArguments().getString("SipUri");
            String displayName = getArguments().getString("DisplayName");
            String photo = getArguments().getString("PhotoUri");
            mAddress.setText(number);
            if (displayName != null) {
                mAddress.setDisplayedName(displayName);
            }
        }

        instance = this;

        return view;
    }

    /**
     * @return null if not ready yet
     */
    public static DialerFragment instance() {
        return instance;
    }

    @Override
    public void onPause() {
        instance = null;
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        instance = this;

        if (LinphoneActivity.isInstanciated()) {
            LinphoneActivity.instance().selectMenu(FragmentsAvailable.DIALER);
            LinphoneActivity.instance().updateDialerFragment(this);
            LinphoneActivity.instance().showStatusBar();
            LinphoneActivity.instance().hideTabBar(false);
        }

        boolean isOrientationLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isOrientationLandscape && !getResources().getBoolean(R.bool.isTablet)) {
            ((LinearLayout) numpad).setVisibility(View.GONE);
        } else {
            ((LinearLayout) numpad).setVisibility(View.VISIBLE);
        }

        if (shouldEmptyAddressField) {
            mAddress.setText("");
        } else {
            shouldEmptyAddressField = true;
        }
        resetLayout(isCallTransferOngoing);

        String addressWaitingToBeCalled = LinphoneActivity.instance().mAddressWaitingToBeCalled;
        if (addressWaitingToBeCalled != null) {
            mAddress.setText(addressWaitingToBeCalled);
            if (getResources().getBoolean(R.bool.automatically_start_intercepted_outgoing_gsm_call)) {
                newOutgoingCall(addressWaitingToBeCalled);
            }
            LinphoneActivity.instance().mAddressWaitingToBeCalled = null;
        }
    }

    public void resetLayout(boolean callTransfer) {
        if (!LinphoneActivity.isInstanciated()) {
            return;
        }
        isCallTransferOngoing = LinphoneActivity.instance().isCallTransfer();
        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc == null) {
            return;
        }

        if (lc.getCallsNb() > 0) {
            if (isCallTransferOngoing) {
                mCall.setImageResource(R.drawable.call_transfer);
                mCall.setExternalClickListener(transferListener);
            } else {
                mCall.setImageResource(R.drawable.call_add);
                mCall.resetClickListener();
            }



        } else {
            if (LinphoneManager.getLc().getVideoActivationPolicy().getAutomaticallyInitiate()) {
                mCall.setImageResource(R.drawable.call_video_start);
            } else {
                mCall.setImageResource(R.drawable.call_audio_start);
            }
            mAddContact.setEnabled(false);
            mAddContact.setImageResource(R.drawable.contact_add_button);
            mAddContact.setOnClickListener(addContactListener);
            enableDisableAddContact();
        }
    }

    public void enableDisableAddContact() {
        mAddContact.setEnabled(LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null && LinphoneManager.getLc().getCallsNb() > 0 || !mAddress.getText().toString().equals(""));
    }

    public void displayTextInAddressBar(String numberOrSipAddress) {
        shouldEmptyAddressField = false;
        mAddress.setText(numberOrSipAddress);
    }

    public void newOutgoingCall(String numberOrSipAddress) {
        displayTextInAddressBar(numberOrSipAddress);
        LinphoneManager.getInstance().newOutgoingCall(mAddress);
    }

    public void newOutgoingCall(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String scheme = intent.getData().getScheme();
            if (scheme.startsWith("imto")) {
                mAddress.setText("sip:" + intent.getData().getLastPathSegment());
            } else if (scheme.startsWith("call") || scheme.startsWith("sip")) {
                mAddress.setText(intent.getData().getSchemeSpecificPart());
            } else {
                Uri contactUri = intent.getData();
                String address = ContactsManager.getAddressOrNumberForAndroidContact(LinphoneService.instance().getContentResolver(), contactUri);
                if (address != null) {
                    mAddress.setText(address);
                } else {
                    Log.e("Unknown scheme: ", scheme);
                    mAddress.setText(intent.getData().getSchemeSpecificPart());
                }
            }

            mAddress.clearDisplayedName();
            intent.setData(null);

            LinphoneManager.getInstance().newOutgoingCall(mAddress);
        }
    }
}
