package iuh.fit.paymentservice.application.port.security;

public interface BankAccountProtector {
    String encrypt(String accountNumber);

    String decrypt(String encryptedAccountNumber);

    String hash(String bankBin, String accountNumber);
}
