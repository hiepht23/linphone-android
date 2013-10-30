package org.linphone.test;

import java.util.Timer;
import java.util.TimerTask;

import org.linphone.LinphoneException;
import org.linphone.LinphoneManager;
import org.linphone.LinphoneManager.LinphoneConfigException;
import org.linphone.LinphoneService;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.MediaEncryption;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PayloadType;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.AndroidVideoApi5JniWrapper;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.telephony.TelephonyManager;

public class LinphoneTestManager implements LinphoneCoreListener {

	private static LinphoneTestManager instance;
	private Context mIContext;
	private LinphoneCore mLc1, mLc2;
	
	public String lastMessageReceived;
	public boolean isDTMFReceived = false;
	public boolean autoAnswer = true;
	public boolean declineCall = false;

	private Timer mTimer = new Timer("Linphone scheduler");
	
	private LinphoneTestManager(Context ac, Context ic) {
		mIContext = ic;
	}
	
	public static LinphoneTestManager createAndStart(Context ac, Context ic, int id) {
		if (instance == null)
			instance = new LinphoneTestManager(ac, ic);
		
		instance.startLibLinphone(ic, id);
		TelephonyManager tm = (TelephonyManager) ac.getSystemService(Context.TELEPHONY_SERVICE);
		boolean gsmIdle = tm.getCallState() == TelephonyManager.CALL_STATE_IDLE;
		setGsmIdle(gsmIdle, id);
		
		if (Version.isVideoCapable())
			AndroidVideoApi5JniWrapper.setAndroidSdkVersion(Version.sdk());
		return instance;
	}
	
	private synchronized void startLibLinphone(Context c, int id) {
		try {
			LinphoneCoreFactory.instance().setDebugMode(true, "LinphoneTester");
			
			final LinphoneCore mLc = LinphoneCoreFactory.instance().createLinphoneCore(this);
			if (id == 2) {
				mLc2 = mLc;
			} else {
				mLc1 = mLc;
			}
			
			mLc.setContext(c);
			try {
				String versionName = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
				if (versionName == null) {
					versionName = String.valueOf(c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode);
				}
				mLc.setUserAgent("LinphoneAndroid", versionName);
			} catch (NameNotFoundException e) {
				Log.e(e, "cannot get version name");
			}

			mLc.enableIpv6(false);
			mLc.setRing(null);

			int availableCores = Runtime.getRuntime().availableProcessors();
			Log.w("MediaStreamer : " + availableCores + " cores detected and configured");
			mLc.setCpuCount(availableCores);
			
			try {
				initFromConf(mLc);
			} catch (LinphoneException e) {
				Log.w("no config ready yet");
			}
			
			TimerTask lTask = new TimerTask() {
				@Override
				public void run() {
					mLc.iterate();
				}
			};
			mTimer.scheduleAtFixedRate(lTask, 0, 20); 

			IntentFilter lFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
	        lFilter.addAction(Intent.ACTION_SCREEN_OFF);
			
	        resetCameraFromPreferences();
		}
		catch (Exception e) {
			Log.e(e, "Cannot start linphone");
		}
	}
	
	private void resetCameraFromPreferences() {
		boolean useFrontCam = true;
		int camId = 0;
		AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
		for (AndroidCamera androidCamera : cameras) {
			if (androidCamera.frontFacing == useFrontCam)
				camId = androidCamera.id;
		}
		LinphoneManager.getLc().setVideoDevice(camId);
	}
	
	public void initFromConf(LinphoneCore mLc) throws LinphoneConfigException {
		LinphoneCoreFactory.instance().setDebugMode(true, "LinphoneTester");

		mLc.setVideoPolicy(true, true);
		boolean isVideoEnabled = true;
		mLc.enableVideo(isVideoEnabled, isVideoEnabled);
		
		mLc.setUseRfc2833ForDtmfs(false);
		mLc.setUseSipInfoForDtmfs(true);
		
		mLc.setNetworkReachable(true); 
	}

	public boolean detectVideoCodec(String mime, LinphoneCore mLc) {
		for (PayloadType videoCodec : mLc.getVideoCodecs()) {
			if (mime.equals(videoCodec.getMime())) return true;
		}
		return false;
	}
	
	public boolean detectAudioCodec(String mime, LinphoneCore mLc){
		for (PayloadType audioCodec : mLc.getAudioCodecs()) {
			if (mime.equals(audioCodec.getMime())) return true;
		}
		return false;
	}

	void initMediaEncryption(LinphoneCore mLc){
		MediaEncryption me=MediaEncryption.None;
		mLc.setMediaEncryption(me);
	}
	
