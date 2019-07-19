package com.oppwa;

import android.app.Activity;
import android.content.Context;

import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.IBinder;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.oppwa.mobile.connect.checkout.dialog.CheckoutActivity;
import com.oppwa.mobile.connect.checkout.meta.CheckoutSecurityPolicyMode;
import com.oppwa.mobile.connect.checkout.meta.CheckoutSettings;
import com.oppwa.mobile.connect.checkout.meta.CheckoutSkipCVVMode;
import com.oppwa.mobile.connect.checkout.meta.CheckoutStorePaymentDetailsMode;
import com.oppwa.mobile.connect.exception.PaymentError;
import com.oppwa.mobile.connect.exception.PaymentException;
import com.oppwa.mobile.connect.payment.BrandsValidation;
import com.oppwa.mobile.connect.payment.CheckoutInfo;
import com.oppwa.mobile.connect.payment.ImagesRequest;
import com.oppwa.mobile.connect.payment.PaymentParams;
import com.oppwa.mobile.connect.payment.card.CardPaymentParams;
import com.oppwa.mobile.connect.payment.token.Token;
import com.oppwa.mobile.connect.payment.token.TokenPaymentParams;
import com.oppwa.mobile.connect.provider.Connect;
import com.oppwa.mobile.connect.provider.ITransactionListener;
import com.oppwa.mobile.connect.provider.Transaction;
import com.oppwa.mobile.connect.provider.TransactionType;
import com.oppwa.mobile.connect.service.IProviderBinder;
import com.oppwa.mobile.connect.service.ConnectService;

import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedHashSet;
import java.util.Set;

public class RNOppwaModule extends ReactContextBaseJavaModule implements ITransactionListener, PaymentStatusRequestListener {
  private final static String TAG = RNOppwaModule.class.getCanonicalName();
  private IProviderBinder binder;
  private Context mApplicationContext;
  private Promise promiseModule;
  private static RNOppwaModule instance;

  private String shopperResultUrl = "http://127.0.0.1";
  private String checkoutId;
  private Connect.ProviderMode providerMode = Connect.ProviderMode.TEST;

  public static final Set<String> PAYMENT_BRANDS;

  static {
    PAYMENT_BRANDS = new LinkedHashSet<>();

    PAYMENT_BRANDS.add("VISA");
    PAYMENT_BRANDS.add("MASTER");
  }

