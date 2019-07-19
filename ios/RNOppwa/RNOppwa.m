#import "RNOppwa.h"

@implementation RNOppwa

OPPPaymentProvider *provider;
OPPProviderMode providerMode;
NSString *shopperResultUrl = @"http://127.0.0.1";
NSString *checkoutId;
OPPCheckoutProvider *checkoutProvider;


RCT_EXPORT_MODULE(RNOppwa);

#define UIColorFromRGB(rgbHex) [UIColor colorWithRed:((float)((rgbHex & 0xFF0000) >> 16))/255.0 green:((float)((rgbHex & 0xFF00) >> 8))/255.0 blue:((float)(rgbHex & 0xFF))/255.0 alpha:1.0];

-(instancetype)init
{
    self = [super init];
    if (self) {
#ifdef DEBUG
        provider = [OPPPaymentProvider paymentProviderWithMode:OPPProviderModeLive];
#else
        provider = [OPPPaymentProvider paymentProviderWithMode:OPPProviderModeLive];
#endif
    }
    
    return self;
}

/**
 * transaction
 */
RCT_EXPORT_METHOD(transactionPayment: (NSDictionary*)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    
    NSError * _Nullable error;
    
    NSMutableDictionary *result = [[NSMutableDictionary alloc]initWithCapacity:10];
    OPPCardPaymentParams *params = [OPPCardPaymentParams cardPaymentParamsWithCheckoutID:[options valueForKey:@"checkoutID"]
                                    
                                                                            paymentBrand:[options valueForKey:@"paymentBrand"]
                                                                                  holder:[options valueForKey:@"holderName"]
                                                                                  number:[options valueForKey:@"cardNumber"]
                                                                             expiryMonth:[options valueForKey:@"expiryMonth"]
                                                                              expiryYear:[options valueForKey:@"expiryYear"]
                                                                                     CVV:[options valueForKey:@"cvv"]
                                                                                   error:&error];
    
    if (error) {
        reject(@"oppwa/card-init",error.localizedDescription, error);
    } else {
        params.tokenizationEnabled = YES;
        OPPTransaction *transaction = [OPPTransaction transactionWithPaymentParams:params];
        
        [provider submitTransaction:transaction completionHandler:^(OPPTransaction * _Nonnull transaction, NSError * _Nullable error) {
            if (transaction.type == OPPTransactionTypeAsynchronous) {
                // Open transaction.redirectURL in Safari browser to complete the transaction
                [result setObject:[NSString stringWithFormat:@"transactionCompleted"] forKey:@"status"];
                [result setObject:[NSString stringWithFormat:@"asynchronous"] forKey:@"type"];
                [result setObject:[NSString stringWithFormat:@"%@",transaction.redirectURL] forKey:@"redirectURL"];
                resolve(result);
            }  else if (transaction.type == OPPTransactionTypeSynchronous) {
                [result setObject:[NSString stringWithFormat:@"transactionCompleted"] forKey:@"status"];
                [result setObject:[NSString stringWithFormat:@"synchronous"] forKey:@"type"];
                resolve(result);
            } else {
                reject(@"oppwa/transaction",error.localizedDescription, error);
                // Handle the error
            }
        }];
    }
}
/**
 * validate number
 * @return
 */
RCT_EXPORT_METHOD(isValidNumber:
                  (NSDictionary*)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    
    
    if ([OPPCardPaymentParams isNumberValid:[options valueForKey:@"cardNumber"] forPaymentBrand:[options valueForKey:@"paymentBrand"]]) {
        resolve([NSNull null]);
    }
    else {
        reject(@"oppwa/card-invalid", @"The card number is invalid.", nil);
    }
}

+ (BOOL)requiresMainQueueSetup
{
    return YES;
}

/**
 * oppwa mode test or live
 * @return
 */
RCT_EXPORT_METHOD(setProviderMode: (NSString*)mode) {
    if([mode  isEqual: @"TEST"]){
        providerMode = OPPProviderModeTest;
    } else {
        providerMode = OPPProviderModeLive;
    }
}

/**
 * open sdk ui
 * @return
 */
