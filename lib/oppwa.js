/**
 * @providesModule Oppwa
 * @flow
 */
import { NativeModules, NativeEventEmitter, Platform } from 'react-native';
import INTERNALS from './internals';
import { isObject, isString, isAndroid } from './utils';

const OppwaCoreModule = NativeModules.RNOppwa;

class OppwaCore {
  constructor() {
    if (!OppwaCoreModule) {
      throw new Error(INTERNALS.ERROR_MISSING_CORE);
    }
  }

  updateCheckoutID(checkoutId) {
    if (!checkoutId) {
      throw new Error(INTERNALS.ERROR_MISSING_OPT('checkoutId'));
    }

    OppwaCoreModule.updateCheckoutID(checkoutId);
  }

  /**
     * SDK transactionPayment
     *
     * @param options
     * @return Promise
     */
  transactionPayment(
    options: Object = {}
  ) {
    if (!isObject(options)) {
      throw new Error(INTERNALS.ERROR_INIT_OBJECT);
    }

    if (!options.checkoutID) {
      throw new Error(INTERNALS.ERROR_MISSING_OPT('checkoutID'));
    }
    if (!options.holderName) {
      throw new Error(INTERNALS.ERROR_MISSING_OPT('holderName'));
    }

    if (!options.cardNumber) {
      throw new Error(INTERNALS.ERROR_MISSING_OPT('cardNumber'));
    }

    if (!options.paymentBrand) {
      throw new Error(INTERNALS.ERROR_MISSING_OPT('paymentBrand'));
    }

    if (!options.expiryMonth) {
      throw new Error(INTERNALS.ERROR_MISSING_OPT('expiryMonth'));
    }

    if (!options.expiryYear) {
      throw new Error(INTERNALS.ERROR_MISSING_OPT('expiryYear'));
    }

    if (!options.cvv) {
      throw new Error(INTERNALS.ERROR_MISSING_OPT('cvv'));
    }

    return OppwaCoreModule.transactionPayment(options);
  }

  isValidNumber(options: Object = {}) {
    if (!isObject(options)) {
      throw new Error(INTERNALS.ERROR_INIT_OBJECT);
    }
    if (!options.cardNumber) {
      throw new Error(INTERNALS.ERROR_MISSING_OPT('cardNumber'));
    }
    return OppwaCoreModule.isValidNumber(options);
  }

  setProviderMode(mode){
    if (mode !== 'TEST' && mode !== 'LIVE') {
      let name = 'OPPProviderMode';
      if(Platform.OS === 'android'){
        name = 'ProviderMode';
      }
      throw new Error(INTERNALS.ERROR_INVALID_VALUE(name,mode));
    }
    return OppwaCoreModule.setProviderMode(mode);
  }

  openCheckoutUI(options: Object = {}, callback){
    if (!isObject(options)) {
      throw new Error(INTERNALS.ERROR_INIT_OBJECT);
    }
    if (!options.checkoutID) {
      throw new Error(INTERNALS.ERROR_MISSING_OPT('checkoutID'));
    }
    return OppwaCoreModule.openCheckoutUI(options);
  }
}

export default new OppwaCore();