  private ServiceConnection serviceConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      binder = (IProviderBinder) service;
      /* we have a connection to the service */
      try {
        binder.initializeProvider(Connect.ProviderMode.TEST);
      } catch (PaymentException ee) {
        /* error occurred */
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      binder = null;
    }
  };

  private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {
    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
      if (requestCode == CheckoutActivity.REQUEST_CODE_CHECKOUT) {
        switch (resultCode) {
          case CheckoutActivity.RESULT_OK:
            /* Transaction completed. */
            Transaction transaction = intent.getParcelableExtra(
                    CheckoutActivity.CHECKOUT_RESULT_TRANSACTION);
            String resourcePath = intent.getStringExtra(
                    CheckoutActivity.CHECKOUT_RESULT_RESOURCE_PATH);
            /* Check the transaction type. */
            if (transaction.getTransactionType() == TransactionType.SYNC) {
              /* Check the status of synchronous transaction. */
              Log.e("RNOppwaModule" , "requestPaymentStatus " + resourcePath);
              if(promiseModule != null){
                WritableMap data = Arguments.createMap();
                data.putBoolean("success", true);
                data.putString("checkoutId", checkoutId);
                data.putString("resourcePath", resourcePath);
                promiseModule.resolve(data);
              }
              //new PaymentStatusRequestAsyncTask(customServer, accessToken, entityId,instance).execute(resourcePath);
            } else if (transaction.getTransactionType() == TransactionType.ASYNC) {
              /* Asynchronous transaction is processed in the onNewIntent(). */
              Log.e("RNOppwaModule" , "Asynchronous transaction is processed in the onNewIntent()");
            } else {
              if(promiseModule != null){
                WritableMap data = Arguments.createMap();
                data.putBoolean("success", false);
                data.putString("error", "Invalid transaction");
                promiseModule.resolve(data);
              }
            }

            break;
          case CheckoutActivity.RESULT_CANCELED:
            Log.e("RNOppwaModule" , "CheckoutActivity.RESULT_CANCELED");
            if(promiseModule != null){
              WritableMap data = Arguments.createMap();
              data.putBoolean("success", false);
              data.putBoolean("cancel", true);
              data.putString("mesage", "Cancel checkout");
              promiseModule.resolve(data);
            }
            break;
          case CheckoutActivity.RESULT_ERROR:
            PaymentError error = intent.getParcelableExtra(
                    CheckoutActivity.CHECKOUT_RESULT_ERROR);
            Log.e("RNOppwaModule" , "RESULT_ERROR: " + error.getErrorInfo());
            error.getErrorMessage();
            if(promiseModule != null){

              WritableMap data = Arguments.createMap();
              data.putBoolean("success", false);
              data.putString("error", error.getErrorMessage());
              promiseModule.resolve(data);
            }
        }
      }
    }
  };

  public RNOppwaModule(ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext.addActivityEventListener(mActivityEventListener);
    instance = this;
    mApplicationContext = reactContext.getApplicationContext();
    Intent intent = new Intent(mApplicationContext, ConnectService.class);

    mApplicationContext.startService(intent);
    mApplicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);


  }

  public void unBindService() {
    if (serviceConnection != null) {
      mApplicationContext.unbindService(serviceConnection);
    }
  }

  @Override
  public String getName() {
    return "RNOppwa";
  }

  @ReactMethod
  public void isValidNumber(ReadableMap options, Promise promise) {
    if (!CardPaymentParams.isNumberValid(options.getString("cardNumber"), options.getString("paymentBrand"))) {
      promise.reject("oppwa/card-invalid", "The card number is invalid.");
    } else {
      promise.resolve(null);
    }


  }

  @ReactMethod
  public void setProviderMode(String mode){
    try{
      if(mode.equals("TEST")){
        this.providerMode = Connect.ProviderMode.TEST;
        binder.initializeProvider(Connect.ProviderMode.TEST);
      } else {
        this.providerMode = Connect.ProviderMode.LIVE;
        binder.initializeProvider(Connect.ProviderMode.LIVE);
      }
    } catch (Exception e){
      e.printStackTrace();
    }
  }

  @ReactMethod
  public void transactionPayment(ReadableMap options, Promise promise) {
    try {
      CardPaymentParams cardPaymentParams = new CardPaymentParams(options.getString("checkoutID"),
          options.getString("paymentBrand"), options.getString("cardNumber"), options.getString("holderName"),
          options.getString("expiryMonth"), options.getString("expiryYear"), options.getString("cvv"));
      cardPaymentParams.setShopperResultUrl("companyname://callback");
      cardPaymentParams.setTokenizationEnabled(true);
      Transaction transaction = null;
      try {
        transaction = new Transaction(cardPaymentParams);
        binder.initializeProvider(this.providerMode);
        binder.submitTransaction(transaction);
        binder.addTransactionListener(RNOppwaModule.this);
        WritableMap data = Arguments.createMap();
        data.putBoolean("success", true);
        promise.resolve(data);
      } catch (PaymentException ee) {
        Log.e("PaymentException 126", ee.getMessage());
        promise.reject(null, ee.getMessage());
      }
    } catch (PaymentException e) {
      Log.e("PaymentException 126", e.getMessage());
      promise.reject(null, e.getMessage());
    }

  }

  @Override
  public void paymentConfigRequestSucceeded(CheckoutInfo checkoutInfo) {
    Log.e("payment-hyperpsy", "getTokens " + checkoutInfo.getTokens());
    Log.e("payment-hyperpsy", "RequestSucceeded " + checkoutInfo.getCurrencyCode());


  }

  @Override
  public void paymentConfigRequestFailed(PaymentError paymentError) {

    Log.e("payment-hyperpsy", "RequestFailed " + paymentError.getErrorInfo() + " : " + paymentError.getErrorMessage());
  }

  @Override
  public void transactionCompleted(Transaction transaction) {
    Log.e("payment-hyperpsy",
            "transactionCompleted ");
  }

  @Override
  public void transactionFailed(Transaction transaction, PaymentError paymentError) {
    WritableMap data = Arguments.createMap();
    data.putString("status", "transactionFailed");
    data.putString("checkoutID", transaction.getPaymentParams().getCheckoutId());

    getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit("transactionStatus", data);
    Log.e("payment-hyperpsy",
        "transactionFailed " + paymentError.getErrorMessage() + " : " + paymentError.getErrorInfo());
  }

  @Override
  public void brandsValidationRequestSucceeded(BrandsValidation var1){}

  @Override
  public void brandsValidationRequestFailed(PaymentError var1){}

  @Override
  public void imagesRequestSucceeded(ImagesRequest var1){}

  @Override
  public void imagesRequestFailed(){}

  @ReactMethod
  public void openCheckoutUI(ReadableMap options, Promise promise) {
    try{
      promiseModule = promise;
      if(!options.hasKey("checkoutID") || options.getString("checkoutID") == null || options.getString("checkoutID").length() == 0){
        if(promiseModule != null){
          WritableMap data = Arguments.createMap();
          data.putBoolean("success", false);
          data.putString("error", "The checkoutID is invalid.");
          promiseModule.resolve(data);
        }
        return;
      }
      if(!options.hasKey("shopperResultUrl") || options.getString("shopperResultUrl") == null || options.getString("shopperResultUrl").length() == 0){
        if(promiseModule != null){
          WritableMap data = Arguments.createMap();
          data.putBoolean("success", false);
          data.putString("error", "The shopperResultUrl is invalid.");
          promiseModule.resolve(data);
        }
        return;
      } else {
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
          return;
        }
        this.checkoutId = options.getString("checkoutID");
        this.shopperResultUrl = options.getString("shopperResultUrl");
        openCheckout(checkoutId);
      }
    } catch (Exception e){
      Log.e(TAG, "openCheckoutUI: " + e.getMessage() );
    }

  }

  protected void openCheckout(String checkoutId) {
    try {
      Activity currentActivity = getCurrentActivity();
      if(currentActivity == null){
        return;
      }
      CheckoutSettings checkoutSettings = createCheckoutSettings(checkoutId,this.shopperResultUrl);
      Intent intent = new Intent(getReactApplicationContext(), CheckoutActivity.class);
      intent.putExtra(CheckoutActivity.CHECKOUT_SETTINGS, checkoutSettings);
      currentActivity.startActivityForResult(intent, CheckoutActivity.REQUEST_CODE_CHECKOUT);
    } catch (Exception e){
      Log.e(TAG,"openCheckout: " + e.getMessage());
    }

  }

  protected CheckoutSettings createCheckoutSettings(String checkoutId, String shopperResultUrl) {
    return new CheckoutSettings(checkoutId, PAYMENT_BRANDS,
            this.providerMode)
            .setSkipCVVMode(CheckoutSkipCVVMode.NEVER)
            .setStorePaymentDetailsMode(CheckoutStorePaymentDetailsMode.ALWAYS)
            .setSecurityPolicyModeForTokens(CheckoutSecurityPolicyMode.DEVICE_AUTH_REQUIRED_IF_AVAILABLE)
            .setWindowSecurityEnabled(false)
            .setShopperResultUrl(shopperResultUrl);
  }

  @Override
  public void onErrorOccurred() {
    if(promiseModule != null){
      WritableMap data = Arguments.createMap();
      data.putBoolean("success", false);
      data.putString("error", "An error occurred");
      promiseModule.resolve(data);
    }
  }

  @Override
  public void onPaymentStatusReceived(String paymentStatus) {
    if(promiseModule != null){
      try{
        JSONObject object = new JSONObject(paymentStatus);
        WritableMap data = Utils.jsonToReact(object);
        promiseModule.resolve(data);
      } catch (Exception e){
        promiseModule.resolve(paymentStatus);
      }
    }
  }

}
