package iuh.fit.paymentservice.domain.enums;

public enum PaymentPurpose {
    BOOKING_DEPOSIT,
    BOOKING_REMAINING,
    TOURNAMENT_FEE;

    public boolean supports(PaymentReferenceType referenceType) {
        return switch (referenceType) {
            case BOOKING -> this == BOOKING_DEPOSIT || this == BOOKING_REMAINING;
            case TOURNAMENT_REGISTRATION -> this == TOURNAMENT_FEE;
        };
    }
}
