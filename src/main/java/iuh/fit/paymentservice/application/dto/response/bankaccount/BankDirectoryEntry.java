package iuh.fit.paymentservice.application.dto.response.bankaccount;

public record BankDirectoryEntry(
        Integer id,
        String name,
        String code,
        String bin,
        String shortName,
        String logo,
        Boolean transferSupported,
        Boolean lookupSupported
) {
}