RCT_EXPORT_METHOD(openCheckoutUI: (NSDictionary*)options resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
    if(![options objectForKey:@"checkoutID"] || [[options valueForKey:@"checkoutID"] length] == 0){
        NSDictionary *json = @{@"success":@NO, @"error":@"The checkoutID is invalid."};
        resolve(json);
        return;
    }
    if(![options objectForKey:@"shopperResultUrl"] || [[options valueForKey:@"shopperResultUrl"] length] == 0){
        NSDictionary *json = @{@"success":@NO, @"error":@"The shopperResultUrl is invalid."};
        resolve(json);
        return;
    }
    checkoutId = [options objectForKey:@"checkoutID"];
    shopperResultUrl = [options objectForKey:@"shopperResultUrl"];
    checkoutProvider = [self configureCheckoutProvider:checkoutId];
    [checkoutProvider setDelegate:self];
    [checkoutProvider presentCheckoutForSubmittingTransactionCompletionHandler:^(OPPTransaction * _Nullable transaction, NSError * _Nullable error) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [self handleTransactionSubmission:transaction error:error resolver:resolve rejecter:reject];
        });
    } cancelHandler:^{
        NSDictionary *json = @{@"success":@NO, @"cancel":@YES,@"mesage":@"Cancel checkout"};
        resolve(json);
    }];
}
/**
 * handle transaction
 * @return
 */
-(void)handleTransactionSubmission:(OPPTransaction*)transaction error:(NSError*)error resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject {
    if(transaction == nil){
        NSDictionary *json = @{@"success":@NO, @"error":[error localizedDescription]};
        resolve(json);
        return;
    }
    if(error){
        NSDictionary *json = @{@"success":@NO, @"error":[error localizedDescription]};
        resolve(json);
        return;
    }
    if([transaction type] == OPPTransactionTypeSynchronous){
        NSDictionary *json = @{@"success":@YES, @"checkoutId":checkoutId, @"resourcePath":[transaction resourcePath]};
        resolve(json);
        NSLog(@"handleTransactionSubmission requestPaymentStatus");
    } else if([transaction type] == OPPTransactionTypeAsynchronous){
        // If a transaction is asynchronous, SDK opens transaction.redirectUrl in a browser
        // Subscribe to notifications to request the payment status when a shopper comes back to the app
    } else {
        NSDictionary *json = @{@"success":@NO, @"error":@"Invalid transaction"};
        resolve(json);
    }
}
/**
 * create checkout provider to open sdk ui
 * @return
 */
- (OPPCheckoutProvider*)configureCheckoutProvider:(NSString *)checkoutID
{
    NSArray *paymentBrands = @[@"VISA", @"MASTER"];
    OPPPaymentProvider *provider = [OPPPaymentProvider paymentProviderWithMode:providerMode];
    OPPSecurityPolicy *securityPolicyForTokens = [OPPSecurityPolicy securityPolicyForTokensWithMode:OPPSecurityPolicyModeDeviceAuthRequiredIfAvailable];
    OPPSecurityPolicy *securityPolicyForPaymentMethods = [OPPSecurityPolicy securityPolicyWithPaymentBrands:paymentBrands mode:OPPSecurityPolicyModeDeviceAuthRequiredIfAvailable];
    
    OPPCheckoutSettings *checkoutSettings = [[OPPCheckoutSettings alloc] init];
    checkoutSettings.paymentBrands = paymentBrands;
    checkoutSettings.storePaymentDetails = OPPCheckoutStorePaymentDetailsModePrompt;
    checkoutSettings.skipCVV = OPPCheckoutSkipCVVModeNever;
    checkoutSettings.securityPolicies = @[securityPolicyForPaymentMethods, securityPolicyForTokens];
    checkoutSettings.shopperResultURL = shopperResultUrl;
    
    checkoutSettings.theme.navigationBarBackgroundColor = UIColorFromRGB(0xFA661C);
    checkoutSettings.theme.accentColor = UIColorFromRGB(0xFA661C);
    checkoutSettings.theme.cellHighlightedBackgroundColor = UIColorFromRGB(0xFA661C);
    checkoutSettings.theme.confirmationButtonColor = UIColorFromRGB(0x006577);
    
    OPPCheckoutProvider *checkoutProvider = [OPPCheckoutProvider checkoutProviderWithPaymentProvider:provider checkoutID:checkoutID settings:checkoutSettings];

    return checkoutProvider;
}

@end
