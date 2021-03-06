/*
 * Copyright (c) 2016 Bambora ( http://bambora.com/ )
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.bambora.nativepayment.handlers;

import android.content.Context;
import android.content.SharedPreferences;

import com.bambora.nativepayment.interfaces.ICardRegistrationCallback;
import com.bambora.nativepayment.interfaces.ITransactionExtListener;
import com.bambora.nativepayment.interfaces.ITransactionListener;
import com.bambora.nativepayment.interfaces.VisaCheckoutDataCallback;
import com.bambora.nativepayment.interfaces.VisaCheckoutTransactionCallback;
import com.bambora.nativepayment.logging.BNLog;
import com.bambora.nativepayment.managers.CertificateManager;
import com.bambora.nativepayment.managers.CreditCardManager;
import com.bambora.nativepayment.models.PaymentSettings;
import com.bambora.nativepayment.models.PaymentType;
import com.bambora.nativepayment.models.VisaCheckoutTransactionParams;
import com.bambora.nativepayment.models.creditcard.CreditCard;
import com.bambora.nativepayment.models.creditcard.RegistrationFormSettings;
import com.bambora.nativepayment.models.creditcard.RegistrationParams;
import com.bambora.nativepayment.network.ApiService;
import com.bambora.nativepayment.network.BNHttpClient;
import com.bambora.nativepayment.network.HttpClient;
import com.bambora.nativepayment.network.Request;
import com.bambora.nativepayment.security.Crypto;
import com.bambora.nativepayment.services.PaymentApiService;
import com.bambora.nativepayment.services.PaymentApiService.PaymentService;
import com.bambora.nativepayment.services.VisaCheckoutApiService;
import com.bambora.nativepayment.services.VisaCheckoutApiService.VisaCheckoutService;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * {@link BNPaymentHandler} handles the responsibilities of managing the authentication and
 * service creation. <br><br>
 * <p/>
 * Created by oskarhenriksson on 14/10/15.
 */

public class BNPaymentHandler {

    /**
     * A variable representing the base URL of the API.
     */
    private String mBaseUrl;

    private static BNPaymentHandler ourInstance = new BNPaymentHandler();

    /**
     * A {@link BNHttpClient} used for communicating to the API.
     */
    private BNHttpClient mHttpClient;

    /**
     * An api-token to identify the Application to the API.
     */
    private String mApiToken;

    /**
     * The merchant account that identifies the Application to the API.
     */
    private String mMerchantAccount;


    public static final String sharedPreferencesKey= "Bambora_APAC_SDK";
    public static final String existingMerchantAccountKey = "BamboraMerchantAccount";
    public static final String existingBaseURLKey = "BamboraBaseURL";

    /**
     * A boolean indicating whether lib is in debug mode
     */
    private boolean mDebug;

    private CreditCardManager creditCardManager = new CreditCardManager();

    private CertificateManager certificateManager = CertificateManager.getInstance();

    /**
     * Empty constructor for {@link BNPaymentHandler}
     */
    private BNPaymentHandler() {
    }

    private JSONObject registrationJsonData;

    public JSONObject getRegistrationJsonData() {
        return registrationJsonData;
    }

    public void setRegistrationJsonData(JSONObject registrationJsonData) {
        this.registrationJsonData = registrationJsonData;
    }

    /**
     * This method is used for accessing the shared BNPaymentHandler.
     *
     * @return An instance of BNPaymentHandler
     */
    public static BNPaymentHandler getInstance() {
        return ourInstance;
    }

    /**
     * Method for setting up {@link BNPaymentHandler}. Must be called before using the handler.
     * This method is required to set context, appId and countryId.
     *
     * @param builder A builder class for {@link BNPaymentHandler}
     */
    public static void setupBNPayments(BNPaymentBuilder builder) {
            ourInstance.mBaseUrl = builder.baseUrl;
            ourInstance.mApiToken = builder.apiToken;
            ourInstance.mMerchantAccount = builder.merchantAccount;
            ourInstance.mDebug = builder.debug;
            if (ourInstance.mApiToken != null && !"".equals(ourInstance.mApiToken)) {
                ourInstance.mHttpClient = new BNHttpClient(builder.context, builder.apiToken);
            } else {
                ourInstance.mHttpClient = new BNHttpClient(builder.merchantAccount);
            }
            ourInstance.certificateManager.getEncryptionCertificates(builder.context, null);
        ourInstance.checkMerchantAndEnvironment(ourInstance.mMerchantAccount,ourInstance.mBaseUrl,builder.context);
    }

