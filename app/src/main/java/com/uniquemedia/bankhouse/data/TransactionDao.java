package com.uniquemedia.bankhouse.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC")
    List<TransactionRecord> getAll();

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    TransactionRecord getById(long id);

    @Query("SELECT COUNT(*) FROM transactions WHERE importFingerprint = :fingerprint")
    int countByFingerprint(long fingerprint);

    @Insert
    long insert(TransactionRecord transaction);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<TransactionRecord> transactions);

    @Update
    void update(TransactionRecord transaction);

    @Delete
    void delete(TransactionRecord transaction);

    @Query("DELETE FROM transactions")
    void deleteAll();
}
