package com.oppwa;

public interface PaymentStatusRequestListener {
    void onErrorOccurred();
    void onPaymentStatusReceived(String paymentStatus);
}