    private void checkMerchantAndEnvironment(String merchantAccount,String baseUrl,Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences(sharedPreferencesKey, 0);
        String existingMerchantAccount=sharedPreferences.getString(existingMerchantAccountKey, "");
        String existingBaseURL=sharedPreferences.getString(existingBaseURLKey, "");
        if(existingMerchantAccount.equalsIgnoreCase("") || existingBaseURL.equalsIgnoreCase(""))
        {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(existingMerchantAccountKey, merchantAccount);
            editor.putString(existingBaseURLKey, baseUrl);
            editor.apply();
        }
        else if(!existingMerchantAccount.equalsIgnoreCase(merchantAccount) || !existingBaseURL.equalsIgnoreCase(baseUrl))
        {
            ourInstance.deleteAllCards(context);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(existingMerchantAccountKey, merchantAccount);
            editor.putString(existingBaseURLKey, baseUrl);
            editor.apply();
        }


    }

    private void deleteAllCards(final Context context) {
        creditCardManager.deleteAllCreditCards(context,null);
    }





    /**
     * Initiates the hosted payment page for registering a credit card and returns the URL to the
     * page.
     *
     * @param formSettings HPP form settings
     * @param listener     Callback listener
     */
    public Request initiateHostedPaymentPage(RegistrationFormSettings formSettings, PaymentApiService.IHppResultListener listener) {
        return PaymentService.initiateHppRegistration(formSettings, listener);
    }

    /**
     * Registers a credit card for future recurring payments.
     * <p>When the registration is completed the card is stored on disk and can be read by calling
     * {@link #getRegisteredCreditCards(Context, CreditCardManager.IOnCreditCardRead)}.</p>
     *
     * @param context      App Context object
     * @param cardNumber   Card number
     * @param expiryMonth  Expiry month of the card
     * @param expiryYear   Expiry year of the card
     * @param securityCode Security code such as CVC or CVV code
     * @param listener     Result listener
     */
    //[[]]c
    public void registerCreditCard(Context context, String cardNumber, String expiryMonth, String expiryYear,
                                   String securityCode, ICardRegistrationCallback listener) {
        creditCardManager.registerCreditCard(
                context, cardNumber, expiryMonth, expiryYear, securityCode, listener);
    }

    /**
     * Registers a credit card for future recurring payments.
     * <p>When the registration is completed the card is stored on disk and can be read by calling
     * {@link #getRegisteredCreditCards(Context, CreditCardManager.IOnCreditCardRead)}.</p>
     *
     * @param context      App Context object
     * @param cardHolder   Card holder
     * @param cardNumber   Card number
     * @param expiryMonth  Expiry month of the card
     * @param expiryYear   Expiry year of the card
     * @param securityCode Security code such as CVC or CVV code
     * @param listener     Result listener
     */
    public void registerCreditCard(Context context, String cardHolder, String cardNumber, String expiryMonth, String expiryYear,
                                   String securityCode, ICardRegistrationCallback listener) {
        creditCardManager.registerCreditCard(
                context, cardHolder, cardNumber, expiryMonth, expiryYear, securityCode, listener);
    }

    /**
     * Reads all stored credit cards from local storage asynchronously and notifies listener.
     *
     * @param context  App Context object
     * @param listener Callback listener; called when list of cards have been loaded
     */
    public void getRegisteredCreditCards(Context context, CreditCardManager.IOnCreditCardRead listener) {
        creditCardManager.getCreditCards(context, listener);
    }

    /**
     * Deletes a stored credit card with given token from local disk.
     *
     * @param context         App context object
     * @param creditCardToken Token for credit card to be deleted
     * @param listener        Result listener
     */
    public void deleteCreditCard(Context context, String creditCardToken, CreditCardManager.IOnCreditCardDeleted listener) {
        creditCardManager.deleteCreditCard(context, creditCardToken, listener);
    }

    /**
     * Updates credit card alias for card with given token.
     *
     * @param context  App Context object
     * @param alias    Name/Alias for the card
     * @param token    Credit card token
     * @param listener Status listener. Called when update is completed.
     */
    public void setCreditCardAlias(Context context, String alias, String token, CreditCardManager.IOnCreditCardSaved listener) {
        creditCardManager.setAlias(context, alias, token, listener);
    }

    /**
     * Makes a transaction as specified in {@link PaymentSettings}.
     *
     * @param paymentIdentifier Identifier of the payment that this transaction refers to.
     * @param paymentSettings   Specifies details of this transaction.
     * @param callBack          Transaction result callback.
     */
    // The following is the original SDK version
    public Request makeTransaction(String paymentIdentifier, PaymentSettings paymentSettings, ITransactionListener callBack) {
        return PaymentService.makeTransaction(paymentIdentifier, paymentSettings, callBack);
    }