	public void initAccounts(LinphoneCore mLc) throws LinphoneCoreException {
		mLc.clearAuthInfos();
		mLc.clearProxyConfigs();
		
		String username, password, domain;
		if (mLc.equals(mLc1)) {
			username = mIContext.getString(org.linphone.test.R.string.account_test_calls_login);
			password = mIContext.getString(org.linphone.test.R.string.account_test_calls_pwd);
			domain = mIContext.getString(org.linphone.test.R.string.account_test_calls_domain);
		} else {
			username = mIContext.getString(org.linphone.test.R.string.conference_account_login);
			password = mIContext.getString(org.linphone.test.R.string.conference_account_password);
			domain = mIContext.getString(org.linphone.test.R.string.conference_account_domain);
		}
		LinphoneAuthInfo lAuthInfo =  LinphoneCoreFactory.instance().createAuthInfo(username, password, null);
		mLc.addAuthInfo(lAuthInfo);
		String identity = "sip:" + username +"@" + domain;
		String proxy = "sip:" + domain;
		LinphoneProxyConfig proxycon = LinphoneCoreFactory.instance().createProxyConfig(identity, proxy, null, true);
		mLc.addProxyConfig(proxycon);
		mLc.setDefaultProxyConfig(proxycon);
		
		LinphoneProxyConfig lDefaultProxyConfig = mLc.getDefaultProxyConfig();
		if (lDefaultProxyConfig != null) {
			//escape +
			lDefaultProxyConfig.setDialEscapePlus(false);
		} else if (LinphoneService.isReady()) {
			LinphoneService.instance().onRegistrationStateChanged(RegistrationState.RegistrationNone, null);
		}
	}

	public static synchronized final LinphoneTestManager getInstance() {
		return instance;
	}
	
	public static synchronized final LinphoneCore getLc(int i) {
		if (i == 2)
			return getInstance().mLc2;
		return getInstance().mLc1;
	}
	
	public static synchronized final LinphoneCore getLc() {
		return getLc(1);
	}

	private int savedMaxCallWhileGsmIncall;
	private synchronized void preventSIPCalls(LinphoneCore mLc) {
		if (savedMaxCallWhileGsmIncall != 0) {
			Log.w("SIP calls are already blocked due to GSM call running");
			return;
		}
		savedMaxCallWhileGsmIncall = mLc.getMaxCalls();
		mLc.setMaxCalls(0);
	}
	private synchronized void allowSIPCalls(LinphoneCore mLc) {
		if (savedMaxCallWhileGsmIncall == 0) {
			Log.w("SIP calls are already allowed as no GSM call knowned to be running");
			return;
		}
		mLc.setMaxCalls(savedMaxCallWhileGsmIncall);
		savedMaxCallWhileGsmIncall = 0;
	}
	public static void setGsmIdle(boolean gsmIdle, int id) {
		LinphoneTestManager mThis = instance;
		if (mThis == null) return;
		if (gsmIdle) {
			mThis.allowSIPCalls(LinphoneTestManager.getLc(id));
		} else {
			mThis.preventSIPCalls(LinphoneTestManager.getLc(id));
		}
	}

	private void doDestroy() {
		try {
			mTimer.cancel();
			mLc1.destroy();
			mLc2.destroy();
		} 
		catch (RuntimeException e) {
			e.printStackTrace();
		}
		finally {
			mLc1 = null;
			mLc2 = null;
			instance = null;
		}
	}

	public static synchronized void destroy() {
		if (instance == null) return;
		instance.doDestroy();
	}

	@Override
	public void authInfoRequested(LinphoneCore lc, String realm, String username) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void globalState(LinphoneCore lc, GlobalState state, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void callState(LinphoneCore lc, LinphoneCall call, State cstate,
			String message) {
		// TODO Auto-generated method stub
		Log.e("Call state = " + cstate.toString());
		if (cstate == LinphoneCall.State.IncomingReceived) {
			if (declineCall) {
				lc.terminateCall(call);
			} else if (autoAnswer) {
				try {
					lc.acceptCall(call);
				} catch (LinphoneCoreException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public void callStatsUpdated(LinphoneCore lc, LinphoneCall call,
			LinphoneCallStats stats) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call,
			boolean encrypted, String authenticationToken) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg,
			RegistrationState cstate, String smessage) {
		// TODO Auto-generated method stub
		Log.e("Registration state = " + cstate.toString());
	}

	@Override
	public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf,
			String url) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void textReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneAddress from, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr,
			LinphoneChatMessage message) {
		// TODO Auto-generated method stub
		Log.e("Message received = " + message.getText());
		lastMessageReceived = message.getText();
	}

	@Override
	public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {
		// TODO Auto-generated method stub
		Log.e("DTMF received = " + dtmf);
		isDTMFReceived = true;
	}

	@Override
	public void ecCalibrationStatus(LinphoneCore lc, EcCalibratorStatus status,
			int delay_ms, Object data) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneCall call,
			LinphoneAddress from, byte[] event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void show(LinphoneCore lc) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayStatus(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayMessage(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void displayWarning(LinphoneCore lc, String message) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void transferState(LinphoneCore lc, LinphoneCall call,
			State new_call_state) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void infoReceived(LinphoneCore lc, LinphoneCall call,
			LinphoneInfoMessage info) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev,
			SubscriptionState state) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void notifyReceived(LinphoneCore lc, LinphoneEvent ev,
			String eventName, LinphoneContent content) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev,
			PublishState state) {
		// TODO Auto-generated method stub
		
	}
}
