package com.uniquemedia.bankhouse.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
public class TransactionRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String type;
    public String category;
    public String source;
    public String accountFrom;
    public String accountTo;
    public long amount;
    public long dateMillis;
    public String notes;
    public long importFingerprint;
}