    // The following is the APAC supported version which is for payment with a card
    public Request submitSinglePaymentCard(Context context, String paymentIdentifier, PaymentSettings paymentSettings, ITransactionExtListener callBack, CreditCardManager.IOnCreditCardSaved onSavedListener) {
        paymentSettings.paymentType = PaymentType.PaymentTypeEnum.PaymentCard;
        return PaymentService.makeTransactionCard(context, paymentIdentifier, paymentSettings, callBack, onSavedListener);
    }
    // The following is the APAC supported version which is for payment with a token
    public Request submitSinglePaymentToken(String paymentIdentifier, PaymentSettings paymentSettings, ITransactionExtListener callBack) {
        paymentSettings.paymentType = PaymentType.PaymentTypeEnum.PaymentToken;
        return PaymentService.makeTransactionToken(paymentIdentifier, paymentSettings, callBack);
    }

    public Request submitPreAuthToken(String paymentIdentifier, PaymentSettings paymentSettings, ITransactionExtListener callBack) {
        paymentSettings.paymentType = PaymentType.PaymentTypeEnum.PreAuthToken;
        return PaymentService.makeTransactionToken(paymentIdentifier, paymentSettings, callBack);
    }

    public Request submitPreAuthCard(Context context, String paymentIdentifier, PaymentSettings paymentSettings, ITransactionExtListener callBack, CreditCardManager.IOnCreditCardSaved onSavedListener) {
        paymentSettings.paymentType = PaymentType.PaymentTypeEnum.PreAuthCard;
        return PaymentService.makeTransactionCard(context, paymentIdentifier, paymentSettings, callBack, onSavedListener);
    }

    // The following is the APAC supported version which is for pre-auth with a card.
    public void submitSinglePreAuthCard(final Context context,
                                        final String paymentIdentifier,
                                        final PaymentSettings paymentSettings,
                                        String cardHolderName,
                                        String cardNumber, String expiryMonth,
                                        String expiryYear, String securityCode,
                                        final boolean requestToken,
                                        final ITransactionExtListener callBack,
                                        final CreditCardManager.IOnCreditCardSaved onSavedListener) {
        paymentSettings.paymentType = PaymentType.PaymentTypeEnum.PreAuthCard;
        submitSingleTransactionCard(context, paymentIdentifier, paymentSettings, cardHolderName, cardNumber, expiryMonth, expiryYear, securityCode,
                                    requestToken, callBack, onSavedListener);
    }

    // The following is the APAC supported version which is for payment with a card.
    public void submitSinglePaymentCard(final Context context,
                                        final String paymentIdentifier,
                                        final PaymentSettings paymentSettings,
                                        String cardHolderName,
                                        String cardNumber, String expiryMonth,
                                        String expiryYear, String securityCode,
                                        final boolean requestToken,
                                        final ITransactionExtListener callBack,
                                        final CreditCardManager.IOnCreditCardSaved onSavedListener) {
        paymentSettings.paymentType = PaymentType.PaymentTypeEnum.PaymentCard;
        submitSingleTransactionCard(context, paymentIdentifier, paymentSettings, cardHolderName, cardNumber, expiryMonth, expiryYear, securityCode,
                                    requestToken, callBack, onSavedListener);
    }

