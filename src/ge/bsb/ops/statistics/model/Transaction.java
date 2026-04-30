package ge.bsb.ops.statistics.model;

import java.time.LocalDate;

public final class Transaction {
    private  int debitCustomerId;
    private  int creditCustomerId;
    private  int channelId;
    private LocalDate date;

    public int getDebitCustomerId() {
        return debitCustomerId;
    }

    public void setDebitCustomerId(int debitCustomerId) {
        this.debitCustomerId = debitCustomerId;
    }

    public int getCreditCustomerId() {
        return creditCustomerId;
    }

    public void setCreditCustomerId(int creditCustomerId) {
        this.creditCustomerId = creditCustomerId;
    }

    public int getChannelId() {
        return channelId;
    }

    public void setChannelId(int channelId) {
        this.channelId = channelId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "debitCustomerId='" + debitCustomerId + '\'' +
                ", creditCustomerId='" + creditCustomerId + '\'' +
                ", channelId='" + channelId + '\'' +
                ", date=" + date +
                '}';
    }
}
