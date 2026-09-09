package iuh.fit.paymentservice.application.port.client;

import iuh.fit.paymentservice.application.dto.response.bankaccount.BankDirectoryEntry;

import java.util.List;

public interface BankDirectoryPort {
    List<BankDirectoryEntry> getBanks();
}