    /*
     * The following is the APAC supported version which is common for payment and preauth with a card.
     * paymentSettings.paymentType needs to have been set to PaymentType.PaymentTypeEnum.PaymentCard or PaymentType.PaymentTypeEnum.PreAuthCard to determine
     * whether payment or preauth will occur.
     */
    public void submitSingleTransactionCard(final Context context,
                                        final String paymentIdentifier,
                                        final PaymentSettings paymentSettings,
                                        String cardHolderName,
                                        String cardNumber, String expiryMonth,
                                        String expiryYear, String securityCode,
                                        final boolean requestToken,
                                        final ITransactionExtListener callBack,
                                        final CreditCardManager.IOnCreditCardSaved onSavedListener) {
        final RegistrationParams params = new RegistrationParams(new Crypto(), CertificateManager.getInstance());
        params.setParametersAndEncrypt(context, cardHolderName, cardNumber, expiryMonth, expiryYear, securityCode,
                new RegistrationParams.IOnEncryptionListener() {
                    @Override
                    public void onEncryptionComplete() {
                        JSONObject cardObject = new JSONObject();
                        try {
                            RegistrationParams.EncryptedSessionKey encryptedSessionKeyObj = params.encryptedSessionKeys.get(0);
                            cardObject.put("cardholderName", params.encryptedCard.cardHolder);
                            cardObject.put("cardNumber", params.encryptedCard.cardNumber);
                            cardObject.put("expiryMonth", params.encryptedCard.expiryMonth);
                            cardObject.put("expiryYear", params.encryptedCard.expiryYear);
                            cardObject.put("sessionKey", encryptedSessionKeyObj.sessionKey);
                            cardObject.put("isTokenRequested", requestToken);
                            paymentSettings.cardPaymentJsonData = cardObject;
                            PaymentService.makeTransactionCard(context, paymentIdentifier, paymentSettings, callBack, onSavedListener);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    @Override
                    public void onEncryptionError() {
                        if (callBack != null) callBack.onTransactionError(null);
                    }
                });
    }







    public void getVisaCheckoutData(Context context, VisaCheckoutDataCallback callBack) {
        VisaCheckoutService.getVisaCheckoutData(context,callBack);
    }

    public void processTransaction(Context context, VisaCheckoutTransactionParams visaCheckoutTransactionParams, VisaCheckoutTransactionCallback callBack) {
        VisaCheckoutService.processTransaction(context,visaCheckoutTransactionParams,callBack);
    }



    /**
     * Instantiates and initiates an {@link ApiService} with a base URL and a network client.
     * <p>The network client is used to customize all requests sent by the service.</p>
     *
     * @param serviceClass Class to be initiated. Must extend {@link ApiService}.
     * @param <T>          Generic class to instantiate
     * @return The initiated service class object.
     */
    public <T extends ApiService> T createService(Class<T> serviceClass) {
        try {
            Constructor<T> constructor = serviceClass.getConstructor();
            T instance = constructor.newInstance();
            instance.initiate(mBaseUrl, mHttpClient);
            return instance;
        } catch (Exception e) {
            BNLog.e(getClass().getSimpleName(), "Internal error; failed to generate API service.", e);
        }
        return null;
    }

    /**
     * Returns the api-token registered to the handler instance.
     *
     * @return The app id used by the {@link BNPaymentHandler}.
     * @deprecated As of release 1.4.0, replaced by {@link #getMerchantAccount()}
     */
    @Deprecated
    public String getApiToken() {
        return mApiToken;
    }

    /**
     * Returns the merchant account registered to the handler instance.
     *
     * @return The merchant account used by the {@link BNPaymentHandler}.
     * @since 1.4.0
     */
    public String getMerchantAccount() {
        return mMerchantAccount;
    }

    /**
     * A getter for debug boolean
     *
     * @return A boolean indicating whether the Lib is in debug mode
     */
    public boolean getDebug() {
        return mDebug;
    }

    /**
     * A getter for the http client
     *
     * @return The http client
     */
    public HttpClient getHttpClient() {
        return mHttpClient;
    }

    /**
     * Override the default {@link CertificateManager}.
     * @param certificateManager A {@link CertificateManager}
     */
    public void setCertificateManager(CertificateManager certificateManager) {
        ourInstance.certificateManager = certificateManager;
    }

    /**
     * Removes current setup so that {@link #setupBNPayments(BNPaymentBuilder)} can be rerun.
     */
    public void clearSetup() {
        ourInstance = new BNPaymentHandler();
    }

    /**
     * {@link BNPaymentBuilder} is a class used
     * for building and instance of {@link BNPaymentHandler}.
     */
    public static class BNPaymentBuilder {
        private String baseUrl = "https://eu-native.bambora.com/";
        private boolean debug;
        private String apiToken;
        private String merchantAccount;
        private Context context;

        /**
         * Constructor for {@link BNPaymentBuilder}
         * that takes the required params.
         *
         * @param context  The context in which the handler will operate (Should be the application context)
         * @param apiToken The app id for this application
         * @deprecated As of version 1.4.0, use {@link BNPaymentBuilder(Context)} and {@link #merchantAccount(String)}
         */
        @Deprecated
        public BNPaymentBuilder(Context context, String apiToken) {
            this.context = context;
            this.apiToken = apiToken;
        }

        /**
         * Constructor for {@link BNPaymentBuilder}
         * that takes the required params.
         *
         * @param context The context in which the handler will operate (Should be the application context)
         */
        public BNPaymentBuilder(Context context) {
            this.context = context;
        }

        /**
         * A method for setting the merchant account for the builder.
         *
         * @param merchantAccount The merchant account to use, e.g. "T012345678"
         * @return The builder with merchant account set
         * @since 1.4.0
         */
        public BNPaymentBuilder merchantAccount(String merchantAccount) {
            this.merchantAccount = merchantAccount;
            return this;
        }

        /**
         * A method for setting the debug flag for the builder.
         *
         * @param debug A flag indicating whether the lib is debug mode or not
         * @return The builder with debug variable set
         */
        public BNPaymentBuilder debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        /**
         * A method for setting the base url for the builder.
         *
         * @param baseUrl The base Url for the endpoint used for network communication
         * @return The builder with debug variable set
         */
        public BNPaymentBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
    }
}
